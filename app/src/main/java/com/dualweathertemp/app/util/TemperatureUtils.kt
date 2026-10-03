package com.dualweathertemp.app.util

import kotlin.math.roundToInt

object TemperatureUtils {

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - 32.0) * 5.0 / 9.0

    /** Whole-degree display value, e.g. 23.6 -> "24". */
    fun format(value: Double): String = value.roundToInt().toString()
}
