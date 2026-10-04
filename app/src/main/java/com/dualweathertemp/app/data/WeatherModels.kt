package com.dualweathertemp.app.data

import kotlinx.serialization.Serializable
import java.time.ZoneId

/** Simplified sky condition, used for icons, descriptions and the background. */
@Serializable
enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, STORM, SNOW, FOG }

@Serializable
data class CurrentConditions(
    val celsius: Double,
    val condition: Condition,
    val feelsLikeCelsius: Double? = null,
    val humidityPercent: Double? = null,
    val dewpointCelsius: Double? = null,
    val windKmh: Double? = null,
    val windDirectionDegrees: Double? = null,
    val pressurePa: Double? = null,
    val visibilityMeters: Double? = null,
    val stationId: String? = null,
)

@Serializable
data class HourlyForecast(
    val startMillis: Long,
    val celsius: Double,
    val condition: Condition,
    val isDaytime: Boolean,
    val precipitationChance: Int? = null,
)

@Serializable
data class DailyForecast(
    /** ISO date (yyyy-MM-dd) in the location's time zone. */
    val date: String,
    val highCelsius: Double? = null,
    val lowCelsius: Double? = null,
    val condition: Condition,
    val precipitationChance: Int? = null,
)

@Serializable
data class WeatherAlert(
    /** NWS event name, e.g. "Heat Advisory". */
    val event: String,
    val endsMillis: Long? = null,
    /**
     * Stays the same when NWS updates or extends an alert ("event|onset"), unlike the alert id,
     * so one alert produces one notification.
     */
    val key: String = "",
)

@Serializable
data class WeatherReport(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val current: CurrentConditions,
    val source: Source,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
    val uvIndex: Double? = null,
    val uvIndexMax: Double? = null,
    val usAqi: Int? = null,
    val timestampMillis: Long,
) {
    val zoneId: ZoneId
        get() = try {
            ZoneId.of(timeZone)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

    /** Today's high/low, if the forecast still includes today. */
    fun today(nowMillis: Long): DailyForecast? {
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toString()
        return daily.firstOrNull { it.date == today }
    }

    @Serializable
    enum class Source {
        /** Measured by the nearest weather station. */
        OBSERVATION,

        /** Hourly forecast, used when no recent observation is available. */
        FORECAST,
    }
}

class WeatherException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {

    enum class Reason { NO_LOCATION, OUTSIDE_US, NETWORK, NO_DATA }
}
