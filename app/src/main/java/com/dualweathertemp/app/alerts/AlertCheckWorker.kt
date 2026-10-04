package com.dualweathertemp.app.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
import com.dualweathertemp.app.widget.WidgetRenderer
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Checks for severe weather alerts every 15 minutes (Android's minimum for background work;
 * the phone may delay it while asleep). Only downloads the alert lists, not the full forecast.
 *
 * Besides notifying, it refreshes the alerts stored with each report, so the widgets' alert
 * strip is never more than a check behind.
 */
class AlertCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private data class Target(val placeId: String, val latitude: Double, val longitude: Double, val name: String?)

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).load()
        val hasWidgets = WidgetRenderer.hasAnyWidget(applicationContext)
        if (!settings.alertsEnabled && !hasWidgets) return Result.success()

        val cache = WeatherCache(applicationContext)
        val notifier = AlertNotifier(applicationContext)
        val repository = WeatherRepository()
        var networkFailed = false

        for (target in targets(cache, settings.alertsForSavedPlaces)) {
            val alerts = try {
                repository.fetchAlerts(target.latitude, target.longitude)
            } catch (e: WeatherException) {
                networkFailed = networkFailed || e.reason == WeatherException.Reason.NETWORK
                continue
            }
            val report = cache.loadReport(target.placeId)
            notifier.notifyNew(
                alerts = alerts,
                placeId = target.placeId,
                locationName = target.name ?: report?.locationName.orEmpty(),
                zone = report?.zoneId ?: ZoneId.systemDefault(),
            )
            if (report != null && report.alerts != alerts) cache.saveReport(report.copy(alerts = alerts), target.placeId)
        }

        if (hasWidgets) WidgetRenderer.updateAll(applicationContext)
        return if (networkFailed && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    /** The phone's location, plus saved cities that notify or are shown by a widget. */
    private suspend fun targets(cache: WeatherCache, allSaved: Boolean): List<Target> {
        val location = LocationProvider(applicationContext).lastKnownLocation()
        val current = location?.let { it.latitude to it.longitude } ?: cache.loadLocation()
        val targets = mutableListOf<Target>()
        current?.let { targets += Target(Places.CURRENT_ID, it.first, it.second, name = null) }
        val onWidgets = WidgetRenderer.placesInUse(applicationContext)
        PlaceStore(applicationContext).load()
            .filter { allSaved || it.id in onWidgets }
            .forEach { targets += Target(it.id, it.latitude, it.longitude, it.shortLabel) }
        return targets
    }

    companion object {
        private const val WORK_NAME = "severe_alert_check"
        private const val INTERVAL_MINUTES = 15L
        private const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
