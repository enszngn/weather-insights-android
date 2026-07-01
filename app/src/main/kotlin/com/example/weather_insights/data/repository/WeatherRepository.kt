package com.example.weather_insights.data.repository

import com.example.weather_insights.data.datasource.WeatherLocalSource
import com.example.weather_insights.data.model.ForecastDay
import com.example.weather_insights.data.model.HourlyForecast
import com.example.weather_insights.data.model.OpenMeteoResponse
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

    suspend fun saveWeatherToCache(data: WeatherData) {
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

private fun OpenMeteoResponse.toWeatherData(): WeatherData {
    val forecastDays = daily.time.mapIndexed { i, dateStr ->
        val startIndex = i * 24
        val dayHourly = mutableListOf<HourlyForecast>()
        for (h in 0..23) {
            val idx = startIndex + h
            if (idx < hourly.time.size) {
                val hourlyTime = hourly.time[idx].substringAfter('T').take(5)
                dayHourly.add(
                    HourlyForecast(
                        time = hourlyTime,
                        temp = hourly.temperature2m.getOrNull(idx) ?: 0.0,
                        humidity = hourly.relativeHumidity2m.getOrNull(idx) ?: 0,
                        windSpeed = hourly.windSpeed10m.getOrNull(idx) ?: 0.0,
                        weatherCode = hourly.weatherCode.getOrNull(idx) ?: 0
                    )
                )
            }
        }

        val dailyTemp = if (i == 0) current.temperature2m else (dayHourly.map { it.temp }.average().takeIf { !it.isNaN() } ?: 0.0)
        val dailyHumidity = if (i == 0) current.relativeHumidity2m else (dayHourly.map { it.humidity }.average().takeIf { !it.isNaN() }?.toInt() ?: 0)
        val dailyWindSpeed = if (i == 0) current.windSpeed10m else (dayHourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0)
        val dailyWeatherCode = if (i == 0) current.weatherCode else (dayHourly.firstOrNull()?.weatherCode ?: 0)

        val sunriseTime = daily.sunrise?.getOrNull(i)?.substringAfter('T')?.take(5) ?: "06:00"
        val sunsetTime = daily.sunset?.getOrNull(i)?.substringAfter('T')?.take(5) ?: "20:00"

        ForecastDay(
            date = dateStr,
            temp = dailyTemp,
            humidity = dailyHumidity,
            windSpeed = dailyWindSpeed,
            uvIndex = daily.uvIndexMax.getOrNull(i) ?: 0.0,
            weatherCode = dailyWeatherCode,
            hourly = dayHourly,
            sunrise = sunriseTime,
            sunset = sunsetTime
        )
    }

    return WeatherData(
        locationName = "Current Location",
        lat = latitude,
        lon = longitude,
        forecast = forecastDays
    )
}
