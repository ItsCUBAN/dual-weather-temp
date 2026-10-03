package com.dualweathertemp.app.data

/**
 * Maps weather.gov icon URLs (e.g. `.../icons/land/night/tsra_sct,40?size=small`) and
 * English descriptions to a [Condition].
 */
object ConditionMapper {

    fun resolve(iconUrl: String?, description: String?): Condition =
        fromIconUrl(iconUrl) ?: fromText(description) ?: Condition.CLEAR

    fun fromIconUrl(url: String?): Condition? {
        if (url.isNullOrBlank()) return null
        val path = url.substringBefore('?')
        val marker = listOf("/day/", "/night/").firstOrNull { it in path } ?: return null
        // Only the first code counts when the icon combines two ("tsra,30/rain,60").
        val code = path.substringAfter(marker).substringBefore('/').substringBefore(',')
        return fromIconCode(code)
    }

    fun fromIconCode(code: String): Condition? = when (code.removePrefix("wind_")) {
        "skc", "few", "hot", "cold" -> Condition.CLEAR
        "sct" -> Condition.PARTLY_CLOUDY
        "bkn", "ovc" -> Condition.CLOUDY
        "rain", "rain_showers", "rain_showers_hi", "fzra", "rain_fzra", "rain_sleet", "sleet" -> Condition.RAIN
        "tsra", "tsra_sct", "tsra_hi", "tornado", "hurricane", "tropical_storm" -> Condition.STORM
        "snow", "rain_snow", "snow_sleet", "snow_fzra", "blizzard" -> Condition.SNOW
        "fog", "haze", "smoke", "dust" -> Condition.FOG
        else -> null
    }

    fun fromText(text: String?): Condition? {
        val t = text?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            "thunder" in t || "storm" in t -> Condition.STORM
            listOf("snow", "flurr", "blizzard").any { it in t } -> Condition.SNOW
            listOf("rain", "drizzle", "shower", "sleet").any { it in t } -> Condition.RAIN
            listOf("fog", "haze", "mist", "smoke", "dust").any { it in t } -> Condition.FOG
            "partly" in t -> Condition.PARTLY_CLOUDY
            "cloudy" in t || "overcast" in t -> Condition.CLOUDY
            listOf("clear", "sunny", "fair").any { it in t } -> Condition.CLEAR
            else -> null
        }
    }
}
