package com.shieldra.protection

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.shieldra.delivery.AlertLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Gets one position fix for a confirmed security event (amendment A-34): a fresh high-accuracy fix
 * if one comes back in time, else the most recent cached fix, else null. The fix's own age and
 * provider travel with it, so a cached fix is never shown as if it were current.
 */
class LocationFix(private val context: Context) {

    suspend fun current(): AlertLocation? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        try {
            val fresh = withTimeoutOrNull(FRESH_TIMEOUT_MS) {
                val deferred = CompletableDeferred<Location?>()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .addOnSuccessListener { deferred.complete(it) }
                    .addOnFailureListener { deferred.complete(null) }
                deferred.await()
            }
            val location = fresh ?: withTimeoutOrNull(CACHED_TIMEOUT_MS) {
                val deferred = CompletableDeferred<Location?>()
                client.lastLocation
                    .addOnSuccessListener { deferred.complete(it) }
                    .addOnFailureListener { deferred.complete(null) }
                deferred.await()
            }
            location?.toAlertLocation()
        } catch (error: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toAlertLocation() = AlertLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        fixedAt = Instant.ofEpochMilli(time),
        provider = provider ?: "fused",
    )

    private companion object {
        const val FRESH_TIMEOUT_MS = 5_000L
        const val CACHED_TIMEOUT_MS = 2_000L
    }
}
