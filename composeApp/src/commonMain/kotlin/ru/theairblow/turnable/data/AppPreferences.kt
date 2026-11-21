package ru.theairblow.turnable.data
import kotlinx.coroutines.flow.Flow
/** Persistent storage for app configuration */
expect class AppPreferences {
    val data: Flow<AppPreferencesData>
    suspend fun update(transform: (AppPreferencesData) -> AppPreferencesData)
}
