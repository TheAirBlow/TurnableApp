package ru.theairblow.turnable.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import ru.theairblow.turnable.data.AppPreferences
import ru.theairblow.turnable.data.DEFAULT_SERVICE_ID
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.platform.CoreUpdate
import ru.theairblow.turnable.platform.PlatformNotifier
import ru.theairblow.turnable.data.AppPreferencesData
import ru.theairblow.turnable.data.ServiceConfig
import ru.theairblow.turnable.data.ServiceType
import ru.theairblow.turnable.data.ThemeMode
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.InstanceStatus
import ru.theairblow.turnable.proto.InstanceType
import ru.theairblow.turnable.proto.StartClientRequest
import ru.theairblow.turnable.proto.LogAttr
import ru.theairblow.turnable.proto.ProviderInfo
import ru.theairblow.turnable.proto.SessionEvent
import ru.theairblow.turnable.service.AppUpdate
import ru.theairblow.turnable.service.ClientKeys
import ru.theairblow.turnable.service.DEFAULT_CORE_VERSION
import ru.theairblow.turnable.service.fetchAppUpdate
import ru.theairblow.turnable.service.DownloadProgress
import ru.theairblow.turnable.service.LocalServiceHost
import ru.theairblow.turnable.service.MIN_CORE_VERSION
import ru.theairblow.turnable.service.compareVersions
import ru.theairblow.turnable.service.isSupportedCore
import ru.theairblow.turnable.service.ServiceEndpoint
import ru.theairblow.turnable.service.ServiceEvent
import ru.theairblow.turnable.service.TurnableServiceClient
import ru.theairblow.turnable.config.configDisplayName
import ru.theairblow.turnable.subscription.FetchedSubscription
import ru.theairblow.turnable.subscription.listenAddrsFor
import ru.theairblow.turnable.subscription.suggestedPorts
import ru.theairblow.turnable.subscription.PORTS_PER_ENTRY
import ru.theairblow.turnable.subscription.RETRY_AFTER_FAILURE_SEC
import ru.theairblow.turnable.subscription.Subscription
import ru.theairblow.turnable.subscription.SubscriptionEntry
import ru.theairblow.turnable.subscription.fetchSubscription
import ru.theairblow.turnable.subscription.randomLocalPort
import ru.theairblow.turnable.ui.util.nowUnixSeconds
import turnable.composeapp.generated.resources.*
import kotlin.io.encoding.Base64
import kotlin.random.Random

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val serviceId: String, val attempt: Int = 1) : ConnectionState
    data class Connected(val serviceId: String) : ConnectionState
    /** [retryInSec] is set while an automatic reconnect is scheduled. */
    data class Failed(val serviceId: String, val message: String, val retryInSec: Int? = null) : ConnectionState

    val activeServiceId: String?
        get() = when (this) {
            Idle -> null
            is Connecting -> serviceId
            is Connected -> serviceId
            is Failed -> serviceId
        }
}

data class LogEntry(val id: Long, val timeNs: Long, val level: Int, val message: String, val attrs: List<LogAttr>)

/** What one update check found; either side may be missing. */
data class UpdateCheck(val core: CoreUpdate?, val app: AppUpdate?)

private const val MAX_LOGS = 2000
private const val LOG_FLUSH_MS = 150L
private const val SUBSCRIPTION_CHECK_MS = 60_000L
private const val DEFAULT_CAPTCHA_PORT = 1984
private const val UPDATE_FIRST_CHECK_MS = 30_000L
private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60_000L
private const val CAPTCHA_PRUNE_MS = 15_000L
private const val CAPTCHA_LOG_MESSAGE = "manual captcha solve required"
private const val MANUAL_CAPTCHA_WINDOW_SEC = 600L
private val RECONNECT_DELAYS_SEC = listOf(2, 4, 8, 15, 30)

