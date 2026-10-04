package com.dualweathertemp.app.data

import android.content.Context
import kotlinx.serialization.json.Json

/** Last known coordinates and reports (one per place), shared by the app screen and the widgets. */
class WeatherCache(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLocation(latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(KEY_LATITUDE, latitude.toString())
            .putString(KEY_LONGITUDE, longitude.toString())
            .apply()
    }

    fun loadLocation(): Pair<Double, Double>? {
        val latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        return latitude to longitude
    }

    /** Stores the latest report of a place ([Places.CURRENT_ID] for the phone's location). */
    fun saveReport(report: WeatherReport, placeId: String = Places.CURRENT_ID) {
        prefs.edit()
            .putString(reportKey(placeId), json.encodeToString(WeatherReport.serializer(), report))
            .apply()
    }

    fun loadReport(placeId: String = Places.CURRENT_ID): WeatherReport? {
        val stored = prefs.getString(reportKey(placeId), null) ?: return null
        return try {
            json.decodeFromString(WeatherReport.serializer(), stored)
        } catch (e: IllegalArgumentException) {
            // Saved by an older version of the app; it will be replaced on the next refresh.
            null
        }
    }

    fun removeReport(placeId: String) {
        prefs.edit().remove(reportKey(placeId)).apply()
    }

    private companion object {
        // The phone's location keeps the key used before saved cities existed.
        fun reportKey(placeId: String) = if (placeId == Places.CURRENT_ID) KEY_REPORT else "${KEY_REPORT}_$placeId"

        const val PREFS_NAME = "weather_cache"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_REPORT = "report_json"

        val json = Json { ignoreUnknownKeys = true }
    }
}
