package ru.theairblow.turnable.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import ru.theairblow.turnable.proto.*
import kotlin.concurrent.Volatile

/** Error reported by the service for a single request. */
class ServiceException(message: String) : Exception(message)

/** Messages the service pushes without being asked. */
sealed interface ServiceEvent {
    data class Log(val record: LogRecord) : ServiceEvent
    data class Instance(val event: InstanceEvent) : ServiceEvent
    data class Session(val event: SessionEvent) : ServiceEvent
    data class Disconnected(val reason: String) : ServiceEvent
}

@OptIn(ExperimentalSerializationApi::class)
private val proto = ProtoBuf

private const val REQUEST_TIMEOUT_MS = 30_000L

/**
 * Client for the Turnable service protocol (see service.proto).
 * Pushed logs and events are interleaved with replies; requests are serialized and each reply is
 * matched by its request ID, so a late reply to a timed-out request is dropped instead of misread.
 */
class TurnableServiceClient private constructor(private val conn: FrameConnection) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestMutex = Mutex()
    @Volatile private var pending: CompletableDeferred<ResponsePayload>? = null
    @Volatile private var pendingId = 0L
    private var lastId = 0L
    private val _closedReason = MutableStateFlow<String?>(null)
    /** Why the connection ended; null while it is open. */
    val closedReason: StateFlow<String?> = _closedReason.asStateFlow()

    private val _events = MutableSharedFlow<ServiceEvent>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ServiceEvent> = _events.asSharedFlow()

    init {
        scope.launch { readLoop() }
    }

    private fun readLoop() {
        val reason = try {
            readUntilError()
        } catch (e: Throwable) {
            _closedReason.value ?: (e.message ?: e::class.simpleName ?: "Connection lost")
        }
        shutDown(reason)
    }

    /** Dispatches incoming frames; returns the message of a protocol-level error, after which the service hangs up. */
    private fun readUntilError(): String {
        while (true) {
            @OptIn(ExperimentalSerializationApi::class)
            val response = proto.decodeFromByteArray<Response>(conn.readFrame())
            when (val p = response.payload) {
                is ResponsePayload.Log -> _events.tryEmit(ServiceEvent.Log(p.v))
                is ResponsePayload.InstanceEventPayload -> _events.tryEmit(ServiceEvent.Instance(p.v))
                is ResponsePayload.SessionEventPayload -> _events.tryEmit(ServiceEvent.Session(p.v))
                is ResponsePayload.Error -> return p.v.message.ifBlank { "Protocol error" }
                null -> Unit // unknown payload from a newer service; never treat it as a reply
                else -> if (response.requestId == pendingId) pending?.complete(p)
            }
        }
    }

    private fun shutDown(reason: String) {
        if (!scope.isActive) return // already shut down
        if (_closedReason.value == null) _closedReason.value = reason
        pending?.completeExceptionally(ServiceException(reason))
        runCatching { conn.close() }
        _events.tryEmit(ServiceEvent.Disconnected(reason))
        scope.cancel()
    }

    /** Closes the connection; any in-flight request fails. */
    fun close() = shutDown("Disconnected")

    private suspend fun send(payload: RequestPayload): ResponsePayload = requestMutex.withLock {
        _closedReason.value?.let { throw ServiceException(it) }
        val deferred = CompletableDeferred<ResponsePayload>()
        val id = ++lastId
        pendingId = id
        pending = deferred
        // The reader may have shut down between the check above and registering
        _closedReason.value?.let { deferred.completeExceptionally(ServiceException(it)) }
        try {
            @OptIn(ExperimentalSerializationApi::class)
            val bytes = proto.encodeToByteArray(Request(payload, id))
            withContext(Dispatchers.IO) { conn.writeFrame(bytes) }
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending = null
            pendingId = 0
        }
    }

    private suspend inline fun <reified T : ResponsePayload> call(payload: RequestPayload): T {
        val resp = send(payload)
        return resp as? T ?: throw ServiceException("Unexpected response ${resp::class.simpleName}")
    }

    private fun String.throwIfError() { if (isNotEmpty()) throw ServiceException(this) }

    // ─── Instances ────────────────────────────────────────────────────────────

    suspend fun listInstances(): List<InstanceInfo> =
        call<ResponsePayload.ListInstances>(RequestPayload.ListInstances(ListInstancesRequest())).v.instances

    suspend fun getInstance(id: String): GetInstanceResponse =
        call<ResponsePayload.GetInstance>(RequestPayload.GetInstance(GetInstanceRequest(id))).v
            .also { it.error.throwIfError() }

    suspend fun startServer(req: StartServerRequest): String =
        call<ResponsePayload.StartServer>(RequestPayload.StartServer(req)).v
            .also { it.error.throwIfError() }.instanceId

    suspend fun startClient(req: StartClientRequest): String =
        call<ResponsePayload.StartClient>(RequestPayload.StartClient(req)).v
            .also { it.error.throwIfError() }.instanceId

    /** Stops an instance; it is removed with its saved config unless [keep] leaves it listed as stopped. */
    suspend fun stopInstance(id: String, keep: Boolean = false) =
        call<ResponsePayload.StopInstance>(RequestPayload.StopInstance(StopInstanceRequest(id, keep))).v.error.throwIfError()

    /** Restarts an instance from its stored config; also revives stopped and failed ones. */
    suspend fun restartInstance(id: String) =
        call<ResponsePayload.RestartInstance>(RequestPayload.RestartInstance(RestartInstanceRequest(id))).v.error.throwIfError()

    /** Unset arguments are left alone; a new [config] or [listenAddrs] restarts the instance. */
    suspend fun updateInstance(
        id: String,
        name: String? = null,
        autostart: Boolean? = null,
        config: String? = null,
        listenAddrs: List<String> = emptyList(),
    ) = call<ResponsePayload.UpdateInstance>(
        RequestPayload.UpdateInstance(UpdateInstanceRequest(id, name, autostart, config, listenAddrs))
    ).v.error.throwIfError()

    // ─── Configs ──────────────────────────────────────────────────────────────

    suspend fun validateServerConfig(config: String): ValidateServerConfigResponse =
        call<ResponsePayload.ValidateServerConfig>(RequestPayload.ValidateServerConfig(ValidateServerConfigRequest(config))).v

    suspend fun validateClientConfig(config: String): ValidateClientConfigResponse =
        call<ResponsePayload.ValidateClientConfig>(RequestPayload.ValidateClientConfig(ValidateClientConfigRequest(config))).v

    suspend fun validateProviderConfig(config: String): ValidateProviderConfigResponse =
        call<ResponsePayload.ValidateProviderConfig>(RequestPayload.ValidateProviderConfig(ValidateProviderConfigRequest(config))).v

    /** Converts a client config between JSON and URL form. */
    suspend fun convertClientConfig(config: String): String =
        call<ResponsePayload.ConvertClientConfig>(RequestPayload.ConvertClientConfig(ConvertClientConfigRequest(config))).v
            .also { it.error.throwIfError() }.config

    // ─── Providers ────────────────────────────────────────────────────────────

    suspend fun listProviders(): List<ProviderInfo> =
        call<ResponsePayload.ListProviders>(RequestPayload.ListProviders(ListProvidersRequest())).v.providers

    suspend fun getProvider(uuid: String): ProviderInfo =
        call<ResponsePayload.GetProvider>(RequestPayload.GetProvider(GetProviderRequest(uuid))).v
            .also { it.error.throwIfError() }.provider ?: throw ServiceException("Provider $uuid not found")

    /** Returns the UUID of the new provider (generated by the service when [uuid] is blank). */
    suspend fun addProvider(uuid: String, name: String, config: String): String =
        call<ResponsePayload.AddProvider>(RequestPayload.AddProvider(AddProviderRequest(uuid, name, config))).v
            .also { it.error.throwIfError() }.uuid

    /** Renames a provider and/or reloads it with a new config. */
    suspend fun updateProvider(uuid: String, name: String? = null, config: String? = null) =
        call<ResponsePayload.UpdateProvider>(RequestPayload.UpdateProvider(UpdateProviderRequest(uuid, name, config))).v.error.throwIfError()

    suspend fun deleteProvider(uuid: String) =
        call<ResponsePayload.DeleteProvider>(RequestPayload.DeleteProvider(DeleteProviderRequest(uuid))).v.error.throwIfError()

    suspend fun listRoutes(providerUuid: String): List<RouteInfo> =
        call<ResponsePayload.ListProviderRoutes>(RequestPayload.ListProviderRoutes(ListProviderRoutesRequest(providerUuid))).v
            .also { it.error.throwIfError() }.routes

    /** Adds or replaces a route (matched by id). */
    suspend fun addRoute(providerUuid: String, route: RouteInfo) =
        call<ResponsePayload.AddProviderRoute>(RequestPayload.AddProviderRoute(AddProviderRouteRequest(providerUuid, route))).v.error.throwIfError()

    suspend fun deleteRoute(providerUuid: String, routeId: String) =
        call<ResponsePayload.DeleteProviderRoute>(RequestPayload.DeleteProviderRoute(DeleteProviderRouteRequest(providerUuid, routeId))).v.error.throwIfError()

    suspend fun listUsers(providerUuid: String): List<UserInfo> =
        call<ResponsePayload.ListProviderUsers>(RequestPayload.ListProviderUsers(ListProviderUsersRequest(providerUuid))).v
            .also { it.error.throwIfError() }.users

    /** Adds or replaces a user (matched by uuid). */
    suspend fun addUser(providerUuid: String, user: UserInfo) =
        call<ResponsePayload.AddProviderUser>(RequestPayload.AddProviderUser(AddProviderUserRequest(providerUuid, user))).v.error.throwIfError()

    /** Removes a user; the service also closes their live tunnels. */
    suspend fun deleteUser(providerUuid: String, userUuid: String) =
        call<ResponsePayload.DeleteProviderUser>(RequestPayload.DeleteProviderUser(DeleteProviderUserRequest(providerUuid, userUuid))).v.error.throwIfError()

    // ─── Server stats ─────────────────────────────────────────────────────────

    suspend fun getStats(instanceId: String, collect: Boolean = false, ackToken: Long? = null): GetStatsResponse =
        call<ResponsePayload.GetStats>(RequestPayload.GetStats(GetStatsRequest(instanceId, collect, ackToken))).v
            .also { it.error.throwIfError() }

    suspend fun listSessions(instanceId: String): List<SessionStats> =
        call<ResponsePayload.ListSessions>(RequestPayload.ListSessions(ListSessionsRequest(instanceId))).v
            .also { it.error.throwIfError() }.sessions

    /** Closes all tunnels of a user; returns how many were closed. */
    suspend fun kickUser(instanceId: String, userUuid: String): Int =
        call<ResponsePayload.Kick>(RequestPayload.Kick(KickRequest(instanceId, userUuid = userUuid))).v
            .also { it.error.throwIfError() }.kicked

    /** Closes all tunnels of a session; returns how many were closed. */
    suspend fun kickSession(instanceId: String, sessionId: String): Int =
        call<ResponsePayload.Kick>(RequestPayload.Kick(KickRequest(instanceId, sessionId = sessionId))).v
            .also { it.error.throwIfError() }.kicked

    companion object {
        suspend fun connect(endpoint: ServiceEndpoint, keys: ClientKeys? = null, serverKey: ByteArray? = null): TurnableServiceClient =
            withContext(Dispatchers.IO) {
                TurnableServiceClient(openServiceConnection(endpoint, keys, serverKey))
            }
    }
}
