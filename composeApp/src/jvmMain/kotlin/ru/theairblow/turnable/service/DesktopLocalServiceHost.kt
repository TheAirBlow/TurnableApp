package ru.theairblow.turnable.service

import java.io.File

/** Where the desktop app keeps its data: `~/.turnable`, or `$TURNABLE_HOME` when set. */
fun desktopHome(): File = System.getenv("TURNABLE_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
    ?: File(System.getProperty("user.home"), ".turnable")

/** Local services on desktop live under [desktopHome]. */
class DesktopLocalServiceHost : JvmLocalServiceHost(desktopHome(), desktopAssetName())

private fun desktopAssetName(): String? {
    val os = System.getProperty("os.name").lowercase()
    val goos = when {
        "linux" in os -> "linux"
        "mac" in os || "darwin" in os -> "darwin"
        "windows" in os -> "windows"
        "freebsd" in os -> "freebsd"
        else -> return null
    }
    val goarch = when (System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        "x86", "i386", "i686" -> "386"
        else -> return null
    }
    return "turnable-$goos-$goarch" + if (goos == "windows") ".exe" else ""
}
