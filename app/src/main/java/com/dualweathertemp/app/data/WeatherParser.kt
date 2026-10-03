package com.dualweathertemp.app.data

import com.dualweathertemp.app.util.TemperatureUtils
import com.dualweathertemp.app.util.Units
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** URLs and place info returned by the weather.gov `/points/{lat},{lon}` endpoint. */
data class PointInfo(
    val observationStationsUrl: String,
    val forecastUrl: String,
    val forecastHourlyUrl: String,
    val locationName: String,
    val timeZone: String,
)

data class UvInfo(val current: Double?, val todayMax: Double?)

/** Pure JSON parsing for weather.gov and Open-Meteo responses, free of I/O so it can be unit tested. */
object WeatherParser {

    private const val MAX_HOURS = 24
    private const val MAX_DAYS = 7

    fun parsePoint(body: String): PointInfo {
        val properties = JSONObject(body).getJSONObject("properties")
        val place = properties.optJSONObject("relativeLocation")?.optJSONObject("properties")
        val name = listOfNotNull(place?.optString("city"), place?.optString("state"))
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return PointInfo(
            observationStationsUrl = properties.getString("observationStations"),
            forecastUrl = properties.getString("forecast"),
            forecastHourlyUrl = properties.getString("forecastHourly"),
            locationName = name,
            timeZone = properties.optString("timeZone"),
        )
    }

    /** Station URLs, nearest first. */
    fun parseStationUrls(body: String): List<String> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            features.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Current conditions from `/stations/{id}/observations/latest`, or null when the station
     * reported no temperature or the observation is older than [maxAge].
     */
    fun parseObservation(body: String, now: Instant, maxAge: Duration): CurrentConditions? {
        val properties = JSONObject(body).optJSONObject("properties") ?: return null

        val observedAt = parseInstant(properties.optString("timestamp"))
        if (observedAt != null && Duration.between(observedAt, now) > maxAge) return null

        val celsius = properties.temperatureCelsius("temperature") ?: return null
        return CurrentConditions(
            celsius = celsius,
            condition = ConditionMapper.resolve(
                properties.optNullableString("icon"),
                properties.optNullableString("textDescription"),
            ),
            feelsLikeCelsius = properties.temperatureCelsius("heatIndex")
                ?: properties.temperatureCelsius("windChill"),
            humidityPercent = properties.measurement("relativeHumidity"),
            dewpointCelsius = properties.temperatureCelsius("dewpoint"),
            windKmh = properties.measurement("windSpeed"),
            windDirectionDegrees = properties.measurement("windDirection"),
            pressurePa = properties.measurement("barometricPressure"),
            visibilityMeters = properties.measurement("visibility"),
            stationId = properties.optNullableString("stationId"),
        )
    }

    /** Up to the next 24 hours of the hourly forecast, starting with the hour in progress. */
    fun parseHourly(body: String, now: Instant): List<HourlyForecast> =
        upcomingHourlyPeriods(body, now).take(MAX_HOURS).mapNotNull { period ->
            HourlyForecast(
                startMillis = parseInstant(period.optString("startTime"))?.toEpochMilli() ?: return@mapNotNull null,
                celsius = period.forecastCelsius() ?: return@mapNotNull null,
                condition = ConditionMapper.resolve(
                    period.optNullableString("icon"),
                    period.optNullableString("shortForecast"),
                ),
                isDaytime = period.optBoolean("isDaytime", true),
                precipitationChance = period.measurement("probabilityOfPrecipitation")?.roundToInt(),
            )
        }

    /** Current conditions built from the hourly forecast, when no station observation is available. */
    fun parseHourlyCurrent(body: String, now: Instant): CurrentConditions? {
        val period = upcomingHourlyPeriods(body, now).firstOrNull() ?: return null
        return CurrentConditions(
            celsius = period.forecastCelsius() ?: return null,
            condition = ConditionMapper.resolve(
                period.optNullableString("icon"),
                period.optNullableString("shortForecast"),
            ),
            humidityPercent = period.measurement("relativeHumidity"),
            dewpointCelsius = period.temperatureCelsius("dewpoint"),
            windKmh = parseLeadingNumber(period.optString("windSpeed"))?.let(Units::mphToKmh),
            windDirectionDegrees = Units.compassToDegrees(period.optString("windDirection")),
        )
    }

