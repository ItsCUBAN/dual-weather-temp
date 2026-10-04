package com.dualweathertemp.app.data

/** Decides which alerts deserve a notification. Pure logic, so it can be unit tested. */
object AlertSelector {

    /**
     * Warnings, watches and emergencies (tornado, severe thunderstorm, flood, winter storm,
     * extreme heat or cold…). Advisories and statements, such as dense fog or frost, stay in the app.
     */
    fun isImportant(event: String): Boolean {
        val name = event.lowercase()
        return "warning" in name || "watch" in name || "emergency" in name
    }

    /** Important alerts not yet notified, given the keys of alerts already notified. */
    fun toNotify(alerts: List<WeatherAlert>, notifiedKeys: Set<String>): List<WeatherAlert> =
        alerts.filter { it.key.isNotBlank() && it.key !in notifiedKeys && isImportant(it.event) }

    /** Keys worth remembering: the notified ones that are still active, plus the new ones. */
    fun remember(alerts: List<WeatherAlert>, notifiedKeys: Set<String>, newlyNotified: List<WeatherAlert>): Set<String> {
        val activeKeys = alerts.map { it.key }.toSet()
        return notifiedKeys.filter { it in activeKeys }.toSet() + newlyNotified.map { it.key }
    }
}
