package com.dualweathertemp.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean = hasLocationPermission(appContext)

    /**
     * A fresh fix if one can be obtained quickly, otherwise the last known location.
     * Tries Wi-Fi/cell first (fast, low battery) and GPS only if that finds nothing.
     */
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        return freshLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, BALANCED_TIMEOUT_MS)
            ?: freshLocation(Priority.PRIORITY_HIGH_ACCURACY, GPS_TIMEOUT_MS)
            ?: lastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(priority: Int, timeoutMs: Long): Location? = try {
        withTimeoutOrNull(timeoutMs) {
            client.getCurrentLocation(priority, CancellationTokenSource().token).await()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Cached location only; safe to call from the background (may return null there). */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            client.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        private const val BALANCED_TIMEOUT_MS = 10_000L
        private const val GPS_TIMEOUT_MS = 20_000L

        fun hasLocationPermission(context: Context): Boolean = PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
