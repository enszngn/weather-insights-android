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
import kotlinx.coroutines.withTimeoutOrNull
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

    fun fetchWeather(
        lat: Double?,
        lon: Double?,
        locationName: String? = null,
        forceRefresh: Boolean = false
    ): Flow<Result<WeatherData>> = flow {
        try {
            // A manual refresh bypasses the shared Worker cache whenever coordinates are available.
            if (forceRefresh && lat != null && lon != null) {
                emit(fetchAndCacheFreshWeather(lat, lon, locationName))
                return@flow
            }

            // 1. Try fetching from Cloudflare Worker
            val workerResponse = weatherApiService.getWeather(lat, lon)
            if (workerResponse.isSuccessful) {
                val responseBody = workerResponse.body()
                if (responseBody != null && responseBody.success && responseBody.weather != null) {
                    val workerWeather = responseBody.weather
                    if (forceRefresh) {
                        // With IP-based location, use the Worker only to resolve coordinates, then
                        // fetch current conditions directly from Open-Meteo.
                        emit(
                            fetchAndCacheFreshWeather(
                                workerWeather.lat,
                                workerWeather.lon,
                                locationName ?: workerWeather.locationName
                            )
                        )
                    } else {
                        saveWeatherToCache(workerWeather)
                        emit(Result.success(workerWeather))
                    }
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
                    emit(fetchAndCacheFreshWeather(resolvedLat, resolvedLon, resolvedLocationName))
                    return@flow
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

    private suspend fun fetchAndCacheFreshWeather(
        lat: Double,
        lon: Double,
        locationName: String?
    ): Result<WeatherData> {
        val meteoResponse = openMeteoApiService.getForecast(lat, lon)
        if (!meteoResponse.isSuccessful) {
            return Result.failure(
                Exception("Open-Meteo error: ${meteoResponse.errorBody()?.string()}")
            )
        }

        val rawMeteo = meteoResponse.body()
            ?: return Result.failure(Exception("Open-Meteo response body was empty"))

        val freshWeather = rawMeteo.toWeatherData(locationName ?: "Current Location")
        saveWeatherToCache(freshWeather)

        // Keep the shared cache warm without delaying or failing the UI refresh.
        repositoryScope.launch {
            try {
                weatherApiService.uploadMeteoData(
                    WeatherPostPayload(
                        lat = lat,
                        lon = lon,
                        locationName = locationName,
                        meteoData = rawMeteo
                    )
                )
            } catch (e: Exception) {
                // Silent failure - local fresh data is already available.
            }
        }

        return Result.success(freshWeather)
    }

    suspend fun getYesterdayTemperature(lat: Double, lon: Double, dateString: String): List<Double>? {
        val localTemps = localSource.getYesterdayHourlyTemps(dateString)
        if (!localTemps.isNullOrEmpty() && localTemps.size == 24) {
            println("WeatherRepository: Yesterday cache hit for date: $dateString")
            return localTemps
        }

        println("WeatherRepository: Yesterday cache miss. Fetching from API for date: $dateString, lat: $lat, lon: $lon")
        try {
            val response = withTimeoutOrNull(HISTORICAL_REQUEST_TIMEOUT_MS) {
                openMeteoApiService.getYesterdayForecast(
                    latitude = lat,
                    longitude = lon,
                    startDate = dateString,
                    endDate = dateString
                )
            } ?: return null
            println("WeatherRepository: Yesterday API response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
            if (response.isSuccessful) {
                val body = response.body()
                val hourlyTemps = body?.hourly?.temperature2m
                println("WeatherRepository: Yesterday API response hourly temps size: ${hourlyTemps?.size}")
                if (hourlyTemps?.size == HOURS_PER_DAY) {
                    localSource.saveYesterdayHourlyTemps(dateString, hourlyTemps)
                    return hourlyTemps
                }
            } else {
                println("WeatherRepository: Yesterday API error body: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            println("WeatherRepository: Error fetching yesterday's temperature: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    private companion object {
        const val HOURS_PER_DAY = 24
        const val HISTORICAL_REQUEST_TIMEOUT_MS = 15_000L
    }
}
