package com.dualweathertemp.app.data

import android.content.Context

enum class UnitOrder { FAHRENHEIT_FIRST, CELSIUS_FIRST }

enum class BackgroundStyle {
    /** Gradient that follows the weather and time of day. */
    SKY,
    LIGHT,
    DARK,
}

data class AppSettings(
    val unitOrder: UnitOrder = UnitOrder.FAHRENHEIT_FIRST,
    val widgetRefreshMinutes: Int = DEFAULT_WIDGET_REFRESH_MINUTES,
    val background: BackgroundStyle = BackgroundStyle.SKY,
    val alertsEnabled: Boolean = true,
    /** Off by default: only alerts where the user is. */
    val alertsForSavedPlaces: Boolean = false,
) {
    companion object {
        const val DEFAULT_WIDGET_REFRESH_MINUTES = 30
        val WIDGET_REFRESH_CHOICES = listOf(30, 60, 120)
    }
}

/** Persists [AppSettings]; read by the app, the widgets and the background workers. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        unitOrder = enumOrDefault(prefs.getString(KEY_UNIT_ORDER, null), UnitOrder.FAHRENHEIT_FIRST),
        widgetRefreshMinutes = prefs.getInt(KEY_WIDGET_REFRESH, AppSettings.DEFAULT_WIDGET_REFRESH_MINUTES)
            .takeIf { it in AppSettings.WIDGET_REFRESH_CHOICES }
            ?: AppSettings.DEFAULT_WIDGET_REFRESH_MINUTES,
        background = enumOrDefault(prefs.getString(KEY_BACKGROUND, null), BackgroundStyle.SKY),
        alertsEnabled = prefs.getBoolean(KEY_ALERTS, true),
        alertsForSavedPlaces = prefs.getBoolean(KEY_ALERTS_SAVED_PLACES, false),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_UNIT_ORDER, settings.unitOrder.name)
            .putInt(KEY_WIDGET_REFRESH, settings.widgetRefreshMinutes)
            .putString(KEY_BACKGROUND, settings.background.name)
            .putBoolean(KEY_ALERTS, settings.alertsEnabled)
            .putBoolean(KEY_ALERTS_SAVED_PLACES, settings.alertsForSavedPlaces)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_UNIT_ORDER = "unit_order"
        const val KEY_WIDGET_REFRESH = "widget_refresh_minutes"
        const val KEY_BACKGROUND = "background"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_ALERTS_SAVED_PLACES = "alerts_saved_places"
    }
}
