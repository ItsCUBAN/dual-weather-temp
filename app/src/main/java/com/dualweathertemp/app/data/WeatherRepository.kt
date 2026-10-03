package com.dualweathertemp.app.data

import com.dualweathertemp.app.data.WeatherException.Reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Locale

class WeatherRepository(
    private val api: WeatherApi = WeatherApi(),
    private val now: () -> Instant = Instant::now,
) {

    /**
     * Full weather report for the given coordinates. Only the current temperature is required;
     * forecasts, alerts, UV and air quality are left empty if their source fails.
     *
     * @throws WeatherException with the reason the temperature couldn't be loaded.
     */
    suspend fun fetch(latitude: Double, longitude: Double): WeatherReport {
        try {
            return coroutineScope {
                val point = loadPoint(latitude, longitude)
                val instant = now()

                val observation = async { attempt { latestObservation(point.observationStationsUrl, instant) } }
                val hourlyBody = async { attempt { api.get(point.forecastHourlyUrl) } }
                val daily = async { attempt { WeatherParser.parseDaily(api.get(point.forecastUrl)) } }
                val alerts = async { attempt { WeatherParser.parseAlerts(api.get(alertsUrl(latitude, longitude))) } }
                val uv = async { attempt { WeatherParser.parseUv(api.get(uvUrl(latitude, longitude))) } }
                val aqi = async { attempt { WeatherParser.parseUsAqi(api.get(airQualityUrl(latitude, longitude))) } }

                val observed = observation.await().getOrNull()
                val current = observed
                    // No usable station reading: fall back to the hourly forecast, surfacing its error.
                    ?: WeatherParser.parseHourlyCurrent(hourlyBody.await().getOrThrow(), instant)
                    ?: throw WeatherException(Reason.NO_DATA)

                val hourly = hourlyBody.await().getOrNull()
                    ?.let { body -> attempt { WeatherParser.parseHourly(body, instant) }.getOrNull() }
                    .orEmpty()
                val uvInfo = uv.await().getOrNull()

                WeatherReport(
                    locationName = point.locationName,
                    latitude = latitude,
                    longitude = longitude,
                    timeZone = point.timeZone,
                    current = current,
                    source = if (observed != null) WeatherReport.Source.OBSERVATION else WeatherReport.Source.FORECAST,
                    hourly = hourly,
                    daily = daily.await().getOrNull().orEmpty(),
                    alerts = alerts.await().getOrNull().orEmpty(),
                    uvIndex = uvInfo?.current,
                    uvIndexMax = uvInfo?.todayMax,
                    usAqi = aqi.await().getOrNull(),
                    timestampMillis = instant.toEpochMilli(),
                )
            }
        } catch (e: WeatherException) {
            throw e
        } catch (e: HttpStatusException) {
            // weather.gov returns 5xx fairly often; those are worth retrying.
            throw WeatherException(if (e.code >= 500) Reason.NETWORK else Reason.NO_DATA, e)
        } catch (e: IOException) {
            throw WeatherException(Reason.NETWORK, e)
        } catch (e: JSONException) {
            throw WeatherException(Reason.NO_DATA, e)
        }
    }

    private suspend fun loadPoint(latitude: Double, longitude: Double): PointInfo {
        val url = String.format(Locale.US, "%s/points/%s", WeatherApi.BASE_URL, coordinates(latitude, longitude))
        val body = try {
            api.get(url)
        } catch (e: HttpStatusException) {
            if (e.code == 404) throw WeatherException(Reason.OUTSIDE_US, e)
            throw e
        }
        return WeatherParser.parsePoint(body)
    }

    private suspend fun latestObservation(stationsUrl: String, instant: Instant): CurrentConditions? {
        val stations = WeatherParser.parseStationUrls(api.get(stationsUrl)).take(MAX_STATIONS)
        for (station in stations) {
            val body = try {
                api.get("$station/observations/latest")
            } catch (e: HttpStatusException) {
                continue
            }
            WeatherParser.parseObservation(body, instant, MAX_OBSERVATION_AGE)?.let { return it }
        }
        return null
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val MAX_STATIONS = 3
        val MAX_OBSERVATION_AGE: Duration = Duration.ofHours(2)

        // weather.gov accepts at most 4 decimal places.
        fun coordinates(latitude: Double, longitude: Double) =
            String.format(Locale.US, "%.4f,%.4f", latitude, longitude)

        fun alertsUrl(latitude: Double, longitude: Double) =
            "${WeatherApi.BASE_URL}/alerts/active?point=${coordinates(latitude, longitude)}"

        fun uvUrl(latitude: Double, longitude: Double) = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=uv_index&daily=uv_index_max&timezone=auto&forecast_days=1",
            latitude, longitude,
        )

        fun airQualityUrl(latitude: Double, longitude: Double) = String.format(
            Locale.US,
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=us_aqi",
            latitude, longitude,
        )
    }
}
