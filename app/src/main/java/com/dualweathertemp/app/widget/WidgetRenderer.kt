package com.dualweathertemp.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.AlertSelector
import com.dualweathertemp.app.data.AppSettings
import com.dualweathertemp.app.data.PlaceStore
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.SettingsStore
import com.dualweathertemp.app.data.WeatherCache
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.sky.SkyTheme
import com.dualweathertemp.app.sky.dayPhase
import com.dualweathertemp.app.ui.MainActivity
import com.dualweathertemp.app.ui.WeatherText

/** Draws both home-screen widgets, each from the cached report of the place it shows. */
object WidgetRenderer {

    const val ACTION_REFRESH = "com.dualweathertemp.app.widget.REFRESH"

    private val HOUR_VIEW_IDS = intArrayOf(R.id.widget_hour_0, R.id.widget_hour_1, R.id.widget_hour_2, R.id.widget_hour_3)
    private val SMALL_PRIMARY_TEXT = intArrayOf(R.id.widget_temps)
    private val SMALL_SECONDARY_TEXT = intArrayOf(R.id.widget_location)
    private val LARGE_PRIMARY_TEXT = intArrayOf(R.id.widget_temps, R.id.widget_high_low) + HOUR_VIEW_IDS
    private val LARGE_SECONDARY_TEXT = intArrayOf(R.id.widget_location, R.id.widget_description)

    private const val TEXT_ON_DARK = 0xFFFFFFFF.toInt()
    private const val TEXT_ON_DARK_SECONDARY = 0xCCFFFFFF.toInt()
    private const val TEXT_ON_LIGHT = 0xFF14212E.toInt()
    private const val TEXT_ON_LIGHT_SECONDARY = 0xB314212E.toInt()

    /** What a widget needs to draw itself. */
    private class Content(
        val appWidgetId: Int,
        val placeId: String,
        val title: String?,
        val report: WeatherReport?,
        val settings: AppSettings,
        val now: Long,
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.idsFor(context, DualTempWidgetProvider::class.java).forEach {
            manager.updateAppWidget(it, buildSmall(context, contentFor(context, it)))
        }
        manager.idsFor(context, DualTempLargeWidgetProvider::class.java).forEach {
            manager.updateAppWidget(it, buildLarge(context, contentFor(context, it)))
        }
    }

    /** Immediate feedback after tapping ↻, until the refreshed report arrives. */
    fun showUpdating(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val updating = context.getString(R.string.widget_updating)
        manager.idsFor(context, DualTempWidgetProvider::class.java).forEach {
            manager.partiallyUpdateAppWidget(it, RemoteViews(context.packageName, R.layout.widget_dual_temp).apply {
                setTextViewText(R.id.widget_location, updating)
                setViewVisibility(R.id.widget_location, View.VISIBLE)
                setViewVisibility(R.id.widget_alert, View.GONE)
            })
        }
        manager.idsFor(context, DualTempLargeWidgetProvider::class.java).forEach {
            manager.partiallyUpdateAppWidget(it, RemoteViews(context.packageName, R.layout.widget_dual_temp_large).apply {
                setTextViewText(R.id.widget_location, updating)
            })
        }
    }

    fun hasAnyWidget(context: Context): Boolean = allIds(context).isNotEmpty()

    /** Places shown by at least one widget; these are refreshed in the background. */
    fun placesInUse(context: Context): Set<String> {
        val widgetPlaces = WidgetPlaces(context)
        return allIds(context).map { widgetPlaces.placeFor(it) }.toSet()
    }

    private fun contentFor(context: Context, appWidgetId: Int): Content {
        var placeId = WidgetPlaces(context).placeFor(appWidgetId)
        val place = if (placeId == Places.CURRENT_ID) null else PlaceStore(context).find(placeId)
        // The city was removed from the app: fall back to the phone's location.
        if (placeId != Places.CURRENT_ID && place == null) placeId = Places.CURRENT_ID
        val report = WeatherCache(context).loadReport(placeId)
        return Content(
            appWidgetId = appWidgetId,
            placeId = placeId,
            title = place?.shortLabel ?: report?.locationName?.takeIf { it.isNotBlank() },
            report = report,
            settings = SettingsStore(context).load(),
            now = System.currentTimeMillis(),
        )
    }

