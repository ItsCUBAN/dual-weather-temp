package com.dualweathertemp.app.util

object Units {

    fun kmhToMph(kmh: Double): Double = kmh / 1.609344

    fun mphToKmh(mph: Double): Double = mph * 1.609344

    fun pascalsToInHg(pa: Double): Double = pa / 3386.389

    fun pascalsToHpa(pa: Double): Double = pa / 100.0

    fun metersToMiles(meters: Double): Double = meters / 1609.344

    fun metersToKm(meters: Double): Double = meters / 1000.0

    /** 0..7 for N, NE, E, SE, S, SW, W, NW. */
    fun compassIndex(degrees: Double): Int = (((degrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8

    /** "E" → 90.0, "NNE" → 22.5; null when unrecognized. */
    fun compassToDegrees(direction: String?): Double? {
        val sixteen = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        val index = sixteen.indexOf(direction?.trim()?.uppercase())
        return if (index < 0) null else index * 22.5
    }
}
