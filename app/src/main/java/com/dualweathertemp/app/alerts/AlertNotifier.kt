package com.dualweathertemp.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.AlertSelector
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.SettingsStore
import com.dualweathertemp.app.data.WeatherAlert
import com.dualweathertemp.app.ui.MainActivity
import com.dualweathertemp.app.ui.WeatherText
import java.time.ZoneId

/** Posts one notification per important NWS alert and remembers which ones were already sent. */
class AlertNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    /** Whether the app should still show Android's notification permission dialog. */
    fun shouldAskPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !canNotify() &&
            !prefs.getBoolean(KEY_ASKED, false)

    fun markPermissionAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    /**
     * Notifies the alerts of one place that are important and new; [alerts] is its full active
     * list. Follows the alert switches in Settings.
     */
    fun notifyNew(alerts: List<WeatherAlert>, placeId: String, locationName: String, zone: ZoneId) {
        val settings = SettingsStore(appContext).load()
        val wanted = settings.alertsEnabled && (placeId == Places.CURRENT_ID || settings.alertsForSavedPlaces)
        if (!wanted) return

        val key = notifiedKey(placeId)
        val notified = prefs.getStringSet(key, emptySet()).orEmpty()
        val fresh = if (canNotify()) AlertSelector.toNotify(alerts, notified) else emptyList()

        if (fresh.isNotEmpty()) {
            ensureChannel()
            fresh.forEach { post(it, placeId, locationName, zone) }
        }
        prefs.edit()
            .putStringSet(key, AlertSelector.remember(alerts, notified, fresh))
            .apply()
    }

    /** Drops what was notified for a place the user removed. */
    fun forgetPlace(placeId: String) {
        prefs.edit().remove(notifiedKey(placeId)).apply()
    }

    private fun post(alert: WeatherAlert, placeId: String, locationName: String, zone: ZoneId) {
        // Re-checked here because the permission can be revoked at any moment.
        if (!canNotify()) return

        val place = locationName.ifBlank { appContext.getString(R.string.your_location) }
        val summary = alert.endsMillis
            ?.let { appContext.getString(R.string.alert_notification_until, place, WeatherText.formatUntil(appContext, it, zone)) }
            ?: place
        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val color = when (WeatherText.alertLevel(alert.event)) {
            WeatherText.AlertLevel.WARNING -> WARNING_COLOR
            else -> WATCH_COLOR
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(color)
            .setContentTitle(alert.event)
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$summary\n${appContext.getString(R.string.alert_notification_detail)}")
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            // Removes the notification by itself once the alert has expired.
            .apply {
                val remaining = alert.endsMillis?.minus(System.currentTimeMillis())
                if (remaining != null && remaining > 0) setTimeoutAfter(remaining)
            }
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify("$placeId|${alert.key}".hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post; nothing else to do.
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = appContext.getString(R.string.alert_channel_description) }
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        // The phone's location keeps the key used before saved cities existed.
        fun notifiedKey(placeId: String) =
            if (placeId == Places.CURRENT_ID) KEY_NOTIFIED else "${KEY_NOTIFIED}_$placeId"

        const val PREFS_NAME = "alert_notifications"
        const val KEY_NOTIFIED = "notified_keys"
        const val KEY_ASKED = "permission_asked"
        const val CHANNEL_ID = "severe_weather_alerts"
        const val WARNING_COLOR = 0xFFD32F2F.toInt()
        const val WATCH_COLOR = 0xFFE65100.toInt()
    }
}
