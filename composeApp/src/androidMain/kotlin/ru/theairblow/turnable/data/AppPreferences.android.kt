package ru.theairblow.turnable.data
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
private val Context.dataStore by preferencesDataStore(name = "app_prefs")
private val KEY_DATA = stringPreferencesKey("data")
private val json = Json { ignoreUnknownKeys = true }
actual class AppPreferences(private val context: Context) {
    actual val data: Flow<AppPreferencesData> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_DATA] ?: return@map AppPreferencesData()
        runCatching { json.decodeFromString<AppPreferencesData>(raw) }.getOrElse { AppPreferencesData() }
    }
    actual suspend fun update(transform: (AppPreferencesData) -> AppPreferencesData) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DATA]
                ?.let { runCatching { json.decodeFromString<AppPreferencesData>(it) }.getOrElse { AppPreferencesData() } }
                ?: AppPreferencesData()
            prefs[KEY_DATA] = json.encodeToString(transform(current))
        }
    }
}
