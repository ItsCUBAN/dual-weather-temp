package com.dualweathertemp.app.widget

import android.content.Context
import com.dualweathertemp.app.data.Places

/** Which place each placed widget shows; widgets without a choice show the phone's location. */
class WidgetPlaces(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun placeFor(appWidgetId: Int): String = prefs.getString(key(appWidgetId), null) ?: Places.CURRENT_ID

    fun set(appWidgetId: Int, placeId: String) {
        prefs.edit().putString(key(appWidgetId), placeId).apply()
    }

    fun remove(appWidgetIds: IntArray) {
        prefs.edit().apply { appWidgetIds.forEach { remove(key(it)) } }.apply()
    }

    /** Widgets showing a city the user just removed go back to the phone's location. */
    fun forgetPlace(placeId: String) {
        val editor = prefs.edit()
        prefs.all.filterValues { it == placeId }.keys.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"

    private companion object {
        const val PREFS_NAME = "widget_places"
    }
}
