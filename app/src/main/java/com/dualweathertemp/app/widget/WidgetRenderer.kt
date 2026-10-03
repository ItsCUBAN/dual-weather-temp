package com.dualweathertemp.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.WeatherCache
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.sky.SkyTheme
import com.dualweathertemp.app.sky.dayPhase
import com.dualweathertemp.app.sky.skyTheme
import com.dualweathertemp.app.ui.MainActivity
import com.dualweathertemp.app.ui.WeatherText

/** Draws both home-screen widgets from the cached report. */
object WidgetRenderer {

    private val HOUR_VIEW_IDS = intArrayOf(R.id.widget_hour_0, R.id.widget_hour_1, R.id.widget_hour_2, R.id.widget_hour_3)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val report = WeatherCache(context).loadReport()
        val now = System.currentTimeMillis()

        manager.idsFor(context, DualTempWidgetProvider::class.java).forEach {
            manager.updateAppWidget(it, buildSmall(context, report, now))
        }
        manager.idsFor(context, DualTempLargeWidgetProvider::class.java).forEach {
            manager.updateAppWidget(it, buildLarge(context, report, now))
        }
    }

    fun hasAnyWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.idsFor(context, DualTempWidgetProvider::class.java).isNotEmpty() ||
            manager.idsFor(context, DualTempLargeWidgetProvider::class.java).isNotEmpty()
    }

    private fun buildSmall(context: Context, report: WeatherReport?, now: Long): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dual_temp)
        applyCommon(context, views, report, now)
        if (report == null) {
            views.setTextViewText(R.id.widget_icon, "")
            views.setTextViewText(R.id.widget_temp_f, context.getString(R.string.temp_placeholder_f))
            views.setTextViewText(R.id.widget_temp_c, context.getString(R.string.temp_placeholder_c))
            views.setTextViewText(R.id.widget_location, context.getString(R.string.widget_open_app))
            return views
        }
        val celsius = report.current.celsius
        views.setTextViewText(R.id.widget_icon, symbol(report, now))
        views.setTextViewText(R.id.widget_temp_f, context.getString(R.string.temp_f, WeatherText.fahrenheit(celsius)))
        views.setTextViewText(R.id.widget_temp_c, context.getString(R.string.temp_c, WeatherText.celsius(celsius)))
        views.setTextViewText(R.id.widget_location, locationAndTime(context, report))
        return views
    }

    private fun buildLarge(context: Context, report: WeatherReport?, now: Long): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dual_temp_large)
        applyCommon(context, views, report, now)
        if (report == null) {
            views.setTextViewText(R.id.widget_location, context.getString(R.string.widget_open_app))
            views.setTextViewText(R.id.widget_temps, context.getString(R.string.dual_temp, "--", "--"))
            views.setTextViewText(R.id.widget_description, "")
            views.setTextViewText(R.id.widget_high_low, "")
            HOUR_VIEW_IDS.forEach { views.setTextViewText(it, "") }
            return views
        }

        val current = report.current
        val isDay = report.dayPhase(now) != DayPhase.NIGHT
        views.setTextViewText(R.id.widget_location, locationAndTime(context, report))
        views.setTextViewText(
            R.id.widget_temps,
            "${symbol(report, now)} " + context.getString(
                R.string.dual_temp,
                WeatherText.fahrenheit(current.celsius),
                WeatherText.celsius(current.celsius),
            ),
        )
        views.setTextViewText(R.id.widget_description, context.getString(WeatherText.conditionRes(current.condition, isDay)))

        val today = report.today(now)
        val highLow = listOfNotNull(
            today?.highCelsius?.let {
                context.getString(R.string.widget_high, WeatherText.fahrenheit(it), WeatherText.celsius(it))
            },
            today?.lowCelsius?.let {
                context.getString(R.string.widget_low, WeatherText.fahrenheit(it), WeatherText.celsius(it))
            },
        ).joinToString("\n")
        views.setTextViewText(R.id.widget_high_low, highLow)

        val upcoming = report.hourly.filter { it.startMillis > now }
        HOUR_VIEW_IDS.forEachIndexed { index, viewId ->
            val hour = upcoming.getOrNull(index)
            val text = hour?.let {
                context.getString(
                    R.string.widget_hour,
                    WeatherText.formatHour(context, it.startMillis, report.zoneId),
                    WeatherText.conditionSymbol(it.condition, it.isDaytime),
                    WeatherText.fahrenheit(it.celsius),
                    WeatherText.celsius(it.celsius),
                )
            }.orEmpty()
            views.setTextViewText(viewId, text)
        }
        return views
    }

    /** Background gradient and tap-to-open, shared by both sizes. */
    private fun applyCommon(context: Context, views: RemoteViews, report: WeatherReport?, now: Long) {
        val theme = report?.skyTheme(now) ?: SkyTheme.CLEAR_DAY
        views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(theme))

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
    }

    private fun symbol(report: WeatherReport, now: Long): String =
        WeatherText.conditionSymbol(report.current.condition, report.dayPhase(now) != DayPhase.NIGHT)

    private fun locationAndTime(context: Context, report: WeatherReport): String {
        val location = report.locationName.ifBlank { context.getString(R.string.your_location) }
        val time = WeatherText.formatTime(context, report.timestampMillis, report.zoneId)
        return context.getString(R.string.widget_updated_at, location, time)
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
    }

    private fun AppWidgetManager.idsFor(context: Context, provider: Class<*>): IntArray =
        getAppWidgetIds(ComponentName(context, provider))
}