    private fun buildSmall(context: Context, content: Content): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dual_temp)
        val theme = applyCommon(context, views, content)
        tintText(views, theme, SMALL_PRIMARY_TEXT, SMALL_SECONDARY_TEXT)

        val report = content.report
        if (report == null) {
            views.setTextViewText(R.id.widget_icon, "")
            views.setTextViewText(R.id.widget_temps, "--° | --°")
            views.setTextViewText(R.id.widget_location, context.getString(R.string.widget_open_app))
            showAlert(views, null)
            return views
        }
        val (firstUnit, secondUnit) = WeatherText.units(content.settings.unitOrder)
        val celsius = report.current.celsius
        views.setTextViewText(R.id.widget_icon, symbol(report, content.now))
        views.setTextViewText(R.id.widget_temps, "${WeatherText.temp(celsius, firstUnit)} | ${WeatherText.temp(celsius, secondUnit)}")
        views.setTextViewText(R.id.widget_location, titleAndTime(context, content, report))
        showAlert(views, importantAlert(report), hideWhenShown = R.id.widget_location)
        return views
    }

    private fun buildLarge(context: Context, content: Content): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dual_temp_large)
        val theme = applyCommon(context, views, content)
        tintText(views, theme, LARGE_PRIMARY_TEXT, LARGE_SECONDARY_TEXT)

        val report = content.report
        if (report == null) {
            views.setTextViewText(R.id.widget_location, context.getString(R.string.widget_open_app))
            views.setTextViewText(R.id.widget_temps, "--° / --°")
            views.setTextViewText(R.id.widget_description, "")
            views.setTextViewText(R.id.widget_high_low, "")
            HOUR_VIEW_IDS.forEach { views.setTextViewText(it, "") }
            showAlert(views, null)
            return views
        }

        val order = content.settings.unitOrder
        val (firstUnit, secondUnit) = WeatherText.units(order)
        val current = report.current
        val phase = report.dayPhase(content.now)
        views.setTextViewText(R.id.widget_location, titleAndTime(context, content, report))
        views.setTextViewText(R.id.widget_temps, "${symbol(report, content.now)} ${WeatherText.dualTemp(current.celsius, order)}")
        views.setTextViewText(R.id.widget_description, context.getString(WeatherText.conditionRes(current.condition, phase)))

        val today = report.today(content.now)
        val highLow = listOfNotNull(
            today?.highCelsius?.let { context.getString(R.string.widget_high, shortDual(it, firstUnit, secondUnit)) },
            today?.lowCelsius?.let { context.getString(R.string.widget_low, shortDual(it, firstUnit, secondUnit)) },
        ).joinToString("\n")
        views.setTextViewText(R.id.widget_high_low, highLow)
        showAlert(views, importantAlert(report))

        val upcoming = report.hourly.filter { it.startMillis > content.now }
        HOUR_VIEW_IDS.forEachIndexed { index, viewId ->
            val text = upcoming.getOrNull(index)?.let {
                context.getString(
                    R.string.widget_hour,
                    WeatherText.formatHour(context, it.startMillis, report.zoneId),
                    WeatherText.conditionSymbol(it.condition, it.isDaytime),
                    shortDual(it.celsius, firstUnit, secondUnit),
                )
            }.orEmpty()
            views.setTextViewText(viewId, text)
        }
        return views
    }

    /** Background, tap to open the app on this widget's place, and the ↻ button. */
    private fun applyCommon(context: Context, views: RemoteViews, content: Content): SkyTheme {
        val theme = SkyTheme.resolve(content.settings.background, content.report, content.now)
        views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(theme))
        views.setInt(R.id.widget_refresh, "setColorFilter", if (theme.isLight) TEXT_ON_LIGHT else TEXT_ON_DARK)

        val openApp = PendingIntent.getActivity(
            context,
            // One request code per widget, so each keeps its own place in the intent.
            content.appWidgetId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_PLACE_ID, content.placeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        val refresh = PendingIntent.getBroadcast(
            context,
            content.appWidgetId,
            Intent(context, DualTempWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        return theme
    }

    private fun tintText(views: RemoteViews, theme: SkyTheme, primary: IntArray, secondary: IntArray) {
        val (main, muted) = if (theme.isLight) {
            TEXT_ON_LIGHT to TEXT_ON_LIGHT_SECONDARY
        } else {
            TEXT_ON_DARK to TEXT_ON_DARK_SECONDARY
        }
        primary.forEach { views.setTextColor(it, main) }
        secondary.forEach { views.setTextColor(it, muted) }
    }

    /** Shows the red alert strip, hiding [hideWhenShown] while it is visible. */
    private fun showAlert(views: RemoteViews, event: String?, hideWhenShown: Int? = null) {
        val visible = event != null
        views.setViewVisibility(R.id.widget_alert, if (visible) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_alert, event?.let { "⚠ $it" }.orEmpty())
        hideWhenShown?.let { views.setViewVisibility(it, if (visible) View.GONE else View.VISIBLE) }
    }

    /** The most serious important alert: warnings before watches. */
    private fun importantAlert(report: WeatherReport): String? =
        report.alerts
            .filter { AlertSelector.isImportant(it.event) }
            .sortedBy { if (WeatherText.alertLevel(it.event) == WeatherText.AlertLevel.WARNING) 0 else 1 }
            .firstOrNull()?.event

    /** "87°/31°" in the user's unit order. */
    private fun shortDual(celsius: Double, first: WeatherText.TempUnit, second: WeatherText.TempUnit) =
        "${WeatherText.number(celsius, first)}°/${WeatherText.number(celsius, second)}°"

    private fun symbol(report: WeatherReport, now: Long): String =
        WeatherText.conditionSymbol(report.current.condition, report.dayPhase(now))

    private fun titleAndTime(context: Context, content: Content, report: WeatherReport): String {
        val title = content.title ?: context.getString(R.string.your_location)
        val time = WeatherText.formatTime(context, report.timestampMillis, report.zoneId)
        return context.getString(R.string.widget_updated_at, title, time)
    }

    @DrawableRes
    private fun backgroundFor(theme: SkyTheme): Int = when (theme) {
        SkyTheme.CLEAR_DAY -> R.drawable.widget_bg_clear_day
        SkyTheme.PARTLY_CLOUDY_DAY -> R.drawable.widget_bg_partly_cloudy_day
        SkyTheme.CLOUDY_DAY -> R.drawable.widget_bg_cloudy_day
        SkyTheme.CLOUDY_NIGHT -> R.drawable.widget_bg_cloudy_night
        SkyTheme.RAIN -> R.drawable.widget_bg_rain
        SkyTheme.STORM -> R.drawable.widget_bg_storm
        SkyTheme.SNOW -> R.drawable.widget_bg_snow
        SkyTheme.FOG -> R.drawable.widget_bg_fog
        SkyTheme.TWILIGHT -> R.drawable.widget_bg_twilight
        SkyTheme.CLEAR_NIGHT -> R.drawable.widget_bg_clear_night
        SkyTheme.LIGHT -> R.drawable.widget_bg_light
        SkyTheme.DARK -> R.drawable.widget_bg_dark
    }

    private fun allIds(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return manager.idsFor(context, DualTempWidgetProvider::class.java).toList() +
            manager.idsFor(context, DualTempLargeWidgetProvider::class.java).toList()
    }

    private fun AppWidgetManager.idsFor(context: Context, provider: Class<*>): IntArray =
        getAppWidgetIds(ComponentName(context, provider))
}