    /**
     * Daily highs/lows from the 12-hour `/forecast` periods. A day's daytime period gives the
     * high and its following night gives the low.
     */
    fun parseDaily(body: String): List<DailyForecast> {
        val periods = JSONObject(body).optJSONObject("properties")?.optJSONArray("periods") ?: return emptyList()
        val days = LinkedHashMap<String, DailyForecast>()

        for (index in 0 until periods.length()) {
            val period = periods.optJSONObject(index) ?: continue
            val start = parseOffsetDateTime(period.optString("startTime")) ?: continue
            val celsius = period.forecastCelsius() ?: continue
            val date = start.toLocalDate().toString()
            val isDaytime = period.optBoolean("isDaytime", true)
            val condition = ConditionMapper.resolve(
                period.optNullableString("icon"),
                period.optNullableString("shortForecast"),
            )
            val chance = period.measurement("probabilityOfPrecipitation")?.roundToInt()

            val existing = days[date]
            days[date] = if (existing == null) {
                DailyForecast(
                    date = date,
                    highCelsius = celsius.takeIf { isDaytime },
                    lowCelsius = celsius.takeIf { !isDaytime },
                    condition = condition,
                    precipitationChance = chance,
                )
            } else {
                existing.copy(
                    highCelsius = if (isDaytime) celsius else existing.highCelsius,
                    lowCelsius = if (isDaytime) existing.lowCelsius else celsius,
                    precipitationChance = listOfNotNull(existing.precipitationChance, chance).maxOrNull(),
                )
            }
        }
        return days.values.take(MAX_DAYS)
    }

    /** Active alerts from `/alerts/active?point=...`. */
    fun parseAlerts(body: String): List<WeatherAlert> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            val properties = features.optJSONObject(index)?.optJSONObject("properties") ?: return@mapNotNull null
            val event = properties.optNullableString("event") ?: return@mapNotNull null
            val ends = parseInstant(properties.optNullableString("ends") ?: properties.optString("expires"))
            WeatherAlert(event = event, endsMillis = ends?.toEpochMilli())
        }.distinctBy { it.event }
    }

    /** UV index from Open-Meteo `/v1/forecast?current=uv_index&daily=uv_index_max`. */
    fun parseUv(body: String): UvInfo {
        val json = JSONObject(body)
        val current = json.optJSONObject("current")?.optFiniteDouble("uv_index")
        val max = json.optJSONObject("daily")?.optJSONArray("uv_index_max")?.let { array ->
            if (array.length() == 0 || array.isNull(0)) null else array.optDouble(0).takeUnless { it.isNaN() }
        }
        return UvInfo(current, max)
    }

    /** US AQI from Open-Meteo `/v1/air-quality?current=us_aqi`. */
    fun parseUsAqi(body: String): Int? =
        JSONObject(body).optJSONObject("current")?.optFiniteDouble("us_aqi")?.roundToInt()

    private fun upcomingHourlyPeriods(body: String, now: Instant): List<JSONObject> {
        val periods = JSONObject(body).optJSONObject("properties")?.optJSONArray("periods") ?: return emptyList()
        return (0 until periods.length()).mapNotNull { index ->
            val period = periods.optJSONObject(index) ?: return@mapNotNull null
            val end = parseInstant(period.optString("endTime"))
            period.takeIf { end == null || end.isAfter(now) }
        }
    }

    private fun JSONObject.forecastCelsius(): Double? {
        if (isNull("temperature")) return null
        val value = optDouble("temperature")
        if (value.isNaN()) return null
        return if (optString("temperatureUnit", "F") == "C") value else TemperatureUtils.fahrenheitToCelsius(value)
    }

    /** `{"unitCode": "...", "value": 12.3}` → 12.3, or null. */
    private fun JSONObject.measurement(key: String): Double? {
        val measurement = optJSONObject(key) ?: return null
        return measurement.optFiniteDouble("value")
    }

    private fun JSONObject.temperatureCelsius(key: String): Double? {
        val value = measurement(key) ?: return null
        val isFahrenheit = optJSONObject(key)?.optString("unitCode").orEmpty().endsWith("degF")
        return if (isFahrenheit) TemperatureUtils.fahrenheitToCelsius(value) else value
    }

    private fun JSONObject.optFiniteDouble(key: String): Double? {
        if (isNull(key)) return null
        return optDouble(key).takeUnless { it.isNaN() || it.isInfinite() }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun parseOffsetDateTime(text: String?): OffsetDateTime? {
        if (text.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun parseInstant(text: String?): Instant? = parseOffsetDateTime(text)?.toInstant()

    private fun parseLeadingNumber(text: String): Double? =
        Regex("""\d+(\.\d+)?""").find(text)?.value?.toDoubleOrNull()
}
