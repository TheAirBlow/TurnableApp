package ru.theairblow.turnable.service

import kotlinx.coroutines.flow.StateFlow

/** A release download in flight. [total] is -1 when the server didn't say. */
data class DownloadProgress(val version: String, val bytes: Long, val total: Long) {
    /** 0..1, or null when the size is unknown. */
    val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

/** Downloads Turnable releases and runs `turnable service server` processes on this device. */
interface LocalServiceHost {
    /** IDs of services whose process is currently alive. */
    val running: StateFlow<Set<String>>

    /** The release currently being downloaded, or null. */
    val download: StateFlow<DownloadProgress?>

    /** Downloads [version] if it isn't installed yet. */
    suspend fun install(version: String)

    /** Release tags published on GitHub, newest first. */
    suspend fun fetchAvailableVersions(): List<String>

    /** Releases already downloaded to this device, so a service can start without network access. */
    suspend fun installedVersions(): List<String>

    /**
     * Downloads [version] if needed and starts the service, returning once its socket is ready. Throws on failure.
     * [env] is added to the process environment. A non-blank [customBinary] runs that file instead of a release.
     */
    suspend fun start(id: String, version: String, env: Map<String, String> = emptyMap(), customBinary: String = "")

    suspend fun stop(id: String)

    fun socketPath(id: String): String

    /** The last part of the process's own output, useful when it fails to start. */
    suspend fun readOutput(id: String): String
}
