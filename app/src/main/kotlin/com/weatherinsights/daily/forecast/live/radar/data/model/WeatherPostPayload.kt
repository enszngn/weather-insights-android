package com.weatherinsights.daily.forecast.live.radar.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherPostPayload(
    val lat: Double,
    val lon: Double,
    val locationName: String? = null,
    val meteoData: OpenMeteoResponse
)
