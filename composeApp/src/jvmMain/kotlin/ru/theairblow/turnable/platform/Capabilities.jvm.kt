package ru.theairblow.turnable.platform

actual val captchaInApp: Boolean = false

// Desktop packages need the user to finish in the system installer, so the release page is the safe path
actual val appSelfUpdate: Boolean = false

actual val appUpdateAssetName: String? = System.getProperty("os.name").lowercase().let { os ->
    when {
        "linux" in os -> "turnable-linux.deb"
        "mac" in os || "darwin" in os -> "turnable-macos.dmg"
        "windows" in os -> "turnable-windows.msi"
        else -> null
    }
}
