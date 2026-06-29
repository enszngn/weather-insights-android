package com.example.weather_insights.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("generationtime_ms") val generationTimeMs: Double,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int,
    val timezone: String,
    @SerialName("timezone_abbreviation") val timezoneAbbreviation: String,
    val elevation: Double,
    val current: OpenMeteoCurrent,
    val hourly: OpenMeteoHourly,
    val daily: OpenMeteoDaily
)

@Serializable
data class OpenMeteoCurrent(
    val time: String,
    val interval: Int,
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Int,
    @SerialName("wind_speed_10m") val windSpeed10m: Double,
    @SerialName("weather_code") val weatherCode: Int
)

@Serializable
data class OpenMeteoHourly(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double>,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: List<Int>,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>
)

@Serializable
data class OpenMeteoDaily(
    val time: List<String>,
    @SerialName("uv_index_max") val uvIndexMax: List<Double>
)
