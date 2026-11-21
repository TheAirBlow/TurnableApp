package ru.theairblow.turnable.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val RELEASES_API = "https://api.github.com/repos/TheAirBlow/Turnable/releases"
private const val DOWNLOAD_BASE = "https://github.com/TheAirBlow/Turnable/releases/download"
private const val PROGRESS_STEP_BYTES = 128 * 1024L
private const val SOCKET_WAIT_MS = 10_000L
private const val OUTPUT_TAIL_BYTES = 16 * 1024L
private const val MAX_SOCKET_PATH_BYTES = 100
private val ansiEscape = Regex("\u001B\\[[0-9;]*m")

private val json = Json { ignoreUnknownKeys = true }

private val versionOrder = Comparator<String> { a, b -> compareVersions(b, a) }

@Serializable private data class GithubRelease(@SerialName("tag_name") val tagName: String = "")

@Serializable private data class ServiceJson(
    @SerialName("unix_socket") val unixSocket: String,
    @SerialName("persist_dir") val persistDir: String,
)

/**
 * Runs Turnable service processes from [baseDir]:
 * `bin/` downloaded releases, `sockets/` unix sockets, `services/<id>/` config, persisted state and output log.
 *
 * @param assetName release asset for this OS/arch, e.g. `turnable-android-arm64`
 */
open class JvmLocalServiceHost(private val baseDir: File, private val assetName: String?) : LocalServiceHost {
    private val processes = ConcurrentHashMap<String, Process>()
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    override val running: StateFlow<Set<String>> = _running.asStateFlow()

    private val _download = MutableStateFlow<DownloadProgress?>(null)
    override val download: StateFlow<DownloadProgress?> = _download.asStateFlow()

    override suspend fun install(version: String) = withContext(Dispatchers.IO) { ensureBinary(version); Unit }

    /** Called after the first service starts and after the last one stops (e.g. for an Android foreground service). */
    protected open fun onRunningChanged(anyRunning: Boolean) {}

    override suspend fun fetchAvailableVersions(): List<String> = withContext(Dispatchers.IO) {
        val resp = httpGet(RELEASES_API, accept = "application/vnd.github+json")
        if (resp.code != 200) throw IOException("GitHub returned HTTP ${resp.code}")
        json.decodeFromString<List<GithubRelease>>(resp.body).map { it.tagName }.filter { it.isNotBlank() && isSupportedCore(it) }
    }

    override suspend fun installedVersions(): List<String> = withContext(Dispatchers.IO) {
        File(baseDir, "bin").listFiles { f -> f.isDirectory && assetName != null && File(f, assetName).exists() }
            ?.map { it.name }?.filter(::isSupportedCore)?.sortedWith(versionOrder).orEmpty()
    }

    /** Unix socket paths are capped near 108 bytes, so deep data directories fall back to a short temp path. */
    override fun socketPath(id: String): String {
        val preferred = File(baseDir, "sockets/$id.sock").absolutePath
        if (preferred.toByteArray().size <= MAX_SOCKET_PATH_BYTES) return preferred
        val owner = baseDir.absolutePath.hashCode().toUInt().toString(16)
        return File(System.getProperty("java.io.tmpdir"), "turnable-$owner-$id.sock").absolutePath
    }

    private fun serviceDir(id: String) = File(baseDir, "services/$id")
    private fun outputFile(id: String) = File(serviceDir(id), "output.log")

    override suspend fun start(id: String, version: String, env: Map<String, String>, customBinary: String) = withContext(Dispatchers.IO) {
        stop(id)
        val binary = if (customBinary.isBlank()) ensureBinary(version) else File(customBinary).also {
            if (!it.isFile) throw IOException("Custom core not found: $customBinary")
            if (!it.canExecute() && !it.setExecutable(true)) throw IOException("Custom core is not executable: $customBinary")
        }
        val socket = File(socketPath(id)).also { it.parentFile.mkdirs(); it.delete() } // drop a stale socket
        val dir = serviceDir(id).also { it.mkdirs() }
        val config = File(dir, "service.json")
        val persist = File(dir, "data").also { it.mkdirs() }
        config.writeText(json.encodeToString(ServiceJson.serializer(), ServiceJson(socket.absolutePath, persist.absolutePath)))

        // Output goes to a file: an undrained pipe would eventually block the service on write
        val process = ProcessBuilder(binary.absolutePath, "service", "server", "-c", config.absolutePath)
            .directory(dir)
            .redirectErrorStream(true)
            .redirectOutput(outputFile(id))
            .apply { environment().putAll(env) }
            .start()
        processes[id] = process
        publishRunning()
        watchExit(id, process)

        var waited = 0L
        while (!socket.exists()) {
            if (!process.isAlive) throw IOException("Service exited with code ${process.exitValue()}")
            if (waited >= SOCKET_WAIT_MS) {
                stop(id)
                throw IOException("Service did not open its socket in ${SOCKET_WAIT_MS / 1000}s")
            }
            delay(100); waited += 100
        }
    }

    private fun watchExit(id: String, process: Process) {
        Thread({
            process.waitFor()
            processes.remove(id, process)
            publishRunning()
        }, "turnable-$id-watch").apply { isDaemon = true }.start()
    }

    override suspend fun stop(id: String) = withContext(Dispatchers.IO) {
        val process = processes.remove(id) ?: return@withContext
        process.destroy()
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        publishRunning()
    }

    /** Stops every service; safe to call from any thread. */
    fun stopAll() {
        processes.keys.toList().forEach { id -> processes.remove(id)?.destroy() }
        publishRunning()
    }

    override suspend fun readOutput(id: String): String = withContext(Dispatchers.IO) {
        val file = outputFile(id)
        if (!file.exists()) return@withContext ""
        RandomAccessFile(file, "r").use { raf ->
            val start = maxOf(0L, raf.length() - OUTPUT_TAIL_BYTES)
            raf.seek(start)
            ByteArray((raf.length() - start).toInt()).also { raf.readFully(it) }.decodeToString().replace(ansiEscape, "")
        }
    }

    @Synchronized
    private fun publishRunning() {
        val now = processes.filterValues { it.isAlive }.keys.toSet()
        val before = _running.value
        _running.update { now }
        if (before.isEmpty() != now.isEmpty()) onRunningChanged(now.isNotEmpty())
    }

    private fun ensureBinary(version: String): File {
        val asset = assetName ?: throw IOException("No Turnable build is published for this device's platform")
        require(version.isNotBlank()) { "No version selected" }
        val target = File(baseDir, "bin/$version/$asset")
        if (target.exists()) return target
        target.parentFile.mkdirs()

        val tmp = File(target.parentFile, "$asset.part")
        val conn = openHttp("$DOWNLOAD_BASE/$version/$asset")
        try {
            when (conn.responseCode) {
                200 -> Unit
                404 -> throw IOException("Release $version has no $asset build")
                else -> throw IOException("Download failed: HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            _download.value = DownloadProgress(version, 0, total)
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPublish = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastPublish >= PROGRESS_STEP_BYTES) {
                            lastPublish = done
                            _download.value = DownloadProgress(version, done, total)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
            _download.value = null
        }
        // Rename only once complete so an interrupted download is never mistaken for an installed binary
        if (!tmp.setExecutable(true) || !tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Could not install $asset")
        }
        return target
    }
}
