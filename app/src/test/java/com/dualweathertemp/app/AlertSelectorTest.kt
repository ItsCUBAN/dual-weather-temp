package com.dualweathertemp.app

import com.dualweathertemp.app.data.AlertSelector
import com.dualweathertemp.app.data.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSelectorTest {

    private val tornado = WeatherAlert("Tornado Warning", key = "Tornado Warning|1")
    private val stormWatch = WeatherAlert("Severe Thunderstorm Watch", key = "Severe Thunderstorm Watch|2")
    private val fog = WeatherAlert("Dense Fog Advisory", key = "Dense Fog Advisory|3")

    @Test
    fun importance_warningsWatchesAndEmergencies() {
        assertTrue(AlertSelector.isImportant("Tornado Warning"))
        assertTrue(AlertSelector.isImportant("Flash Flood Watch"))
        assertTrue(AlertSelector.isImportant("Winter Storm Warning"))
        assertTrue(AlertSelector.isImportant("Tornado Emergency"))
        assertFalse(AlertSelector.isImportant("Dense Fog Advisory"))
        assertFalse(AlertSelector.isImportant("Frost Advisory"))
        assertFalse(AlertSelector.isImportant("Special Weather Statement"))
        assertFalse(AlertSelector.isImportant("Hazardous Weather Outlook"))
    }

    @Test
    fun toNotify_skipsMinorAndAlreadyNotified() {
        val alerts = listOf(tornado, stormWatch, fog)
        assertEquals(listOf(tornado, stormWatch), AlertSelector.toNotify(alerts, emptySet()))
        assertEquals(listOf(stormWatch), AlertSelector.toNotify(alerts, setOf(tornado.key)))
    }

    @Test
    fun toNotify_updatedAlertKeepsItsKeySoItIsNotRepeated() {
        // NWS extends the warning: new id and end time, same event and onset.
        val extended = tornado.copy(endsMillis = 999L)
        assertTrue(AlertSelector.toNotify(listOf(extended), setOf(tornado.key)).isEmpty())
    }

    @Test
    fun toNotify_ignoresAlertsWithoutKey() {
        assertTrue(AlertSelector.toNotify(listOf(WeatherAlert("Tornado Warning")), emptySet()).isEmpty())
    }

    @Test
    fun remember_forgetsExpiredAndAddsNew() {
        val remembered = AlertSelector.remember(
            alerts = listOf(stormWatch),
            notifiedKeys = setOf(tornado.key, stormWatch.key),
            newlyNotified = emptyList(),
        )
        assertEquals(setOf(stormWatch.key), remembered)

        assertEquals(
            setOf(stormWatch.key, tornado.key),
            AlertSelector.remember(listOf(stormWatch, tornado), setOf(stormWatch.key), listOf(tornado)),
        )
    }
}
