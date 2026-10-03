package com.dualweathertemp.app.astro

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Sunrise and sunset as epoch millis; null during polar day or night. */
data class SunTimes(val sunriseMillis: Long?, val sunsetMillis: Long?)

/**
 * Sunrise/sunset from the standard sunrise equation (accurate to a few minutes),
 * computed on the phone so it needs no network.
 */
object SunCalculator {

    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JULIAN_DAY = 2440587.5
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val EARTH_TILT = 23.4397
    private const val SUN_ALTITUDE_AT_HORIZON = -0.833

    /** @param date the local calendar date at the location. Longitude is negative west of Greenwich. */
    fun sunTimes(date: LocalDate, latitude: Double, longitude: Double): SunTimes {
        val daysSinceJ2000 = date.toEpochDay() - 10957.0
        val meanSolarTime = daysSinceJ2000 - longitude / 360.0

        val anomaly = normalizeDegrees(357.5291 + 0.98560028 * meanSolarTime)
        val anomalyRad = Math.toRadians(anomaly)
        val center = 1.9148 * sin(anomalyRad) + 0.02 * sin(2 * anomalyRad) + 0.0003 * sin(3 * anomalyRad)
        val eclipticLongitude = Math.toRadians(normalizeDegrees(anomaly + center + 180.0 + 102.9372))

        val solarTransit = J2000 + meanSolarTime + 0.0053 * sin(anomalyRad) - 0.0069 * sin(2 * eclipticLongitude)
        val sinDeclination = sin(eclipticLongitude) * sin(Math.toRadians(EARTH_TILT))
        val cosDeclination = cos(asin(sinDeclination))
        val latitudeRad = Math.toRadians(latitude)

        val cosHourAngle = (sin(Math.toRadians(SUN_ALTITUDE_AT_HORIZON)) - sin(latitudeRad) * sinDeclination) /
            (cos(latitudeRad) * cosDeclination)
        if (cosHourAngle < -1 || cosHourAngle > 1) return SunTimes(null, null)

        val halfDay = Math.toDegrees(acos(cosHourAngle)) / 360.0
        return SunTimes(
            sunriseMillis = julianToMillis(solarTransit - halfDay),
            sunsetMillis = julianToMillis(solarTransit + halfDay),
        )
    }

    private fun julianToMillis(julianDay: Double): Long =
        ((julianDay - UNIX_EPOCH_JULIAN_DAY) * MILLIS_PER_DAY).roundToLong()

    private fun normalizeDegrees(degrees: Double): Double = (degrees % 360 + 360) % 360
}
