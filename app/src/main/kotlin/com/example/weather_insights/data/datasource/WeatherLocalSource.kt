package com.example.weather_insights.data.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.weather_insights.data.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface WeatherLocalSource {
    suspend fun getCachedWeather(): WeatherData?
    suspend fun saveWeatherToCache(data: WeatherData)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_settings")

@Singleton
class DataStoreWeatherLocalSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : WeatherLocalSource {

    private val CACHED_WEATHER_KEY = stringPreferencesKey("cached_weather")

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
}
