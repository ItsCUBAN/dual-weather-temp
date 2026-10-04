package com.dualweathertemp.app.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dualweathertemp.app.data.PlaceStore
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.SettingsStore
import com.dualweathertemp.app.data.WeatherCache
import com.dualweathertemp.app.data.WeatherException
import com.dualweathertemp.app.data.WeatherRepository
import com.dualweathertemp.app.location.LocationProvider
import java.util.concurrent.TimeUnit

/**
 * Refreshes the places shown by widgets in the background, at the interval chosen in Settings.
 *
 * The app only asks for "while in use" location, so for the phone's location this uses
 * Android's cached location when it provides one and otherwise the coordinates saved the
 * last time the app was opened.
 */
class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cache = WeatherCache(applicationContext)
        val repository = WeatherRepository()
        val savedPlaces = PlaceStore(applicationContext).load().associateBy { it.id }
        var networkFailed = false

        for (placeId in WidgetRenderer.placesInUse(applicationContext)) {
            val coordinates = if (placeId == Places.CURRENT_ID) {
                currentCoordinates(cache)
            } else {
                savedPlaces[placeId]?.let { it.latitude to it.longitude }
            } ?: continue

            try {
                cache.saveReport(repository.fetch(coordinates.first, coordinates.second), placeId)
            } catch (e: WeatherException) {
                networkFailed = networkFailed || e.reason == WeatherException.Reason.NETWORK
            }
        }

        WidgetRenderer.updateAll(applicationContext)
        return if (networkFailed && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private suspend fun currentCoordinates(cache: WeatherCache): Pair<Double, Double>? {
        val location = LocationProvider(applicationContext).lastKnownLocation() ?: return cache.loadLocation()
        cache.saveLocation(location.latitude, location.longitude)
        return location.latitude to location.longitude
    }

    companion object {
        private const val PERIODIC_WORK = "dual_temp_periodic"
        private const val REFRESH_WORK = "dual_temp_refresh"
        private const val MAX_RETRIES = 3

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** (Re)schedules the periodic refresh with the interval from Settings. */
        fun schedulePeriodic(context: Context) {
            val minutes = SettingsStore(context).load().widgetRefreshMinutes.toLong()
            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            // UPDATE: picks up a new interval from Settings without losing the schedule.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(networkConstraint)
                .build()
            // KEEP: enqueuing work can re-trigger onUpdate, so never restart a refresh in flight.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC_WORK)
                cancelUniqueWork(REFRESH_WORK)
            }
        }
    }
}
