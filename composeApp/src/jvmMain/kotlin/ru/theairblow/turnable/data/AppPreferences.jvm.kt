package ru.theairblow.turnable.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.theairblow.turnable.service.desktopHome
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.prefs.Preferences

private val json = Json { ignoreUnknownKeys = true }

/** Stored as JSON under `~/.turnable`; java.util.prefs caps values at 8 KB, which keys and subscriptions exceed. */
actual class AppPreferences(private val file: File = File(desktopHome(), "prefs.json")) {
    private val _data = MutableStateFlow(load())
    actual val data: Flow<AppPreferencesData> = _data

    private val writeLock = Mutex()

    actual suspend fun update(transform: (AppPreferencesData) -> AppPreferencesData) = writeLock.withLock {
        _data.update(transform)
        save(_data.value)
    }

    private fun load(): AppPreferencesData {
        // Earlier versions kept the data in java.util.prefs
        val raw = runCatching { file.readText() }.getOrNull()
            ?: runCatching { Preferences.userRoot().node("ru/theairblow/turnable").get("data", null) }.getOrNull()
            ?: return AppPreferencesData()
        return runCatching { json.decodeFromString<AppPreferencesData>(raw) }.getOrElse { AppPreferencesData() }
    }

    private fun save(data: AppPreferencesData) {
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(data))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
