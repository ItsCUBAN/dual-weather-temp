package com.dualweathertemp.app

import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.data.ConditionMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConditionMapperTest {

    @Test
    fun iconUrl_usesFirstCode() {
        assertEquals(
            Condition.STORM,
            ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/night/tsra_sct,30/rain,60?size=medium"),
        )
        assertEquals(Condition.CLEAR, ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/day/few?size=small"))
        assertEquals(Condition.PARTLY_CLOUDY, ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/day/sct"))
        assertEquals(Condition.CLOUDY, ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/day/wind_ovc"))
        assertEquals(Condition.SNOW, ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/day/blizzard"))
        assertEquals(Condition.FOG, ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/day/haze"))
    }

    @Test
    fun iconUrl_unknownOrMissing() {
        assertNull(ConditionMapper.fromIconUrl(null))
        assertNull(ConditionMapper.fromIconUrl("https://api.weather.gov/icons/land/day/somethingnew"))
    }

    @Test
    fun text_fallback() {
        assertEquals(Condition.STORM, ConditionMapper.fromText("Chance Showers And Thunderstorms"))
        assertEquals(Condition.RAIN, ConditionMapper.fromText("Light Rain"))
        assertEquals(Condition.PARTLY_CLOUDY, ConditionMapper.fromText("Partly Cloudy"))
        assertEquals(Condition.CLOUDY, ConditionMapper.fromText("Mostly Cloudy"))
        assertEquals(Condition.CLEAR, ConditionMapper.fromText("Mostly Sunny"))
        assertNull(ConditionMapper.fromText(""))
    }

    @Test
    fun resolve_defaultsToClear() {
        assertEquals(Condition.CLEAR, ConditionMapper.resolve(null, null))
        assertEquals(Condition.RAIN, ConditionMapper.resolve(null, "Rain Showers"))
    }
}
