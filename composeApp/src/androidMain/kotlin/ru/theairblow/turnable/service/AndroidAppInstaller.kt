package ru.theairblow.turnable.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val APK_MIME = "application/vnd.android.package-archive"

/** Downloads the release APK and opens the system package installer. */
class AndroidAppInstaller(private val context: Context) : AppInstaller {
    private val _download = MutableStateFlow<DownloadProgress?>(null)
    override val download: StateFlow<DownloadProgress?> = _download.asStateFlow()

    override suspend fun install(update: AppUpdate) {
        if (!update.hasInstaller) throw AppInstallException(AppInstallFailure.NO_INSTALLER)
        val target = cacheFile(update)
        // Retrying (e.g. after granting install permission) reuses what was already downloaded
        if (!target.isFile || target.length() == 0L) {
            // An older installer is useless once a newer release needs the cache
            target.parentFile?.listFiles { f -> f.isFile && f.absolutePath != target.absolutePath }?.forEach { it.delete() }
            downloadAppAsset(update.assetUrl, target, update.version, _download)
        }
        withContext(Dispatchers.Main) { openInstaller(target) }
    }

    private fun cacheFile(update: AppUpdate): File {
        val name = update.assetName.ifBlank { "turnable-android.apk" }
        val base = name.substringBeforeLast(".apk", name).ifBlank { "turnable-android" }
        val version = update.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.cacheDir, "app-updates"), "$base-$version.apk")
    }

    private fun openInstaller(apk: File) = try {
        // Installs from this app must be allowed before the package installer can be opened
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            throw AppInstallException(AppInstallFailure.PERMISSION)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: AppInstallException) {
        throw e
    } catch (e: Exception) {
        throw AppInstallException(AppInstallFailure.OPEN_FAILED, e)
    }
}
