package com.dualweathertemp.app.ui

import android.content.Context
import androidx.annotation.StringRes
import com.dualweathertemp.app.R
import com.dualweathertemp.app.astro.MoonPhase
import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.data.WeatherException
import com.dualweathertemp.app.util.TemperatureUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Text and formatting shared by the app screen and the widgets. */
object WeatherText {

    enum class AlertLevel { WARNING, WATCH, ADVISORY }

    fun fahrenheit(celsius: Double): String =
        TemperatureUtils.format(TemperatureUtils.celsiusToFahrenheit(celsius))

    fun celsius(celsius: Double): String = TemperatureUtils.format(celsius)

    /** "86°F / 30°C" */
    fun dualTemp(context: Context, celsius: Double): String =
        context.getString(R.string.dual_temp, fahrenheit(celsius), celsius(celsius))

    @StringRes
    fun conditionRes(condition: Condition, isDay: Boolean): Int = when (condition) {
        Condition.CLEAR -> if (isDay) R.string.condition_clear_day else R.string.condition_clear_night
        Condition.PARTLY_CLOUDY -> R.string.condition_partly_cloudy
        Condition.CLOUDY -> R.string.condition_cloudy
        Condition.RAIN -> R.string.condition_rain
        Condition.STORM -> R.string.condition_storm
        Condition.SNOW -> R.string.condition_snow
        Condition.FOG -> R.string.condition_fog
    }

    /** Small symbol for the widgets, which can't show the app's vector icons. */
    fun conditionSymbol(condition: Condition, isDay: Boolean): String = when (condition) {
        Condition.CLEAR -> if (isDay) "☀️" else "🌙"
        Condition.PARTLY_CLOUDY -> if (isDay) "⛅" else "☁️"
        Condition.CLOUDY -> "☁️"
        Condition.RAIN -> "🌧️"
        Condition.STORM -> "⛈️"
        Condition.SNOW -> "❄️"
        Condition.FOG -> "🌫️"
    }

    fun alertLevel(event: String): AlertLevel {
        val name = event.lowercase()
        return when {
            "warning" in name -> AlertLevel.WARNING
            "watch" in name -> AlertLevel.WATCH
            else -> AlertLevel.ADVISORY
        }
    }

    @StringRes
    fun uvLevelRes(uv: Double): Int = when {
        uv < 3 -> R.string.uv_low
        uv < 6 -> R.string.uv_moderate
        uv < 8 -> R.string.uv_high
        uv < 11 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    }

    @StringRes
    fun aqiLevelRes(aqi: Int): Int = when {
        aqi <= 50 -> R.string.aqi_good
        aqi <= 100 -> R.string.aqi_moderate
        aqi <= 150 -> R.string.aqi_sensitive
        aqi <= 200 -> R.string.aqi_unhealthy
        aqi <= 300 -> R.string.aqi_very_unhealthy
        else -> R.string.aqi_hazardous
    }

    @StringRes
    fun moonRes(phase: MoonPhase): Int = when (phase) {
        MoonPhase.NEW -> R.string.moon_new
        MoonPhase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
        MoonPhase.FIRST_QUARTER -> R.string.moon_first_quarter
        MoonPhase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
        MoonPhase.FULL -> R.string.moon_full
        MoonPhase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
        MoonPhase.LAST_QUARTER -> R.string.moon_last_quarter
        MoonPhase.WANING_CRESCENT -> R.string.moon_waning_crescent
    }

    @StringRes
    fun errorRes(reason: WeatherException.Reason): Int = when (reason) {
        WeatherException.Reason.NO_LOCATION -> R.string.error_no_location
        WeatherException.Reason.OUTSIDE_US -> R.string.error_outside_us
        WeatherException.Reason.NETWORK -> R.string.error_network
        WeatherException.Reason.NO_DATA -> R.string.error_no_data
    }

    /** "7:12 AM", or "19:12" when the phone uses 24-hour time. */
    fun formatTime(context: Context, millis: Long, zone: ZoneId): String =
        format(millis, zone, if (is24Hour(context)) "HH:mm" else "h:mm a")

    /** "4 PM", or "16:00" when the phone uses 24-hour time. */
    fun formatHour(context: Context, millis: Long, zone: ZoneId): String =
        format(millis, zone, if (is24Hour(context)) "HH:mm" else "h a")

    /** "Today" or a short weekday such as "Sat". */
    fun dayLabel(context: Context, isoDate: String, today: LocalDate): String {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        if (date == today) return context.getString(R.string.today)
        return date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    }

    private fun is24Hour(context: Context) = android.text.format.DateFormat.is24HourFormat(context)

    private fun format(millis: Long, zone: ZoneId, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(millis).atZone(zone))
}
