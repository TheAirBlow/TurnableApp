package ru.theairblow.turnable.platform

/** True where the app can host the platform's call page itself and capture the captcha tokens for the user. */
expect val captchaInApp: Boolean

/** Installer asset of this platform's app build in GitHub releases, or null where there is none. */
expect val appUpdateAssetName: String?

/** True where the app can download its own release and hand it to the platform installer. */
expect val appSelfUpdate: Boolean
