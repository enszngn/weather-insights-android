package com.example.weather_insights.data.location

interface LocationTracker {
    suspend fun getCurrentLocation(): LocationData?
}

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val cityName: String? = null
)
