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
import com.dualweathertemp.app.data.WeatherCache
import com.dualweathertemp.app.data.WeatherException
import com.dualweathertemp.app.data.WeatherRepository
import com.dualweathertemp.app.location.LocationProvider
import java.util.concurrent.TimeUnit

/**
 * Refreshes the widget in the background.
 *
 * The app only asks for "while in use" location, so in the background this uses the phone's
 * cached location when Android provides it and otherwise the coordinates saved the last time
 * the app was opened.
 */
class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cache = WeatherCache(applicationContext)
        val location = LocationProvider(applicationContext).lastKnownLocation()
        val coordinates = if (location != null) {
            cache.saveLocation(location.latitude, location.longitude)
            location.latitude to location.longitude
        } else {
            cache.loadLocation() ?: return Result.success()
        }

        return try {
            val report = WeatherRepository().fetch(coordinates.first, coordinates.second)
            cache.saveReport(report)
            WidgetRenderer.updateAll(applicationContext)
            Result.success()
        } catch (e: WeatherException) {
            val canRetry = e.reason == WeatherException.Reason.NETWORK && runAttemptCount < MAX_RETRIES
            if (canRetry) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "dual_temp_periodic"
        private const val REFRESH_WORK = "dual_temp_refresh"
        private const val REFRESH_INTERVAL_MINUTES = 30L
        private const val MAX_RETRIES = 3

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
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
