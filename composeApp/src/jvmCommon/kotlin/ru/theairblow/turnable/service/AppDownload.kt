package ru.theairblow.turnable.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private const val PROGRESS_STEP_BYTES = 128 * 1024L

/**
 * Streams [url] into [target], publishing [progress] while it runs.
 * The file only appears under its final name once the download is complete.
 */
internal suspend fun downloadAppAsset(
    url: String,
    target: File,
    version: String,
    progress: MutableStateFlow<DownloadProgress?>,
) = withContext(Dispatchers.IO) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, "${target.name}.part")
    val conn = openHttp(url)
    try {
        if (conn.responseCode != 200) throw AppInstallException(AppInstallFailure.DOWNLOAD_FAILED)
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: 0
        progress.value = DownloadProgress(version, 0, total)
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
                        progress.value = DownloadProgress(version, done, total)
                    }
                }
            }
        }
        if (target.exists() && !target.delete()) throw AppInstallException(AppInstallFailure.DOWNLOAD_FAILED)
        if (!tmp.renameTo(target)) throw AppInstallException(AppInstallFailure.DOWNLOAD_FAILED)
    } catch (e: AppInstallException) {
        tmp.delete()
        throw e
    } catch (e: Throwable) {
        tmp.delete()
        throw AppInstallException(AppInstallFailure.DOWNLOAD_FAILED, e)
    } finally {
        conn.disconnect()
        progress.value = null
    }
}
