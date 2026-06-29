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

        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        val rawLocation: android.location.Location? = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cts.cancel()
            }
            locationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
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

        if (rawLocation == null) {
            return null
        }

        val cityName = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(rawLocation.latitude, rawLocation.longitude, 1)
                addresses?.firstOrNull()?.locality 
                    ?: addresses?.firstOrNull()?.adminArea 
                    ?: addresses?.firstOrNull()?.countryName
            } catch (e: Exception) {
                null
            }
        }

        return LocationData(rawLocation.latitude, rawLocation.longitude, cityName)
    }
}
