package com.weatherinsights.daily.forecast.live.radar.data.repository

import com.weatherinsights.daily.forecast.live.radar.data.datasource.WeatherLocalSource
import com.weatherinsights.daily.forecast.live.radar.data.mapper.toWeatherData
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherPostPayload
import com.weatherinsights.daily.forecast.live.radar.data.network.OpenMeteoApiService
import com.weatherinsights.daily.forecast.live.radar.data.network.WeatherApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import com.weatherinsights.daily.forecast.live.radar.data.model.WorkerErrorResponse
import kotlinx.serialization.json.Json

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApiService: WeatherApiService,
    private val openMeteoApiService: OpenMeteoApiService,
    private val localSource: WeatherLocalSource,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getCachedWeather(): WeatherData? {
        return localSource.getCachedWeather()
    }

    private suspend fun saveWeatherToCache(data: WeatherData) {
        localSource.saveWeatherToCache(data)
    }

    fun fetchWeather(lat: Double?, lon: Double?, locationName: String? = null): Flow<Result<WeatherData>> = flow {
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

            val errorBodyString = if (!workerResponse.isSuccessful) {
                workerResponse.errorBody()?.string()
            } else {
                null
            }

            // 2. If HTTP 404, check if we need to fall back to Open-Meteo
            if (workerResponse.code() == 404) {
                var resolvedLat = lat
                var resolvedLon = lon
                var resolvedLocationName = locationName

                if (!errorBodyString.isNullOrEmpty()) {
                    try {
                        val errorResponse = json.decodeFromString<WorkerErrorResponse>(errorBodyString)
                        if (resolvedLat == null) resolvedLat = errorResponse.lat
                        if (resolvedLon == null) resolvedLon = errorResponse.lon
                        if (resolvedLocationName == null) resolvedLocationName = errorResponse.locationName
                    } catch (e: Exception) {
                        // Ignore parsing errors and rely on original values
                    }
                }

                if (resolvedLat != null && resolvedLon != null) {
                    val meteoResponse = openMeteoApiService.getForecast(resolvedLat, resolvedLon)
                    if (meteoResponse.isSuccessful) {
                        val rawMeteo = meteoResponse.body()
                        if (rawMeteo != null) {
                            // 3. Post raw meteo data back to Worker to cache in the background (fire and forget)
                            repositoryScope.launch {
                                try {
                                    val payload = WeatherPostPayload(
                                        lat = resolvedLat,
                                        lon = resolvedLon,
                                        locationName = resolvedLocationName,
                                        meteoData = rawMeteo
                                    )
                                    weatherApiService.uploadMeteoData(payload)
                                } catch (e: Exception) {
                                    // Silent failure - do not affect UI state
                                }
                            }

                            // Map and emit locally structured data immediately to stop loading spinner
                            val localWeatherData = rawMeteo.toWeatherData(resolvedLocationName ?: "Current Location")
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
                    val errorMsg = if (!errorBodyString.isNullOrEmpty()) "Worker error: $errorBodyString" else "Worker error (status code ${workerResponse.code()})"
                    emit(Result.failure(Exception(errorMsg)))
                }
            } else {
                val errorMsg = if (!errorBodyString.isNullOrEmpty()) "Worker error: $errorBodyString" else "Worker error (status code ${workerResponse.code()})"
                emit(Result.failure(Exception(errorMsg)))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    suspend fun getYesterdayTemperature(lat: Double, lon: Double, dateString: String): List<Double>? {
        val localTemps = localSource.getYesterdayHourlyTemps(dateString)
        if (localTemps != null) {
            return localTemps
        }

        try {
            val response = openMeteoApiService.getYesterdayForecast(
                url = "https://api.open-meteo.com/v1/forecast",
                latitude = lat,
                longitude = lon,
                startDate = dateString,
                endDate = dateString
            )
            if (response.isSuccessful) {
                val body = response.body()
                val hourlyTemps = body?.hourly?.temperature2m
                if (!hourlyTemps.isNullOrEmpty()) {
                    localSource.saveYesterdayHourlyTemps(dateString, hourlyTemps)
                    return hourlyTemps
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
}
