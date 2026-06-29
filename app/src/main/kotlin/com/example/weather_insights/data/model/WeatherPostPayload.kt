package com.example.weather_insights.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherPostPayload(
    val lat: Double,
    val lon: Double,
    val meteoData: OpenMeteoResponse
)
