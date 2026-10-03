package com.dualweathertemp.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Small widget (2×1): current temperature in °F and °C. Tapping it opens the app. */
class DualTempWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        onWidgetsUpdated(context)

    override fun onEnabled(context: Context) = WeatherUpdateWorker.schedulePeriodic(context)

    override fun onDisabled(context: Context) = onWidgetTypeRemoved(context)
}

/** Large widget (4×2): current conditions, today's high/low and the next hours. */
class DualTempLargeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        onWidgetsUpdated(context)

    override fun onEnabled(context: Context) = WeatherUpdateWorker.schedulePeriodic(context)

    override fun onDisabled(context: Context) = onWidgetTypeRemoved(context)
}

private fun onWidgetsUpdated(context: Context) {
    WidgetRenderer.updateAll(context)
    WeatherUpdateWorker.schedulePeriodic(context)
    WeatherUpdateWorker.refreshNow(context)
}

/** Background refreshes stop only once no widget of either size is left. */
private fun onWidgetTypeRemoved(context: Context) {
    if (!WidgetRenderer.hasAnyWidget(context)) WeatherUpdateWorker.cancelAll(context)
}
