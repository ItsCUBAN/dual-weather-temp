package com.dualweathertemp.app

import com.dualweathertemp.app.astro.SunTimes
import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.sky.SkyTheme
import com.dualweathertemp.app.util.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkyThemeTest {

    private val hour = 3_600_000L
    private val sun = SunTimes(sunriseMillis = 7 * hour, sunsetMillis = 19 * hour)

    @Test
    fun dayPhase_aroundSunriseAndSunset() {
        assertEquals(DayPhase.NIGHT, DayPhase.at(3 * hour, sun, localHour = 3))
        assertEquals(DayPhase.TWILIGHT, DayPhase.at(7 * hour + 30 * 60_000L, sun, localHour = 7))
        assertEquals(DayPhase.DAY, DayPhase.at(12 * hour, sun, localHour = 12))
        assertEquals(DayPhase.TWILIGHT, DayPhase.at(19 * hour - 20 * 60_000L, sun, localHour = 18))
        assertEquals(DayPhase.NIGHT, DayPhase.at(21 * hour, sun, localHour = 21))
    }

    @Test
    fun dayPhase_withoutSunTimesUsesClock() {
        val none = SunTimes(null, null)
        assertEquals(DayPhase.DAY, DayPhase.at(0, none, localHour = 12))
        assertEquals(DayPhase.NIGHT, DayPhase.at(0, none, localHour = 23))
    }

    @Test
    fun skyTheme_weatherOverridesTimeOfDay() {
        assertEquals(SkyTheme.RAIN, SkyTheme.from(Condition.RAIN, DayPhase.NIGHT))
        assertEquals(SkyTheme.STORM, SkyTheme.from(Condition.STORM, DayPhase.DAY))
        assertEquals(SkyTheme.CLOUDY_NIGHT, SkyTheme.from(Condition.CLOUDY, DayPhase.NIGHT))
        assertEquals(SkyTheme.CLOUDY_DAY, SkyTheme.from(Condition.CLOUDY, DayPhase.TWILIGHT))
    }

    @Test
    fun skyTheme_clearSkyFollowsTimeOfDay() {
        assertEquals(SkyTheme.CLEAR_DAY, SkyTheme.from(Condition.CLEAR, DayPhase.DAY))
        assertEquals(SkyTheme.PARTLY_CLOUDY_DAY, SkyTheme.from(Condition.PARTLY_CLOUDY, DayPhase.DAY))
        assertEquals(SkyTheme.TWILIGHT, SkyTheme.from(Condition.CLEAR, DayPhase.TWILIGHT))
        assertEquals(SkyTheme.CLEAR_NIGHT, SkyTheme.from(Condition.PARTLY_CLOUDY, DayPhase.NIGHT))
    }

    @Test
    fun units_conversions() {
        assertEquals(11.2, Units.kmhToMph(18.0), 0.05)
        assertEquals(30.0, Units.pascalsToInHg(101591.66), 0.01)
        assertEquals(1015.92, Units.pascalsToHpa(101591.66), 0.01)
        assertEquals(10.0, Units.metersToMiles(16093.44), 1e-9)
        assertEquals(2, Units.compassIndex(90.0))
        assertEquals(0, Units.compassIndex(350.0))
        assertEquals(7, Units.compassIndex(-45.0))
        assertEquals(22.5, Units.compassToDegrees("NNE")!!, 1e-9)
        assertNull(Units.compassToDegrees("calm"))
    }
}
