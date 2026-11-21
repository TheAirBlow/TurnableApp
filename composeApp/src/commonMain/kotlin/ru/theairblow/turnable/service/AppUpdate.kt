package ru.theairblow.turnable.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.theairblow.turnable.platform.appUpdateAssetName

private const val APP_RELEASES_API = "https://api.github.com/repos/TheAirBlow/TurnableApp/releases"

private val json = Json { ignoreUnknownKeys = true }

/** A published app release; [assetUrl] is the installer for this platform, blank when there is none. */
data class AppUpdate(
    val version: String,
    /** Release page in a browser. */
    val url: String,
    /** Direct download of this platform's installer. */
    val assetUrl: String = "",
    val assetName: String = "",
) {
    val hasInstaller: Boolean get() = assetUrl.isNotBlank()
}

@Serializable
private data class AppGithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AppGithubAsset> = emptyList(),
)

@Serializable
private data class AppGithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
)

/** Newest published app release, or null when the releases list is empty. */
private suspend fun fetchLatestAppRelease(): AppUpdate? {
    val resp = httpGet(APP_RELEASES_API, accept = "application/vnd.github+json")
    check(resp.code == 200) { "GitHub returned HTTP ${resp.code}" }
    return json.decodeFromString<List<AppGithubRelease>>(resp.body)
        .filter { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
        .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }
        ?.let { release ->
            val asset = release.assets.firstOrNull { it.name == appUpdateAssetName }
            AppUpdate(
                version = release.tagName,
                url = release.htmlUrl,
                assetUrl = asset?.url.orEmpty(),
                assetName = asset?.name.orEmpty(),
            )
        }
}

/** The latest app release when it is newer than [APP_VERSION], otherwise null. */
suspend fun fetchAppUpdate(): AppUpdate? =
    fetchLatestAppRelease()?.takeIf { compareVersions(it.version, APP_VERSION) > 0 }
