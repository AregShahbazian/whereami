package com.mby4m.whereami

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Everything that talks to the platform: permission state, location services,
 * the fused provider, and the geocoder. Deliberately free of UI concerns.
 */
class LocationSource(private val context: Context) {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasFineLocation(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasCoarseLocation(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasAnyLocationPermission(): Boolean = hasFineLocation() || hasCoarseLocation()

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Whether the user has location turned on for the whole device. */
    fun locationServicesEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * A fresh fix, falling back to the last known one if the provider times out —
     * a stale fix beats a dead end. Null when neither is available.
     *
     * Caller must have checked [hasAnyLocationPermission] first; the SuppressLint
     * is for that contract, which the linter cannot see across the call.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        // With coarse-only permission the fused provider cannot honour high accuracy
        // anyway, and asking for it just wastes battery on a doomed GPS attempt.
        val priority = if (hasFineLocation()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setDurationMillis(FIX_TIMEOUT_MILLIS)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MILLIS)
            .build()

        // The token lets a cancelled coroutine (Refresh tapped again, screen gone)
        // actually stop the provider rather than leave it running out its duration.
        val cancellation = CancellationTokenSource()
        val fresh = awaitTask(cancellation) { fused.getCurrentLocation(request, cancellation.token) }
        return fresh ?: awaitTask(null) { fused.lastLocation }
    }

    /** Bridges a Play Services [com.google.android.gms.tasks.Task] to a coroutine. */
    private suspend fun <T> awaitTask(
        cancellation: CancellationTokenSource?,
        start: () -> com.google.android.gms.tasks.Task<T>,
    ): T? = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancellation?.cancel() }
        try {
            start()
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                .addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            if (continuation.isActive) continuation.resume(null)
        }
    }

    /**
     * Best-effort reverse geocode. Returns null rather than throwing on every
     * failure path — no geocoder backend, no result, no network, timeout — so the
     * caller can always still show coordinates.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Place? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context)
        return withTimeoutOrNull(GEOCODE_TIMEOUT_MILLIS) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocodeAsync(geocoder, latitude, longitude)
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)
                            ?.firstOrNull()
                            ?.toPlace()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): Place? = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.toPlace())
            }

            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(null)
            }
        })
    }

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 15_000L
        const val MAX_FIX_AGE_MILLIS = 30_000L
        const val GEOCODE_TIMEOUT_MILLIS = 10_000L
    }
}

/** Keeps only the fields this app shows; blanks are normalised to null. */
private fun android.location.Address.toPlace(): Place {
    val street = listOfNotNull(subThoroughfare, thoroughfare)
        .joinToString(" ")
        .ifBlank { null }
        ?: featureName?.ifBlank { null }
    return Place(
        street = street,
        locality = locality?.ifBlank { null } ?: subAdminArea?.ifBlank { null },
        adminArea = adminArea?.ifBlank { null },
        country = countryName?.ifBlank { null },
    )
}