class AppViewModel(
    private val prefs: AppPreferences,
    private val localHost: LocalServiceHost,
    private val notifier: PlatformNotifier = PlatformNotifier.None,
) : ViewModel() {
    val prefsData: StateFlow<AppPreferencesData> =
        prefs.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesData())

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _instances = MutableStateFlow<List<InstanceInfo>>(emptyList())
    val instances: StateFlow<List<InstanceInfo>> = _instances.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers: StateFlow<List<ProviderInfo>> = _providers.asStateFlow()

    private val _clientListen = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** Listen addresses of client instances by ID (not part of ListInstances, so fetched once per client). */
    val clientListen: StateFlow<Map<String, List<String>>> = _clientListen.asStateFlow()

    private val _syncing = MutableStateFlow<Set<String>>(emptySet())
    /** IDs of subscriptions being updated. */
    val syncing: StateFlow<Set<String>> = _syncing.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _sessionEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    /** Tunnel connects/disconnects of server instances on the connected service. */
    val sessionEvents: SharedFlow<SessionEvent> = _sessionEvents.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short user-facing notices (shown as snackbars). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val localRunning: StateFlow<Set<String>> = localHost.running

    /** Set once running clients were restored, so the empty list at startup doesn't erase them. */
    private var restoreArmed = false
    private val _captchas = MutableStateFlow<List<CaptchaRequest>>(emptyList())
    /** Clients waiting for the user to solve a captcha by hand. */
    val captchas: StateFlow<List<CaptchaRequest>> = _captchas.asStateFlow()

    private val _update = MutableStateFlow<CoreUpdate?>(null)
    /** A newer core release waiting for the user's answer. */
    val update: StateFlow<CoreUpdate?> = _update.asStateFlow()

    private val _appUpdate = MutableStateFlow<AppUpdate?>(null)
    /** A newer app release than this build, when the last check found one. */
    val appUpdate: StateFlow<AppUpdate?> = _appUpdate.asStateFlow()

    private val _captchaToSolve = MutableStateFlow<String?>(null)
    /** Instance whose captcha screen is open. */
    val captchaToSolve: StateFlow<String?> = _captchaToSolve.asStateFlow()

    private val _localStarting = MutableStateFlow<Set<String>>(emptySet())
    val localStarting: StateFlow<Set<String>> = _localStarting.asStateFlow()

    /** The core release being downloaded right now, for first-run setup and updates. */
    val coreDownload: StateFlow<DownloadProgress?> = localHost.download

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private var client: TurnableServiceClient? = null
    private var connectionJob: Job? = null
    private var refreshJob: Job? = null
    private val pendingLogs = ArrayList<LogEntry>()
    private var logSeq = 0L

    init {
        viewModelScope.launch {
            ensureDefaultService()
            val p = prefs.data.first()
            // Simple mode only ever uses the on-device service, so it always comes up with the app
            val target = if (p.advancedMode) p.services.firstOrNull { it.id == p.selectedServiceId }?.takeIf { p.autoConnect }
            else p.services.firstOrNull { it.id == DEFAULT_SERVICE_ID }
            if (target == null) return@launch
            if (target.type == ServiceType.LOCAL && target.id !in localHost.running.value) startLocalService(target.id)
            else connect(target.id)
        }
        viewModelScope.launch {
            while (true) {
                delay(LOG_FLUSH_MS)
                flushLogs()
            }
        }
        viewModelScope.launch {
            while (true) {
                syncDueSubscriptions()
                delay(SUBSCRIPTION_CHECK_MS)
            }
        }
        viewModelScope.launch {
            delay(UPDATE_FIRST_CHECK_MS)
            while (true) {
                if (prefsData.value.autoUpdate) checkForUpdates()
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(CAPTCHA_PRUNE_MS)
                pruneCaptchas()
            }
        }
    }

    /** First launch: create the on-device service that simple mode runs. */
    private suspend fun ensureDefaultService() {
        val defaultName = getString(Res.string.vm_default_service_name)
        prefs.update { d ->
            if (d.services.isNotEmpty()) d
            else d.copy(
                services = listOf(ServiceConfig(DEFAULT_SERVICE_ID, defaultName, ServiceType.LOCAL)),
                selectedServiceId = DEFAULT_SERVICE_ID,
            )
        }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    /** Makes [serviceId] the active service and connects to it. */
    fun selectService(serviceId: String) {
        viewModelScope.launch { prefs.update { it.copy(selectedServiceId = serviceId) } }
        val svc = prefsData.value.services.firstOrNull { it.id == serviceId } ?: return
        if (svc.type == ServiceType.LOCAL && serviceId !in localHost.running.value) {
            disconnect()
            startLocalService(serviceId)
        } else {
            connect(serviceId)
        }
    }

    fun reconnect() {
        _connection.value.activeServiceId?.let(::connect)
    }

    private fun connect(serviceId: String) {
        disconnect()
        connectionJob = viewModelScope.launch {
            var attempt = 0
            while (true) {
                attempt++
                _connection.value = ConnectionState.Connecting(serviceId, attempt)
                val reason = try {
                    runSession(serviceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: getString(Res.string.vm_connection_failed)
                }
                if (client != null) attempt = 1 // we were connected; restart the backoff
                closeClient()
                val retry = RECONNECT_DELAYS_SEC.getOrNull(attempt - 1)
                val svc = prefsData.value.services.firstOrNull { it.id == serviceId }
                // A stopped local service won't come back on its own
                val canRetry = retry != null && svc != null &&
                    (svc.type == ServiceType.REMOTE || serviceId in localHost.running.value)
                if (!canRetry) {
                    _connection.value = ConnectionState.Failed(serviceId, reason)
                    return@launch
                }
                for (left in retry downTo 1) {
                    _connection.value = ConnectionState.Failed(serviceId, reason, retryInSec = left)
                    delay(1000)
                }
            }
        }
    }

    /** Connects and processes events until the connection drops; returns why it ended. */
    private suspend fun runSession(serviceId: String): String {
        val svc = prefsData.value.services.firstOrNull { it.id == serviceId }
            ?: error(getString(Res.string.vm_service_gone))
        val endpoint = when (svc.type) {
            ServiceType.LOCAL -> ServiceEndpoint.Unix(localHost.socketPath(svc.id))
            ServiceType.REMOTE -> ServiceEndpoint.Tcp(svc.host, svc.port)
        }
        val keys = svc.clientPubKeyB64.takeIf { it.isNotBlank() }
            ?.let { ClientKeys(Base64.decode(it.trim()), Base64.decode(svc.clientPrivKeyB64.trim())) }
        val c = TurnableServiceClient.connect(endpoint, keys, svc.serverPubKeyB64.takeIf { it.isNotBlank() }?.let { Base64.decode(it.trim()) })
        client = c
        _connection.value = ConnectionState.Connected(serviceId)
        _logs.value = emptyList()
        refresh()
        viewModelScope.launch { syncDueSubscriptions() }
        restoreArmed = false
        viewModelScope.launch { restoreRunningClients(c) }
        val eventsJob = viewModelScope.launch {
            c.events.collect { event ->
                when (event) {
                    is ServiceEvent.Log -> {
                        pendingLogs += event.record.let { LogEntry(logSeq++, it.time, it.level, it.message, it.attrs) }
                        if (svcIsLocal(serviceId)) captureCaptcha(event.record)
                    }
                    is ServiceEvent.Instance -> {
                        _clientListen.update { it - event.event.instanceId }
                        refresh()
                    }
                    is ServiceEvent.Session -> _sessionEvents.tryEmit(event.event)
                    is ServiceEvent.Disconnected -> Unit
                }
            }
        }
        try {
            return c.closedReason.filterNotNull().first()
        } finally {
            eventsJob.cancel()
        }
    }

    /** Starts the clients that were running last time, then begins tracking which ones are running. */
    private suspend fun restoreRunningClients(c: TurnableServiceClient) {
        try {
            val p = prefsData.value
            if (!p.restoreRunning || p.runningClients.isEmpty()) return
            c.listInstances()
                .filter { it.type == InstanceType.INSTANCE_TYPE_CLIENT && it.id in p.runningClients }
                .filter { it.status != InstanceStatus.INSTANCE_STATUS_STARTED && it.status != InstanceStatus.INSTANCE_STATUS_STARTING }
                .forEach { runCatching { c.restartInstance(it.id) } }
        } finally {
            if (client === c) { restoreArmed = true; refresh() }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        closeClient()
        _connection.value = ConnectionState.Idle
    }

    private fun closeClient() {
        client?.close()
        client = null
        _instances.value = emptyList()
        _providers.value = emptyList()
        _clientListen.value = emptyMap()
        _captchas.value.forEach { clearCaptcha(it.instanceId) }
        notifier.status(0, 0)
    }

    private fun flushLogs() {
        // Producer and flusher both run on the main dispatcher
        if (pendingLogs.isEmpty()) return
        val batch = pendingLogs.toList()
        pendingLogs.clear()
        _logs.update { (it + batch).takeLast(MAX_LOGS) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // ─── Captcha ──────────────────────────────────────────────────────────────

    private fun svcIsLocal(serviceId: String) =
        prefsData.value.services.firstOrNull { it.id == serviceId }?.type == ServiceType.LOCAL

    /** Local services run with VK_MANUAL_MANAGED, which logs where the captcha page and token endpoint are. */
    private fun captureCaptcha(record: ru.theairblow.turnable.proto.LogRecord) {
        if (record.message != CAPTCHA_LOG_MESSAGE) return
        val attrs = record.attrs.associate { it.key to it.value }
        val port = attrs["port"]?.toIntOrNull() ?: DEFAULT_CAPTCHA_PORT
        val url = attrs["url"] ?: return
        val instanceId = attrs["client_id"] ?: return
        val name = _instances.value.firstOrNull { it.id == instanceId }?.name?.ifBlank { null } ?: instanceId
        val request = CaptchaRequest(instanceId, name, port, url, nowUnixSeconds() + MANUAL_CAPTCHA_WINDOW_SEC)
        _captchas.update { list -> list.filter { it.instanceId != instanceId } + request }
        notifier.captchaRequired(request)
    }

    fun openCaptcha(instanceId: String) {
        if (_captchas.value.any { it.instanceId == instanceId }) _captchaToSolve.value = instanceId
    }

    fun closeCaptcha() {
        _captchaToSolve.value = null
    }

    /** The service got the tokens; the client carries on connecting by itself. */
    fun captchaSolved(instanceId: String) {
        clearCaptcha(instanceId)
        viewModelScope.launch { notify(getString(Res.string.vm_captcha_solved)) }
    }

    fun clearCaptcha(instanceId: String) {
        _captchas.update { list -> list.filter { it.instanceId != instanceId } }
        if (_captchaToSolve.value == instanceId) _captchaToSolve.value = null
        notifier.captchaCleared(instanceId)
    }

    private fun rememberRunning(clients: List<InstanceInfo>) {
        val running = clients.filter {
            it.status == InstanceStatus.INSTANCE_STATUS_STARTED || it.status == InstanceStatus.INSTANCE_STATUS_STARTING
        }.map { it.id }.toSet()
        val listed = clients.map { it.id }.toSet()
        val next = prefsData.value.runningClients.let { (it - listed) + running }
        if (next != prefsData.value.runningClients) viewModelScope.launch { prefs.update { it.copy(runningClients = next) } }
    }

    private fun pruneCaptchas() {
        val now = nowUnixSeconds()
        _captchas.value.filter { it.deadlineSec <= now }.forEach { clearCaptcha(it.instanceId) }
    }

    private fun publishStatus(list: List<InstanceInfo>) {
        val clients = list.filter { it.type == InstanceType.INSTANCE_TYPE_CLIENT }
        if (restoreArmed) rememberRunning(clients)
        notifier.status(clients.count { it.status == InstanceStatus.INSTANCE_STATUS_STARTED }, clients.size)
        val ids = list.associateBy { it.id }
        // Only a client still starting can be waiting for a captcha; any other state ends the request
        val now = nowUnixSeconds()
        _captchas.value.filter { c ->
            when (ids[c.instanceId]?.status) {
                null -> now - (c.deadlineSec - MANUAL_CAPTCHA_WINDOW_SEC) > 10
                InstanceStatus.INSTANCE_STATUS_STARTING -> false
                else -> true
            }
        }.forEach { clearCaptcha(it.instanceId) }
    }

    // ─── Requests ─────────────────────────────────────────────────────────────

    /** Runs [block] against the connected service. */
    suspend fun <T> request(block: suspend TurnableServiceClient.() -> T): Result<T> {
        val c = client ?: return Result.failure(IllegalStateException(getString(Res.string.vm_not_connected)))
        return try {
            Result.success(c.block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Like [request], then reloads instances and providers; reports failures as a message. */
    suspend fun <T> mutate(block: suspend TurnableServiceClient.() -> T): Result<T> =
        request(block).also { result ->
            result.exceptionOrNull()?.let { notify(it.message ?: getString(Res.string.vm_request_failed)) }
            refresh()
        }

    fun refresh() {
        val c = client ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val list = c.listInstances().sortedBy { it.name.lowercase() }
                _instances.value = list
                publishStatus(list)
                _providers.value = c.listProviders().sortedBy { it.name.lowercase() }
                val clientIds = list.filter { it.type == InstanceType.INSTANCE_TYPE_CLIENT }.map { it.id }.toSet()
                val known = _clientListen.value.filterKeys { it in clientIds }
                val fetched = (clientIds - known.keys).associateWith { id ->
                    runCatching { c.getInstance(id).listenAddrs }.getOrDefault(emptyList())
                }
                _clientListen.value = known + fetched
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // a broken connection is reported through the Disconnected event
            } finally {
                _refreshing.value = false
            }
        }
    }

    // ─── Clients ──────────────────────────────────────────────────────────────

    /** Every local port some client may listen on: each known address plus the ports following it for extra routes. */
    private fun takenPorts(): Set<Int> =
        (_clientListen.value.values.flatten().mapNotNull { it.substringAfterLast(':').toIntOrNull() } +
            prefsData.value.subscriptions.flatMap { s -> s.entries.map { it.port } })
            .flatMap { it until it + PORTS_PER_ENTRY }.toSet()

    /**
     * A loopback address on a random port. Picked once when a client is created and passed explicitly,
     * so the service persists it and the port stays the same across restarts.
     */
    fun newClientListenAddr(): String = "127.0.0.1:${randomLocalPort(takenPorts())}"

    // ─── Subscriptions ────────────────────────────────────────────────────────

    /** Fetches [url] and, if it serves links, saves it as a subscription of the active service and imports them. */
    suspend fun addSubscription(url: String, name: String): Result<Unit> {
        val serviceId = (_connection.value as? ConnectionState.Connected)?.serviceId
            ?: return Result.failure(IllegalStateException(getString(Res.string.vm_connect_first)))
        val fetched = try {
            fetchSubscription(url.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val sub = Subscription(Random.nextLong().toULong().toString(16), serviceId, url.trim(), name.trim())
        prefs.update { it.copy(subscriptions = it.subscriptions + sub) }
        return syncSubscription(sub.id, fetched)
    }

    suspend fun editSubscription(id: String, name: String, url: String, updateHours: Int) {
        val urlChanged = prefsData.value.subscriptions.firstOrNull { it.id == id }?.url != url.trim()
        updateSubscription(id) { it.copy(name = name.trim(), url = url.trim(), updateHours = updateHours.coerceAtLeast(0)) }
        if (urlChanged) syncSubscription(id)
    }

    /** Forgets a subscription; with [removeClients] its client instances are stopped and removed too. */
    suspend fun removeSubscription(id: String, removeClients: Boolean) {
        val sub = prefsData.value.subscriptions.firstOrNull { it.id == id } ?: return
        if (removeClients) {
            val live = _instances.value.map { it.id }.toSet()
            sub.entries.filter { it.instanceId in live }.forEach { e -> request { stopInstance(e.instanceId) } }
            refresh()
        }
        prefs.update { d -> d.copy(subscriptions = d.subscriptions.filter { it.id != id }) }
    }

    /** Updates every subscription of the active service. */
    suspend fun syncAllSubscriptions() {
        val serviceId = (_connection.value as? ConnectionState.Connected)?.serviceId ?: return
        prefsData.value.subscriptions.filter { it.serviceId == serviceId }.forEach { syncSubscription(it.id) }
    }

    /**
     * Starts one client per pasted turnable:// link on its own port, honoring the ports a link suggests.
     * Returns how many started and the failure messages.
     */
    suspend fun importClientLinks(links: List<String>): Pair<Int, List<String>> {
        var started = 0
        val failures = mutableListOf<String>()
        links.forEach { link ->
            val addrs = listenAddrsFor(suggestedPorts(link), newClientListenAddr().substringAfterLast(':').toInt())
            request { addClientStopped(StartClientRequest(link, addrs, name = configDisplayName(link).orEmpty())) }
                .onSuccess { started++ }.onFailure { failures += it.message.orEmpty() }
            refresh()
        }
        return started to failures.distinct()
    }

    /** The service has no add-only call, so the new client is started and immediately stopped again. */
    private suspend fun TurnableServiceClient.addClientStopped(req: StartClientRequest) {
        val id = startClient(req.copy(autostart = false))
        stopInstance(id, keep = true)
    }

    /** Starts a stopped client, or stops a running one while keeping it listed. */
    suspend fun toggleInstance(info: InstanceInfo) {
        val running = info.status == InstanceStatus.INSTANCE_STATUS_STARTED || info.status == InstanceStatus.INSTANCE_STATUS_STARTING
        mutate { if (running) stopInstance(info.id, keep = true) else restartInstance(info.id) }
    }

    /** Connects or disconnects every client of a group at once. */
    suspend fun setGroupRunning(clients: List<InstanceInfo>, run: Boolean) {
        clients.forEach { info ->
            val running = info.status == InstanceStatus.INSTANCE_STATUS_STARTED || info.status == InstanceStatus.INSTANCE_STATUS_STARTING
            if (run && !running) request { restartInstance(info.id) }
            else if (!run && running) request { stopInstance(info.id, keep = true) }
        }
        refresh()
    }

    /** The subscription whose group client [instanceId] belongs to. */
    fun subscriptionOf(instanceId: String): Subscription? =
        prefsData.value.subscriptions.firstOrNull { s -> s.entries.any { it.instanceId == instanceId } }

    /**
     * Downloads the subscription (unless [prefetched]) and reconciles its client instances: new links are started
     * on a fresh port, changed links are updated in place on their existing port, and links that disappeared are removed.
     */
    suspend fun syncSubscription(id: String, prefetched: FetchedSubscription? = null): Result<Unit> {
        if (id in _syncing.value) return Result.success(Unit)
        _syncing.update { it + id }
        try {
            val sub = prefsData.value.subscriptions.firstOrNull { it.id == id } ?: return Result.success(Unit)
            val result = runCatching {
                val fetched = prefetched ?: fetchSubscription(sub.url)
                val live = request { listInstances() }.getOrThrow().map { it.id }.toSet()
                val old = sub.entries.associateBy { it.key }
                val taken = takenPorts().toMutableSet()
                val failures = mutableListOf<String>()
                val entries = fetched.links.map { link ->
                    val entry = old[link.key]?.copy(name = link.name)
                        ?: SubscriptionEntry(link.key, "sub-${sub.id}-${link.key.hashCode().toUInt().toString(16)}", randomLocalPort(taken), link.name)
                    taken += entry.port until entry.port + PORTS_PER_ENTRY
                    val addrs = link.listenAddrs(entry.port)
                    val current = if (entry.instanceId in live) request { getInstance(entry.instanceId) }.getOrNull() else null
                    val outcome = when {
                        current == null ->
                            request { addClientStopped(StartClientRequest(link.url, addrs, entry.instanceId, entry.name)) }
                        current.config.trim() != link.url ->
                            request { updateInstance(entry.instanceId, name = entry.name, config = link.url, listenAddrs = addrs) }
                        current.info?.name != entry.name -> request { updateInstance(entry.instanceId, name = entry.name) }
                        else -> null
                    }
                    outcome?.onFailure { failures += "${link.name}: ${it.message}" }
                    entry
                }
                val keep = entries.map { it.instanceId }.toSet()
                sub.entries.filter { it.instanceId !in keep && it.instanceId in live }
                    .forEach { e -> request { stopInstance(e.instanceId) } }
                val now = nowUnixSeconds()
                updateSubscription(id) {
                    it.copy(
                        title = fetched.title ?: it.title,
                        updateHours = if (it.lastUpdatedSec == 0L) fetched.updateHours ?: it.updateHours else it.updateHours,
                        usage = fetched.usage ?: it.usage,
                        announce = fetched.announce.orEmpty(),
                        supportUrl = fetched.supportUrl.orEmpty(),
                        webPageUrl = fetched.webPageUrl.orEmpty(),
                        entries = entries,
                        lastUpdatedSec = now,
                        lastAttemptSec = now,
                        lastError = failures.joinToString("\n"),
                    )
                }
                if (failures.isNotEmpty()) notify(
                    getPluralString(Res.plurals.vm_links_failed, failures.size, failures.size, sub.displayName),
                )
            }
            result.exceptionOrNull()?.let { e ->
                if (e is CancellationException) throw e
                val reason = e.message ?: getString(Res.string.vm_update_failed)
                updateSubscription(id) { it.copy(lastError = reason, lastAttemptSec = nowUnixSeconds()) }
            }
            refresh()
            return result
        } finally {
            _syncing.update { it - id }
        }
    }

    private suspend fun updateSubscription(id: String, transform: (Subscription) -> Subscription) {
        prefs.update { d -> d.copy(subscriptions = d.subscriptions.map { if (it.id == id) transform(it) else it }) }
    }

    private suspend fun syncDueSubscriptions() {
        val serviceId = (_connection.value as? ConnectionState.Connected)?.serviceId ?: return
        val now = nowUnixSeconds()
        prefsData.value.subscriptions
            .filter {
                it.serviceId == serviceId && it.updateHours > 0 &&
                    now - it.lastUpdatedSec >= it.updateHours * 3600L && now - it.lastAttemptSec >= RETRY_AFTER_FAILURE_SEC
            }
            .forEach { syncSubscription(it.id) }
    }

    // ─── Local services ───────────────────────────────────────────────────────

    suspend fun fetchAvailableVersions(): Result<List<String>> = runCatching { localHost.fetchAvailableVersions() }

    fun startLocalService(serviceId: String) {
        val svc = prefsData.value.services.firstOrNull { it.id == serviceId && it.type == ServiceType.LOCAL } ?: return
        if (serviceId in _localStarting.value) return
        viewModelScope.launch {
            _localStarting.update { it + serviceId }
            try {
                localHost.start(svc.id, if (svc.customCorePath.isBlank()) resolveVersion(svc) else "", localEnv(svc), svc.customCorePath)
                if (prefsData.value.selectedServiceId == serviceId) connect(serviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val output = localHost.readOutput(serviceId).lines().lastOrNull { it.isNotBlank() }
                val message = listOfNotNull(e.message, output).joinToString("\n")
                if (prefsData.value.selectedServiceId == serviceId) {
                    _connection.value = ConnectionState.Failed(serviceId, message)
                }
                notify(getString(Res.string.vm_could_not_start, svc.name, e.message.orEmpty()))
            } finally {
                _localStarting.update { it - serviceId }
            }
        }
    }

    /** A pinned version wins, then the newest installed release, then the default one, which gets downloaded. */
    private suspend fun resolveVersion(svc: ServiceConfig): String {
        if (svc.version.isNotBlank()) {
            check(isSupportedCore(svc.version)) {
                getString(Res.string.vm_core_too_old, svc.version, MIN_CORE_VERSION)
            }
            return svc.version
        }
        return localHost.installedVersions().firstOrNull() ?: DEFAULT_CORE_VERSION
    }

    /** Manual captchas are always handed to the app; forcing them skips the automatic attempt. */
    private fun localEnv(svc: ServiceConfig): Map<String, String> = buildMap {
        put("VK_MANUAL_MANAGED", "1")
        if (svc.forceManualCaptcha) put("VK_FORCE_MANUAL", "1")
    }

    /**
     * Looks for a newer app release and a core release newer than what a local service runs. Unless [force],
     * the check is skipped when one ran recently, and a core release the user declined stays quiet.
     */
    suspend fun checkForUpdates(force: Boolean = false): Result<UpdateCheck> = runCatching {
        val now = nowUnixSeconds()
        val p = prefsData.value
        if (!force && now - p.lastUpdateCheckSec < UPDATE_CHECK_INTERVAL_MS / 1000) {
            return@runCatching UpdateCheck(_update.value, _appUpdate.value)
        }
        prefs.update { it.copy(lastUpdateCheckSec = now) }
        // A failed app check keeps the previous answer; a failed core check still fails the whole check
        val app = runCatching { fetchAppUpdate() }.getOrElse { _appUpdate.value }
        val appChanged = app != _appUpdate.value
        _appUpdate.value = app
        val latest = localHost.fetchAvailableVersions().firstOrNull()
            ?: return@runCatching UpdateCheck(_update.value, app)
        val found = p.services.filter { it.type == ServiceType.LOCAL && it.customCorePath.isBlank() }.firstNotNullOfOrNull { svc ->
            val current = svc.version.ifBlank { localHost.installedVersions().firstOrNull() ?: DEFAULT_CORE_VERSION }
            CoreUpdate(svc.id, svc.name, latest).takeIf { compareVersions(latest, current) > 0 }
        }?.takeIf { force || it.version != p.dismissedUpdate }
        if (found != _update.value || appChanged) {
            _update.value = found
            if (found != null) notifier.updateAvailable(found, app != null) else notifier.updateCleared()
        }
        UpdateCheck(found, app)
    }

    /** Installs the offered release and restarts that service. */
    fun applyUpdate() {
        val offered = _update.value ?: return
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            updateLocalService(offered.serviceId)
                .onSuccess { notify(getString(Res.string.vm_updated_to, it)) }
                .onFailure { notify(getString(Res.string.vm_update_failed_detail, it.message.orEmpty())) }
            _updating.value = false
            clearUpdate()
        }
    }

    /** The user said no; the same release isn't offered again. */
    fun dismissUpdate() {
        val offered = _update.value ?: return
        viewModelScope.launch { prefs.update { it.copy(dismissedUpdate = offered.version) } }
        clearUpdate()
    }

    private fun clearUpdate() {
        _update.value = null
        notifier.updateCleared()
    }

    fun updateAutoUpdate(value: Boolean) {
        viewModelScope.launch { prefs.update { it.copy(autoUpdate = value) } }
        if (!value) clearUpdate()
    }

    /** Switches [serviceId] to the newest release and restarts it if running; returns the version now in use. */
    private suspend fun updateLocalService(serviceId: String): Result<String> = runCatching {
        val latest = localHost.fetchAvailableVersions().firstOrNull()
            ?: error(getString(Res.string.vm_no_release))
        val svc = prefsData.value.services.first { it.id == serviceId }
        check(svc.customCorePath.isBlank()) { getString(Res.string.vm_custom_core_in_use) }
        if (svc.version != latest) {
            localHost.install(latest)
            prefs.update { d -> d.copy(services = d.services.map { if (it.id == serviceId) it.copy(version = latest) else it }) }
            if (serviceId in localHost.running.value) restartLocalService(serviceId)
        }
        latest
    }

    fun restartLocalService(serviceId: String) {
        viewModelScope.launch {
            val wasActive = _connection.value.activeServiceId == serviceId
            if (wasActive) disconnect()
            localHost.stop(serviceId)
            if (wasActive) prefs.update { it.copy(selectedServiceId = serviceId) }
            startLocalService(serviceId)
        }
    }

    fun stopLocalService(serviceId: String) {
        viewModelScope.launch {
            if (_connection.value.activeServiceId == serviceId) disconnect()
            localHost.stop(serviceId)
        }
    }

    suspend fun localServiceOutput(serviceId: String): String = localHost.readOutput(serviceId)

    fun localSocketPath(serviceId: String): String = localHost.socketPath(serviceId)

    // ─── Saved services ───────────────────────────────────────────────────────

    suspend fun saveService(cfg: ServiceConfig) {
        val existed = prefsData.value.services.any { it.id == cfg.id }
        prefs.update { d ->
            if (existed) d.copy(services = d.services.map { if (it.id == cfg.id) cfg else it })
            else d.copy(services = d.services + cfg, selectedServiceId = d.selectedServiceId.ifEmpty { cfg.id })
        }
        // Reconnect so edited connection settings take effect
        if (existed && _connection.value.activeServiceId == cfg.id) {
            if (cfg.type == ServiceType.LOCAL && cfg.id in localHost.running.value) {
                restartLocalService(cfg.id)
            } else {
                connect(cfg.id)
            }
        }
    }

    suspend fun removeService(id: String) {
        if (_connection.value.activeServiceId == id) disconnect()
        localHost.stop(id)
        prefs.update { d ->
            val remaining = d.services.filter { it.id != id }
            d.copy(
                services = remaining,
                selectedServiceId = if (d.selectedServiceId == id) "" else d.selectedServiceId,
                subscriptions = d.subscriptions.filter { it.serviceId != id },
            )
        }
    }

    /** Adds services and their subscriptions from a backup, skipping ones that already exist. Returns how many services were added. */
    suspend fun importServices(services: List<ServiceConfig>, subscriptions: List<Subscription> = emptyList()): Int {
        var added = 0
        prefs.update { d ->
            val known = d.services.map { it.id }.toSet()
            val fresh = services.filter { it.id !in known }
            added = fresh.size
            val subKnown = d.subscriptions.map { it.id }.toSet()
            val serviceIds = known + fresh.map { it.id }
            d.copy(
                services = d.services + fresh,
                subscriptions = d.subscriptions + subscriptions.filter { it.id !in subKnown && it.serviceId in serviceIds },
            )
        }
        return added
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.update { it.copy(themeMode = mode) } }
    }

    fun updateAdvancedMode(value: Boolean) {
        viewModelScope.launch { prefs.update { it.copy(advancedMode = value) } }
    }

    /** Simple mode edits the on-device service's captcha behavior through the settings screen. */
    fun updateForceManualCaptcha(serviceId: String, value: Boolean) {
        viewModelScope.launch {
            val svc = prefsData.value.services.firstOrNull { it.id == serviceId } ?: return@launch
            if (svc.forceManualCaptcha != value) saveService(svc.copy(forceManualCaptcha = value))
        }
    }

    fun updateRestoreRunning(value: Boolean) {
        viewModelScope.launch { prefs.update { it.copy(restoreRunning = value) } }
    }

    fun updateAutoConnect(value: Boolean) {
        viewModelScope.launch { prefs.update { it.copy(autoConnect = value) } }
    }

    override fun onCleared() {
        disconnect()
    }
}
