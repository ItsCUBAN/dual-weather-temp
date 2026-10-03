package com.dualweathertemp.app

import com.dualweathertemp.app.util.TemperatureUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureUtilsTest {

    @Test
    fun celsiusToFahrenheit_knownPoints() {
        assertEquals(32.0, TemperatureUtils.celsiusToFahrenheit(0.0), 1e-9)
        assertEquals(212.0, TemperatureUtils.celsiusToFahrenheit(100.0), 1e-9)
        assertEquals(-40.0, TemperatureUtils.celsiusToFahrenheit(-40.0), 1e-9)
    }

    @Test
    fun fahrenheitToCelsius_knownPoints() {
        assertEquals(37.0, TemperatureUtils.fahrenheitToCelsius(98.6), 1e-9)
        assertEquals(0.0, TemperatureUtils.fahrenheitToCelsius(32.0), 1e-9)
    }

    @Test
    fun conversionsRoundTrip() {
        assertEquals(23.4, TemperatureUtils.fahrenheitToCelsius(TemperatureUtils.celsiusToFahrenheit(23.4)), 1e-9)
    }

    @Test
    fun format_roundsToWholeDegrees() {
        assertEquals("24", TemperatureUtils.format(23.6))
        assertEquals("23", TemperatureUtils.format(23.4))
        assertEquals("-3", TemperatureUtils.format(-2.6))
    }
}
