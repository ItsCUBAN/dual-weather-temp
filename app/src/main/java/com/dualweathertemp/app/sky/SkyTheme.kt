package com.dualweathertemp.app.sky

import com.dualweathertemp.app.astro.SunCalculator
import com.dualweathertemp.app.astro.SunTimes
import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.data.WeatherReport
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.abs

enum class DayPhase {
    DAY, TWILIGHT, NIGHT;

    companion object {
        private const val TWILIGHT_WINDOW_MS = 40 * 60 * 1000L

        /** Within 40 minutes of sunrise or sunset counts as twilight. */
        fun at(nowMillis: Long, sun: SunTimes, localHour: Int): DayPhase {
            val sunrise = sun.sunriseMillis
            val sunset = sun.sunsetMillis
            if (sunrise == null || sunset == null) return if (localHour in 7..18) DAY else NIGHT
            if (abs(nowMillis - sunrise) <= TWILIGHT_WINDOW_MS || abs(nowMillis - sunset) <= TWILIGHT_WINDOW_MS) {
                return TWILIGHT
            }
            return if (nowMillis in sunrise..sunset) DAY else NIGHT
        }
    }
}

/** Background gradient (top to bottom, ARGB) for each kind of sky. */
enum class SkyTheme(val topColor: Long, val bottomColor: Long) {
    CLEAR_DAY(0xFF2F80ED, 0xFF56CCF2),
    PARTLY_CLOUDY_DAY(0xFF3A7BD5, 0xFF8CB8DE),
    CLOUDY_DAY(0xFF5F6B7A, 0xFF9AA5B1),
    CLOUDY_NIGHT(0xFF2B3440, 0xFF4A5562),
    RAIN(0xFF2C3E50, 0xFF4C6A85),
    STORM(0xFF2E2A4F, 0xFF4B4E6D),
    SNOW(0xFF6B8BA4, 0xFFA9BCCD),
    FOG(0xFF6B7489, 0xFFA0A7B6),
    TWILIGHT(0xFF5B3A8C, 0xFFF2994A),
    CLEAR_NIGHT(0xFF0B1A3A, 0xFF24365E);

    companion object {

        fun from(condition: Condition, phase: DayPhase): SkyTheme = when (condition) {
            Condition.RAIN -> RAIN
            Condition.STORM -> STORM
            Condition.SNOW -> SNOW
            Condition.FOG -> FOG
            Condition.CLOUDY -> if (phase == DayPhase.NIGHT) CLOUDY_NIGHT else CLOUDY_DAY
            Condition.CLEAR, Condition.PARTLY_CLOUDY -> when (phase) {
                DayPhase.TWILIGHT -> TWILIGHT
                DayPhase.NIGHT -> CLEAR_NIGHT
                DayPhase.DAY -> if (condition == Condition.CLEAR) CLEAR_DAY else PARTLY_CLOUDY_DAY
            }
        }
    }
}

/** Sun times for today at the report's location. */
fun WeatherReport.sunTimes(nowMillis: Long): SunTimes {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return SunCalculator.sunTimes(today, latitude, longitude)
}

fun WeatherReport.dayPhase(nowMillis: Long): DayPhase {
    val localHour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).hour
    return DayPhase.at(nowMillis, sunTimes(nowMillis), localHour)
}

fun WeatherReport.skyTheme(nowMillis: Long): SkyTheme = SkyTheme.from(current.condition, dayPhase(nowMillis))
