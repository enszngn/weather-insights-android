package com.example.weather_insights.data.repository

import com.example.weather_insights.data.datasource.WeatherLocalSource
import com.example.weather_insights.data.mapper.toWeatherData
import com.example.weather_insights.data.model.WeatherData
import com.example.weather_insights.data.model.WeatherPostPayload
import com.example.weather_insights.data.network.OpenMeteoApiService
import com.example.weather_insights.data.network.WeatherApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApiService: WeatherApiService,
    private val openMeteoApiService: OpenMeteoApiService,
    private val localSource: WeatherLocalSource
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getCachedWeather(): WeatherData? {
        return localSource.getCachedWeather()
    }

    private suspend fun saveWeatherToCache(data: WeatherData) {
        localSource.saveWeatherToCache(data)
    }

    fun fetchWeather(lat: Double, lon: Double): Flow<Result<WeatherData>> = flow {
        try {
            // 1. Try fetching from Cloudflare Worker
            val workerResponse = weatherApiService.getWeather(lat, lon)
            if (workerResponse.isSuccessful) {
                val responseBody = workerResponse.body()
                if (responseBody != null && responseBody.success && responseBody.weather != null) {
                    saveWeatherToCache(responseBody.weather)
                    emit(Result.success(responseBody.weather))
                    return@flow
                }
            }

            // 2. If HTTP 404, fetch from Open-Meteo
            if (workerResponse.code() == 404) {
                val meteoResponse = openMeteoApiService.getForecast(lat, lon)
                if (meteoResponse.isSuccessful) {
                    val rawMeteo = meteoResponse.body()
                    if (rawMeteo != null) {
                        // 3. Post raw meteo data back to Worker to cache in the background (fire and forget)
                        repositoryScope.launch {
                            try {
                                val payload = WeatherPostPayload(lat = lat, lon = lon, meteoData = rawMeteo)
                                weatherApiService.uploadMeteoData(payload)
                            } catch (e: Exception) {
                                // Silent failure - do not affect UI state
                            }
                        }

                        // Map and emit locally structured data immediately to stop loading spinner
                        val localWeatherData = rawMeteo.toWeatherData()
                        saveWeatherToCache(localWeatherData)
                        emit(Result.success(localWeatherData))
                        return@flow
                    } else {
                        emit(Result.failure(Exception("Open-Meteo response body was empty")))
                    }
                } else {
                    emit(Result.failure(Exception("Open-Meteo error: ${meteoResponse.errorBody()?.string()}")))
                }
            } else {
                emit(Result.failure(Exception("Worker error: ${workerResponse.errorBody()?.string()}")))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(Result.failure(e))
        }
    }
}
