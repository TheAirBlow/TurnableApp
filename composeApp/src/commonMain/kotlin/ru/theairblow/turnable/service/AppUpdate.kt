package ru.theairblow.turnable.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val APP_RELEASES_API = "https://api.github.com/repos/TheAirBlow/TurnableApp/releases"

private val json = Json { ignoreUnknownKeys = true }

/** A published app release; [version] is its git tag and [url] the release page. */
data class AppUpdate(val version: String, val url: String)

@Serializable
private data class AppGithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/** Newest published app release, or null when the releases list is empty. */
private suspend fun fetchLatestAppRelease(): AppUpdate? {
    val resp = httpGet(APP_RELEASES_API, accept = "application/vnd.github+json")
    check(resp.code == 200) { "GitHub returned HTTP ${resp.code}" }
    return json.decodeFromString<List<AppGithubRelease>>(resp.body)
        .filter { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
        .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }
        ?.let { AppUpdate(it.tagName, it.htmlUrl) }
}

/** The latest app release when it is newer than [APP_VERSION], otherwise null. */
suspend fun fetchAppUpdate(): AppUpdate? =
    fetchLatestAppRelease()?.takeIf { compareVersions(it.version, APP_VERSION) > 0 }
