package com.weatherinsights.data.location

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

    /** Single source of truth for location permission checks. */
    private fun hasPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    override suspend fun getCurrentLocation(forceRefresh: Boolean): LocationData? {
        if (!hasPermission()) return null

        val cts = com.google.android.gms.tasks.CancellationTokenSource()

        // When forceRefresh is true (user-triggered refresh), always request a live GPS fix
        // to pick up emulator location changes or real device movements.
        val rawLocation: android.location.Location? = if (forceRefresh) {
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cts.cancel() }
                    locationClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    )
                        .addOnSuccessListener { location -> continuation.resume(location) }
                        .addOnFailureListener { continuation.resume(null) }
                        .addOnCanceledListener { continuation.resume(null) }
                }
            }
        } else {
            // Normal path: use lastLocation cache if it is fresh (≤ 15 minutes)
            val lastLocation: android.location.Location? = suspendCancellableCoroutine { continuation ->
                locationClient.lastLocation
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { continuation.resume(null) }
                    .addOnCanceledListener { continuation.resume(null) }
            }

            val MAX_LOCATION_AGE_MS = 15 * 60 * 1000 // 15 minutes
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < MAX_LOCATION_AGE_MS) {
                lastLocation
            } else {
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { cts.cancel() }
                        locationClient.getCurrentLocation(
                            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            cts.token
                        )
                            .addOnSuccessListener { location -> continuation.resume(location) }
                            .addOnFailureListener { continuation.resume(null) }
                            .addOnCanceledListener { continuation.resume(null) }
                    }
                }
            }
        }

        if (rawLocation == null) return null
        return LocationData(rawLocation.latitude, rawLocation.longitude)
    }

    override suspend fun getCityName(latitude: Double, longitude: Double): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Use ENGLISH locale for consistent place name results independent of
                // the host machine's locale (important on emulators routed via developer's network).
                val geocoder = android.location.Geocoder(context, java.util.Locale.ENGLISH)
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.locality
                    ?: addresses?.firstOrNull()?.adminArea
                    ?: addresses?.firstOrNull()?.countryName
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun hasLocationPermission(): Boolean = hasPermission()
}
