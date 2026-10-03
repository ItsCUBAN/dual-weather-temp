package com.dualweathertemp.app

import com.dualweathertemp.app.astro.MoonCalculator
import com.dualweathertemp.app.astro.MoonPhase
import com.dualweathertemp.app.astro.SunCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class AstroTest {

    private val toleranceMillis = 5 * 60 * 1000L

    @Test
    fun sunTimes_miami() {
        // Reference: Open-Meteo, 2026-10-03 (11:13 / 23:05 UTC).
        val sun = SunCalculator.sunTimes(LocalDate.of(2026, 10, 3), 25.7617, -80.1918)
        assertClose(Instant.parse("2026-10-03T11:13:00Z"), sun.sunriseMillis!!)
        assertClose(Instant.parse("2026-10-03T23:05:00Z"), sun.sunsetMillis!!)
    }

    @Test
    fun sunTimes_seattle() {
        // Reference: Open-Meteo, 2026-10-03 (14:11 / 01:44 next day UTC).
        val sun = SunCalculator.sunTimes(LocalDate.of(2026, 10, 3), 47.6062, -122.3321)
        assertClose(Instant.parse("2026-10-03T14:11:00Z"), sun.sunriseMillis!!)
        assertClose(Instant.parse("2026-10-04T01:44:00Z"), sun.sunsetMillis!!)
    }

    @Test
    fun sunTimes_polarNight() {
        val sun = SunCalculator.sunTimes(LocalDate.of(2026, 12, 21), 71.29, -156.79)
        assertNull(sun.sunriseMillis)
        assertNull(sun.sunsetMillis)
    }

    @Test
    fun moon_phasesAcrossOneCycle() {
        val newMoon = 947_182_440_000L
        val day = 86_400_000L

        MoonCalculator.at(newMoon).let {
            assertEquals(MoonPhase.NEW, it.phase)
            assertEquals(15, it.daysUntilFull)
        }
        assertEquals(MoonPhase.FIRST_QUARTER, MoonCalculator.at(newMoon + 7 * day + day / 2).phase)
        MoonCalculator.at(newMoon + 14 * day + 18 * 3_600_000L).let {
            assertEquals(MoonPhase.FULL, it.phase)
            assertEquals(0, it.daysUntilFull)
        }
        assertEquals(MoonPhase.LAST_QUARTER, MoonCalculator.at(newMoon + 22 * day).phase)
        // Same phase one synodic month later.
        assertEquals(MoonPhase.NEW, MoonCalculator.at(newMoon + 29 * day + 12 * 3_600_000L + 44 * 60_000L).phase)
    }

    private fun assertClose(expected: Instant, actualMillis: Long) {
        val diff = abs(expected.toEpochMilli() - actualMillis)
        assertTrue("off by ${diff / 60000} min", diff <= toleranceMillis)
    }
}
