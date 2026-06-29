package com.example.weather_insights.data.repository

import com.example.weather_insights.data.model.WeatherData
import com.example.weather_insights.data.model.WeatherPostPayload
import com.example.weather_insights.data.network.OpenMeteoApiService
import com.example.weather_insights.data.network.WeatherApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApiService: WeatherApiService,
    private val openMeteoApiService: OpenMeteoApiService
) {
    fun fetchWeather(lat: Double, lon: Double): Flow<Result<WeatherData>> = flow {
        try {
            // 1. Try fetching from Cloudflare Worker
            val workerResponse = weatherApiService.getWeather(lat, lon)
            if (workerResponse.isSuccessful) {
                val responseBody = workerResponse.body()
                if (responseBody != null && responseBody.success && responseBody.weather != null) {
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
                        // 3. Post raw meteo data back to Worker to cache and get formatted weather object
                        val payload = WeatherPostPayload(lat = lat, lon = lon, meteoData = rawMeteo)
                        val postResponse = weatherApiService.uploadMeteoData(payload)
                        if (postResponse.isSuccessful) {
                            val postBody = postResponse.body()
                            if (postBody != null && postBody.success && postBody.weather != null) {
                                emit(Result.success(postBody.weather))
                                return@flow
                            }
                        }
                        emit(Result.failure(Exception("Failed to upload and format weather from Worker")))
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
