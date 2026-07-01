package com.example.weather_insights.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DefaultLocationTracker @Inject constructor(
    private val locationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : LocationTracker {

    override suspend fun getCurrentLocation(): LocationData? {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
            return null
        }

        val lastLocation: android.location.Location? = suspendCancellableCoroutine { continuation ->
            locationClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
                .addOnCanceledListener {
                    continuation.resume(null)
                }
        }

        val MAX_LOCATION_AGE_MS = 15 * 60 * 1000 // 15 minutes
        val rawLocation: android.location.Location? = if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < MAX_LOCATION_AGE_MS) {
            lastLocation
        } else {
            val cts = com.google.android.gms.tasks.CancellationTokenSource()
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation {
                        cts.cancel()
                    }
                    locationClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    )
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        continuation.resume(null)
                    }
                }
            }
        }

        if (rawLocation == null) {
            return null
        }

        return LocationData(rawLocation.latitude, rawLocation.longitude, null)
    }

    override suspend fun getCityName(latitude: Double, longitude: Double): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.locality 
                    ?: addresses?.firstOrNull()?.adminArea 
                    ?: addresses?.firstOrNull()?.countryName
            } catch (e: Exception) {
                null
            }
        }
    }
}
