package com.weatherinsights.data.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weatherinsights.data.model.NotificationPreferences
import com.weatherinsights.data.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface WeatherLocalSource {
    suspend fun getCachedWeather(): WeatherData?
    suspend fun saveWeatherToCache(data: WeatherData)

    /**
     * Returns the persisted refresh (count, windowStartMs) pair, or null if none saved yet.
     */
    suspend fun getRefreshState(): Pair<Int, Long>?

    /**
     * Persists the refresh counter and the epoch-ms timestamp of the current window's first refresh.
     */
    suspend fun saveRefreshState(count: Int, windowStart: Long)

    suspend fun getNotificationPreferences(): NotificationPreferences
    suspend fun saveNotificationPreferences(prefs: NotificationPreferences)
    suspend fun getLastNotificationDate(key: String): String?
    suspend fun saveLastNotificationDate(key: String, dateString: String)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_settings")

@Singleton
class DataStoreWeatherLocalSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : WeatherLocalSource {

    private val CACHED_WEATHER_KEY = stringPreferencesKey("cached_weather")
    private val REFRESH_COUNT_KEY = stringPreferencesKey("refresh_count")
    private val REFRESH_WINDOW_START_KEY = stringPreferencesKey("refresh_window_start")
    private val NOTIFICATION_PREFS_KEY = stringPreferencesKey("notification_preferences")

    override suspend fun getCachedWeather(): WeatherData? {
        return try {
            val jsonString = context.dataStore.data.map { preferences ->
                preferences[CACHED_WEATHER_KEY]
            }.firstOrNull()
            if (jsonString != null) {
                json.decodeFromString<WeatherData>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveWeatherToCache(data: WeatherData) {
        try {
            val jsonString = json.encodeToString(WeatherData.serializer(), data)
            context.dataStore.edit { preferences ->
                preferences[CACHED_WEATHER_KEY] = jsonString
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun getRefreshState(): Pair<Int, Long>? {
        return try {
            val prefs = context.dataStore.data.firstOrNull() ?: return null
            val countStr = prefs[REFRESH_COUNT_KEY] ?: return null
            val windowStr = prefs[REFRESH_WINDOW_START_KEY] ?: return null
            val count = countStr.toIntOrNull() ?: return null
            val windowStart = windowStr.toLongOrNull() ?: return null
            count to windowStart
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveRefreshState(count: Int, windowStart: Long) {
        try {
            context.dataStore.edit { prefs ->
                prefs[REFRESH_COUNT_KEY] = count.toString()
                prefs[REFRESH_WINDOW_START_KEY] = windowStart.toString()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun getNotificationPreferences(): NotificationPreferences {
        return try {
            val jsonString = context.dataStore.data.map { preferences ->
                preferences[NOTIFICATION_PREFS_KEY]
            }.firstOrNull()
            if (jsonString != null) {
                json.decodeFromString<NotificationPreferences>(jsonString)
            } else {
                NotificationPreferences()
            }
        } catch (e: Exception) {
            NotificationPreferences()
        }
    }

    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
        try {
            val jsonString = json.encodeToString(NotificationPreferences.serializer(), prefs)
            context.dataStore.edit { preferences ->
                preferences[NOTIFICATION_PREFS_KEY] = jsonString
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun getLastNotificationDate(key: String): String? {
        return try {
            val prefs = context.dataStore.data.firstOrNull() ?: return null
            prefs[stringPreferencesKey("last_notif_$key")]
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun saveLastNotificationDate(key: String, dateString: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("last_notif_$key")] = dateString
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
