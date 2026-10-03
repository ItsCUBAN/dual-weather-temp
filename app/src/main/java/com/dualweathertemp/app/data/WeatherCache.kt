package com.dualweathertemp.app.data

import android.content.Context
import kotlinx.serialization.json.Json

/** Last known coordinates and report, shared by the app screen and the widgets. */
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

    fun saveReport(report: WeatherReport) {
        prefs.edit()
            .putString(KEY_REPORT, json.encodeToString(WeatherReport.serializer(), report))
            .apply()
    }

    fun loadReport(): WeatherReport? {
        val stored = prefs.getString(KEY_REPORT, null) ?: return null
        return try {
            json.decodeFromString(WeatherReport.serializer(), stored)
        } catch (e: IllegalArgumentException) {
            // Saved by an older version of the app; it will be replaced on the next refresh.
            null
        }
    }

    private companion object {
        const val PREFS_NAME = "weather_cache"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_REPORT = "report_json"

        val json = Json { ignoreUnknownKeys = true }
    }
}
