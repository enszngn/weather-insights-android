package com.weatherinsights.daily.forecast.live.radar.data.location

interface LocationTracker {
    suspend fun getCurrentLocation(forceRefresh: Boolean = false): LocationData?
    suspend fun getCityName(latitude: Double, longitude: Double): String?
    fun hasLocationPermission(): Boolean
}

data class LocationData(
    val latitude: Double,
    val longitude: Double
)
