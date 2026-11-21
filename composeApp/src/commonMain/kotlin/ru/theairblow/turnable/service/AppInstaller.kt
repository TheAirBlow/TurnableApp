package ru.theairblow.turnable.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Why an app update could not be handed to the system installer. */
enum class AppInstallFailure { NO_INSTALLER, PERMISSION, OPEN_FAILED, DOWNLOAD_FAILED }

class AppInstallException(val failure: AppInstallFailure, cause: Throwable? = null) : Exception(cause?.message, cause)

/** Downloads an app release for this platform and hands it to the system installer. */
interface AppInstaller {
    /** The download in flight, or null. */
    val download: StateFlow<DownloadProgress?>

    /** Downloads the release asset for [update] and opens the platform installer. */
    suspend fun install(update: AppUpdate)

    /** For platforms that expect the user to install from the release page. */
    object Unsupported : AppInstaller {
        override val download: StateFlow<DownloadProgress?> = MutableStateFlow(null)

        override suspend fun install(update: AppUpdate) = throw AppInstallException(AppInstallFailure.NO_INSTALLER)
    }
}
