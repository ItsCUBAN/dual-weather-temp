package com.dualweathertemp.app.astro

import kotlin.math.roundToInt

enum class MoonPhase {
    NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
    FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
}

data class MoonInfo(val phase: MoonPhase, val ageDays: Double, val daysUntilFull: Int)

/** Moon phase from the mean synodic month, counted from a known new moon. */
object MoonCalculator {

    private const val SYNODIC_MONTH_DAYS = 29.530588853
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** New moon of 2000-01-06 18:14 UTC. */
    internal const val REFERENCE_NEW_MOON_MILLIS = 947_182_440_000L

    fun at(epochMillis: Long): MoonInfo {
        val days = (epochMillis - REFERENCE_NEW_MOON_MILLIS) / MILLIS_PER_DAY
        val age = (days % SYNODIC_MONTH_DAYS + SYNODIC_MONTH_DAYS) % SYNODIC_MONTH_DAYS
        val phaseIndex = ((age / SYNODIC_MONTH_DAYS) * 8 + 0.5).toInt() % 8
        val untilFull = (SYNODIC_MONTH_DAYS / 2 - age + SYNODIC_MONTH_DAYS) % SYNODIC_MONTH_DAYS
        return MoonInfo(
            phase = MoonPhase.entries[phaseIndex],
            ageDays = age,
            daysUntilFull = untilFull.roundToInt(),
        )
    }
}
