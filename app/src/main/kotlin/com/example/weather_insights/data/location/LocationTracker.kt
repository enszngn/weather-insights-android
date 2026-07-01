package com.example.weather_insights.data.location

interface LocationTracker {
    suspend fun getCurrentLocation(): LocationData?
    suspend fun getCityName(latitude: Double, longitude: Double): String?
    fun hasLocationPermission(): Boolean
}

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val cityName: String? = null
)
