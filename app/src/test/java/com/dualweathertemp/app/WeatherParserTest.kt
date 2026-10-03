package com.dualweathertemp.app

import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.data.WeatherParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WeatherParserTest {

    private val now = Instant.parse("2026-10-03T04:50:00Z")
    private val maxAge = Duration.ofHours(2)

    @Test
    fun parsePoint_readsUrlsPlaceAndTimeZone() {
        val body = """
            {"properties": {
              "observationStations": "https://api.weather.gov/gridpoints/MFL/110,50/stations",
              "forecast": "https://api.weather.gov/gridpoints/MFL/110,50/forecast",
              "forecastHourly": "https://api.weather.gov/gridpoints/MFL/110,50/forecast/hourly",
              "timeZone": "America/New_York",
              "relativeLocation": {"properties": {"city": "Miami", "state": "FL"}}
            }}
        """.trimIndent()

        val point = WeatherParser.parsePoint(body)

        assertEquals("https://api.weather.gov/gridpoints/MFL/110,50/forecast", point.forecastUrl)
        assertEquals("https://api.weather.gov/gridpoints/MFL/110,50/forecast/hourly", point.forecastHourlyUrl)
        assertEquals("Miami, FL", point.locationName)
        assertEquals("America/New_York", point.timeZone)
    }

    @Test
    fun parseStationUrls_keepsOrder() {
        val body = """
            {"features": [
              {"id": "https://api.weather.gov/stations/KMIA"},
              {"id": "https://api.weather.gov/stations/KTMB"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf("https://api.weather.gov/stations/KMIA", "https://api.weather.gov/stations/KTMB"),
            WeatherParser.parseStationUrls(body),
        )
    }

    @Test
    fun parseObservation_readsAllDetails() {
        // Trimmed copy of a real KMIA response.
        val body = """
            {"properties": {
              "stationId": "KMIA",
              "timestamp": "2026-10-03T04:40:00+00:00",
              "textDescription": "Mostly Clear",
              "icon": "https://api.weather.gov/icons/land/night/few?size=medium",
              "temperature": {"unitCode": "wmoUnit:degC", "value": 29},
              "dewpoint": {"unitCode": "wmoUnit:degC", "value": 26},
              "windDirection": {"unitCode": "wmoUnit:degree_(angle)", "value": 90},
              "windSpeed": {"unitCode": "wmoUnit:km_h-1", "value": 18.36},
              "barometricPressure": {"unitCode": "wmoUnit:Pa", "value": 101591.66},
              "visibility": {"unitCode": "wmoUnit:m", "value": 16093.44},
              "relativeHumidity": {"unitCode": "wmoUnit:percent", "value": 83.9},
              "windChill": {"unitCode": "wmoUnit:degC", "value": null},
              "heatIndex": {"unitCode": "wmoUnit:degC", "value": 35.6}
            }}
        """.trimIndent()

        val current = WeatherParser.parseObservation(body, now, maxAge)

        assertNotNull(current)
        current!!
        assertEquals(29.0, current.celsius, 1e-9)
        assertEquals(Condition.CLEAR, current.condition)
        assertEquals(35.6, current.feelsLikeCelsius!!, 1e-9)
        assertEquals(83.9, current.humidityPercent!!, 1e-9)
        assertEquals(26.0, current.dewpointCelsius!!, 1e-9)
        assertEquals(18.36, current.windKmh!!, 1e-9)
        assertEquals(90.0, current.windDirectionDegrees!!, 1e-9)
        assertEquals(101591.66, current.pressurePa!!, 1e-9)
        assertEquals(16093.44, current.visibilityMeters!!, 1e-9)
        assertEquals("KMIA", current.stationId)
    }

    @Test
    fun parseObservation_missingValuesStayNull() {
        val body = observation(timestamp = "2026-10-03T04:40:00+00:00", temperature = "20")
        val current = WeatherParser.parseObservation(body, now, maxAge)!!
        assertNull(current.windKmh)
        assertNull(current.feelsLikeCelsius)
    }

    @Test
    fun parseObservation_convertsFahrenheit() {
        val body = """
            {"properties": {"timestamp": "2026-10-03T04:40:00+00:00",
              "temperature": {"unitCode": "wmoUnit:degF", "value": 212}}}
        """.trimIndent()
        assertEquals(100.0, WeatherParser.parseObservation(body, now, maxAge)!!.celsius, 1e-9)
    }

    @Test
    fun parseObservation_nullTemperatureIsIgnored() {
        val body = observation(timestamp = "2026-10-03T04:40:00+00:00", temperature = "null")
        assertNull(WeatherParser.parseObservation(body, now, maxAge))
    }

    @Test
    fun parseObservation_staleValueIsIgnored() {
        val body = observation(timestamp = "2026-10-02T20:00:00+00:00", temperature = "20")
        assertNull(WeatherParser.parseObservation(body, now, maxAge))
    }

    @Test
    fun parseHourly_skipsFinishedHoursAndReadsRainChance() {
        val hourly = WeatherParser.parseHourly(HOURLY, now)

        assertEquals(2, hourly.size)
        assertEquals(Instant.parse("2026-10-03T04:00:00Z").toEpochMilli(), hourly[0].startMillis)
        assertEquals(27.78, hourly[0].celsius, 0.01)
        assertEquals(Condition.STORM, hourly[0].condition)
        assertEquals(40, hourly[0].precipitationChance)
        assertEquals(false, hourly[0].isDaytime)
        assertEquals(Condition.CLOUDY, hourly[1].condition)
    }

    @Test
    fun parseHourlyCurrent_usesHourInProgress() {
        val current = WeatherParser.parseHourlyCurrent(HOURLY, now)!!

        assertEquals(27.78, current.celsius, 0.01)
        assertEquals(91.0, current.humidityPercent!!, 1e-9)
        assertEquals(24.14, current.windKmh!!, 0.01)
        assertEquals(90.0, current.windDirectionDegrees!!, 1e-9)
    }

    @Test
    fun parseDaily_pairsDayHighWithNightLow() {
        val body = """
            {"properties": {"periods": [
              {"startTime": "2026-10-02T18:00:00-04:00", "isDaytime": false, "temperature": 81,
               "temperatureUnit": "F", "probabilityOfPrecipitation": {"value": 36},
               "icon": "https://api.weather.gov/icons/land/night/tsra_sct,30?size=medium"},
              {"startTime": "2026-10-03T06:00:00-04:00", "isDaytime": true, "temperature": 90,
               "temperatureUnit": "F", "probabilityOfPrecipitation": {"value": 20},
               "icon": "https://api.weather.gov/icons/land/day/sct?size=medium"},
              {"startTime": "2026-10-03T18:00:00-04:00", "isDaytime": false, "temperature": 79,
               "temperatureUnit": "F", "probabilityOfPrecipitation": {"value": 50},
               "icon": "https://api.weather.gov/icons/land/night/rain?size=medium"}
            ]}}
        """.trimIndent()

        val days = WeatherParser.parseDaily(body)

        assertEquals(2, days.size)
        assertEquals("2026-10-02", days[0].date)
        assertNull(days[0].highCelsius)
        assertEquals(27.22, days[0].lowCelsius!!, 0.01)

        assertEquals("2026-10-03", days[1].date)
        assertEquals(32.22, days[1].highCelsius!!, 0.01)
        assertEquals(26.11, days[1].lowCelsius!!, 0.01)
        assertEquals(Condition.PARTLY_CLOUDY, days[1].condition)
        assertEquals(50, days[1].precipitationChance)
    }

    @Test
    fun parseAlerts_usesEndsOrExpires() {
        val body = """
            {"features": [
              {"properties": {"event": "Rip Current Statement",
                "ends": "2026-10-03T20:00:00-04:00", "expires": "2026-10-03T06:00:00-04:00"}},
              {"properties": {"event": "Heat Advisory", "ends": null,
                "expires": "2026-10-03T19:00:00-04:00"}}
            ]}
        """.trimIndent()

        val alerts = WeatherParser.parseAlerts(body)

        assertEquals(listOf("Rip Current Statement", "Heat Advisory"), alerts.map { it.event })
        assertEquals(Instant.parse("2026-10-04T00:00:00Z").toEpochMilli(), alerts[0].endsMillis)
        assertEquals(Instant.parse("2026-10-03T23:00:00Z").toEpochMilli(), alerts[1].endsMillis)
    }

    @Test
    fun parseAlerts_noAlerts() {
        assertTrue(WeatherParser.parseAlerts("""{"features": []}""").isEmpty())
    }

    @Test
    fun parseOpenMeteo_uvAndAirQuality() {
        val uv = WeatherParser.parseUv(
            """{"current": {"uv_index": 0.00}, "daily": {"uv_index_max": [8.35]}}"""
        )
        assertEquals(0.0, uv.current!!, 1e-9)
        assertEquals(8.35, uv.todayMax!!, 1e-9)

        assertEquals(25, WeatherParser.parseUsAqi("""{"current": {"time": "2026-10-03T05:00", "us_aqi": 25}}"""))
        assertNull(WeatherParser.parseUsAqi("""{"current": {"us_aqi": null}}"""))
    }

    private fun observation(timestamp: String, temperature: String) = """
        {"properties": {
          "timestamp": "$timestamp",
          "temperature": {"unitCode": "wmoUnit:degC", "value": $temperature}
        }}
    """.trimIndent()

    private companion object {
        // First hour already ended at `now`; the second is in progress.
        val HOURLY = """
            {"properties": {"periods": [
              {"startTime": "2026-10-03T00:00:00-04:00", "endTime": "2026-10-03T00:30:00-04:00",
               "isDaytime": false, "temperature": 83, "temperatureUnit": "F",
               "icon": "https://api.weather.gov/icons/land/night/few?size=small"},
              {"startTime": "2026-10-03T00:00:00-04:00", "endTime": "2026-10-03T01:00:00-04:00",
               "isDaytime": false, "temperature": 82, "temperatureUnit": "F",
               "probabilityOfPrecipitation": {"unitCode": "wmoUnit:percent", "value": 40},
               "relativeHumidity": {"unitCode": "wmoUnit:percent", "value": 91},
               "dewpoint": {"unitCode": "wmoUnit:degC", "value": 26.1},
               "windSpeed": "15 mph", "windDirection": "E",
               "icon": "https://api.weather.gov/icons/land/night/tsra_sct,40?size=small",
               "shortForecast": "Chance Showers And Thunderstorms"},
              {"startTime": "2026-10-03T01:00:00-04:00", "endTime": "2026-10-03T02:00:00-04:00",
               "isDaytime": false, "temperature": 81, "temperatureUnit": "F",
               "icon": "https://api.weather.gov/icons/land/night/bkn?size=small"}
            ]}}
        """.trimIndent()
    }
}
