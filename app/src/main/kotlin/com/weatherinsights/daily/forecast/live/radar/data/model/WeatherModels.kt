package com.weatherinsights.daily.forecast.live.radar.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val success: Boolean,
    val weather: WeatherData? = null
)

@Serializable
data class WeatherData(
    val locationName: String,
    val lat: Double,
    val lon: Double,
    val forecast: List<ForecastDay>
)

@Serializable
data class ForecastDay(
    val date: String,
    val temp: Double,
    val humidity: Int,
    val windSpeed: Double,
    val uvIndex: Double,
    val weatherCode: Int,
    val hourly: List<HourlyForecast>,
    val sunrise: String? = null,
    val sunset: String? = null
)

@Serializable
data class HourlyForecast(
    val time: String,
    val temp: Double,
    val humidity: Int,
    val windSpeed: Double,
    val weatherCode: Int,
    val precipitationProbability: Int = 0
)

@Serializable
data class WorkerErrorResponse(
    val success: Boolean,
    val reason: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val locationName: String? = null
)
