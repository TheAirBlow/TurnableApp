plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}


fun git(vararg args: String): String = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

// CI passes the release version; otherwise it is derived from git like Turnable does: `0.8.4-3-g3da3d29`, `+` when the tree has uncommitted changes
val appVersion: String = providers.gradleProperty("appVersion").orNull ?: run {
    val dirty = git("status", "--porcelain", "--untracked-files=no").isNotEmpty()
    val describe = Regex("""^v?(.+)-(\d+)-g([0-9a-f]+)$""").matchEntire(git("describe", "--tags", "--long", "--match", "v[0-9]*"))
    val base = when {
        describe == null -> "0.0.0-${git("rev-list", "--count", "HEAD").ifEmpty { "0" }}-g${git("rev-parse", "--short", "HEAD").ifEmpty { "unknown" }}"
        describe.groupValues[2] == "0" -> describe.groupValues[1]
        else -> "${describe.groupValues[1]}-${describe.groupValues[2]}-g${describe.groupValues[3]}"
    }
    if (dirty) "$base+" else base
}
extra["appVersion"] = appVersion
