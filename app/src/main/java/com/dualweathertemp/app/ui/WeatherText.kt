package com.dualweathertemp.app.ui

import android.content.Context
import androidx.annotation.StringRes
import com.dualweathertemp.app.R
import com.dualweathertemp.app.astro.MoonPhase
import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.data.UnitOrder
import com.dualweathertemp.app.data.WeatherException
import com.dualweathertemp.app.sky.DayPhase
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

    enum class TempUnit(val symbol: String) { FAHRENHEIT("F"), CELSIUS("C") }

    /** Both units in the order the user chose in Settings. */
    fun units(order: UnitOrder): List<TempUnit> = when (order) {
        UnitOrder.FAHRENHEIT_FIRST -> listOf(TempUnit.FAHRENHEIT, TempUnit.CELSIUS)
        UnitOrder.CELSIUS_FIRST -> listOf(TempUnit.CELSIUS, TempUnit.FAHRENHEIT)
    }

    fun fahrenheit(celsius: Double): String =
        TemperatureUtils.format(TemperatureUtils.celsiusToFahrenheit(celsius))

    fun celsius(celsius: Double): String = TemperatureUtils.format(celsius)

    /** Whole number in [unit]: "73" */
    fun number(celsius: Double, unit: TempUnit): String = when (unit) {
        TempUnit.FAHRENHEIT -> fahrenheit(celsius)
        TempUnit.CELSIUS -> celsius(celsius)
    }

    /** "73°F" */
    fun temp(celsius: Double, unit: TempUnit): String = "${number(celsius, unit)}°${unit.symbol}"

    /** "73°F / 23°C", or "23°C / 73°F" when Celsius comes first. */
    fun dualTemp(celsius: Double, order: UnitOrder): String =
        units(order).joinToString(" / ") { temp(celsius, it) }

    /** "87° / 81°F · 31° / 27°C" for a high and a low. */
    fun dualRange(highCelsius: Double, lowCelsius: Double, order: UnitOrder): String =
        units(order).joinToString(" · ") { unit ->
            "${number(highCelsius, unit)}° / ${number(lowCelsius, unit)}°${unit.symbol}"
        }

    /** "Sunny" only in full daylight; "Clear" at twilight and at night. */
    @StringRes
    fun conditionRes(condition: Condition, phase: DayPhase): Int = conditionRes(condition, phase == DayPhase.DAY)

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

    /** Like the other overload, with a sunset symbol for clear skies at twilight. */
    fun conditionSymbol(condition: Condition, phase: DayPhase): String {
        val clearish = condition == Condition.CLEAR || condition == Condition.PARTLY_CLOUDY
        return if (clearish && phase == DayPhase.TWILIGHT) "🌇" else conditionSymbol(condition, phase == DayPhase.DAY)
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

    /**
     * End time of an alert: "7:15 PM" today, "tomorrow 5:30 AM", or "Mon 5:30 AM" further out,
     * so a multi-day alert isn't mistaken for one ending today.
     */
    fun formatUntil(context: Context, millis: Long, zone: ZoneId, nowMillis: Long = System.currentTimeMillis()): String {
        val time = formatTime(context, millis, zone)
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (day) {
            today -> time
            today.plusDays(1) -> context.getString(R.string.tomorrow_at, time)
            else -> "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)} $time"
        }
    }

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
