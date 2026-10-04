package com.dualweathertemp.app

import com.dualweathertemp.app.data.BackgroundStyle
import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.data.UnitOrder
import com.dualweathertemp.app.data.UsStates
import com.dualweathertemp.app.data.WeatherParser
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.sky.SkyTheme
import com.dualweathertemp.app.ui.WeatherText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesAndUnitsTest {

    @Test
    fun parsePlaces_keepsUsCitiesWithState() {
        // Trimmed copy of a real Open-Meteo geocoding response.
        val body = """
            {"results": [
              {"id": 4297983, "name": "Lexington", "latitude": 37.98869, "longitude": -84.47772,
               "country_code": "US", "admin1": "Kentucky"},
              {"id": 4297999, "name": "Lexington", "latitude": 38.0498, "longitude": -84.45855,
               "country_code": "US", "admin1": "Kentucky"},
              {"id": 4635031, "name": "Lexington", "latitude": 35.82, "longitude": -88.39,
               "country_code": "US", "admin1": "Tennessee"},
              {"id": 1, "name": "Lexington", "latitude": 1.0, "longitude": 1.0, "country_code": "GB", "admin1": "England"},
              {"id": 2, "name": "Nowhere", "latitude": 1.0, "longitude": 1.0, "country_code": "US"}
            ]}
        """.trimIndent()

        val places = WeatherParser.parsePlaces(body)

        assertEquals(listOf("Lexington, Kentucky", "Lexington, Tennessee"), places.map { it.longLabel })
        assertEquals("4297983", places[0].id)
        assertEquals("Lexington, KY", places[0].shortLabel)
        assertEquals(37.98869, places[0].latitude, 1e-9)
    }

    @Test
    fun parsePlaces_noResults() {
        assertTrue(WeatherParser.parsePlaces("""{"generationtime_ms": 0.4}""").isEmpty())
    }

    @Test
    fun stateAbbreviations() {
        assertEquals("KY", UsStates.abbreviation("Kentucky"))
        assertEquals("DC", UsStates.abbreviation("District of Columbia"))
        assertEquals("Puerto Rico", UsStates.abbreviation("Puerto Rico"))
    }

    @Test
    fun unitOrder_appliesToEveryFormat() {
        val celsius = 22.8 // 73°F
        assertEquals("73°F / 23°C", WeatherText.dualTemp(celsius, UnitOrder.FAHRENHEIT_FIRST))
        assertEquals("23°C / 73°F", WeatherText.dualTemp(celsius, UnitOrder.CELSIUS_FIRST))
        assertEquals("77° / 53°F · 25° / 12°C", WeatherText.dualRange(25.0, 11.7, UnitOrder.FAHRENHEIT_FIRST))
        assertEquals("25° / 12°C · 77° / 53°F", WeatherText.dualRange(25.0, 11.7, UnitOrder.CELSIUS_FIRST))
        assertEquals(
            listOf(WeatherText.TempUnit.CELSIUS, WeatherText.TempUnit.FAHRENHEIT),
            WeatherText.units(UnitOrder.CELSIUS_FIRST),
        )
    }

    @Test
    fun sunnyOnlyInFullDaylight() {
        assertEquals(R.string.condition_clear_day, WeatherText.conditionRes(Condition.CLEAR, DayPhase.DAY))
        assertEquals(R.string.condition_clear_night, WeatherText.conditionRes(Condition.CLEAR, DayPhase.TWILIGHT))
        assertEquals(R.string.condition_clear_night, WeatherText.conditionRes(Condition.CLEAR, DayPhase.NIGHT))
        assertEquals("🌇", WeatherText.conditionSymbol(Condition.CLEAR, DayPhase.TWILIGHT))
        assertEquals("🌧️", WeatherText.conditionSymbol(Condition.RAIN, DayPhase.TWILIGHT))
    }

    @Test
    fun backgroundStyle_overridesSky() {
        assertEquals(SkyTheme.CLEAR_DAY, SkyTheme.resolve(BackgroundStyle.SKY, report = null, nowMillis = 0))
        assertEquals(SkyTheme.LIGHT, SkyTheme.resolve(BackgroundStyle.LIGHT, report = null, nowMillis = 0))
        assertEquals(SkyTheme.DARK, SkyTheme.resolve(BackgroundStyle.DARK, report = null, nowMillis = 0))
        assertTrue(SkyTheme.LIGHT.isLight)
        assertFalse(SkyTheme.DARK.isLight)
        assertFalse(SkyTheme.CLEAR_DAY.isLight)
    }
}
