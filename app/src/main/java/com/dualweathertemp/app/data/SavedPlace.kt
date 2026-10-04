package com.dualweathertemp.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A city the user saved, besides their own location. */
@Serializable
data class SavedPlace(
    /** Open-Meteo geocoding id, stable for the same city. */
    val id: String,
    val name: String,
    /** Full state name, e.g. "Kentucky". */
    val state: String,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Lexington, KY" */
    val shortLabel: String get() = "$name, ${UsStates.abbreviation(state)}"

    /** "Lexington, Kentucky" — used in search results to tell same-named cities apart. */
    val longLabel: String get() = "$name, $state"
}

object Places {
    /** Page and cache id of the phone's own location. */
    const val CURRENT_ID = "current"
    const val MAX_SAVED = 10

    /** Open-Meteo returns nothing for shorter queries. */
    const val MIN_SEARCH_LENGTH = 3
}

/** The user's saved cities, in the order they were added. */
class PlaceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<SavedPlace> {
        val stored = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, stored)
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    fun save(places: List<SavedPlace>) {
        prefs.edit().putString(KEY_PLACES, json.encodeToString(serializer, places)).apply()
    }

    fun find(id: String): SavedPlace? = load().firstOrNull { it.id == id }

    private companion object {
        const val PREFS_NAME = "places"
        const val KEY_PLACES = "saved_places"
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(SavedPlace.serializer())
    }
}

object UsStates {

    private val ABBREVIATIONS = mapOf(
        "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
        "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "District of Columbia" to "DC",
        "Washington, D.C." to "DC", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
        "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY",
        "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI",
        "Minnesota" to "MN", "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE",
        "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM",
        "New York" to "NY", "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH",
        "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI",
        "South Carolina" to "SC", "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT",
        "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV",
        "Wisconsin" to "WI", "Wyoming" to "WY",
    )

    /** "Kentucky" → "KY"; unknown names are returned unchanged. */
    fun abbreviation(state: String): String = ABBREVIATIONS[state] ?: state
}
