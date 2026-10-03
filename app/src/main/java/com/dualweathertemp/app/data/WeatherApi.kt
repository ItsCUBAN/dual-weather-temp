package com.dualweathertemp.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** Minimal HTTP client for api.weather.gov (free, no API key, US only). */
class WeatherApi {

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // weather.gov rejects requests that don't identify the app.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/geo+json")

            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code, url)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://api.weather.gov"
        private const val USER_AGENT = "DualWeatherTemp/1.0 (Android)"
        private const val TIMEOUT_MS = 15_000
    }
}
