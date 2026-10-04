package com.dualweathertemp.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.dualweathertemp.app.data.Condition
import com.dualweathertemp.app.sky.DayPhase

fun Condition.icon(isDay: Boolean): ImageVector = when (this) {
    Condition.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode
    Condition.PARTLY_CLOUDY -> if (isDay) Icons.Outlined.WbCloudy else Icons.Outlined.NightsStay
    Condition.CLOUDY -> Icons.Outlined.Cloud
    Condition.RAIN -> Icons.Outlined.Umbrella
    Condition.STORM -> Icons.Outlined.Thunderstorm
    Condition.SNOW -> Icons.Outlined.AcUnit
    Condition.FOG -> Icons.Outlined.Dehaze
}

/** Shows a sunset for clear skies at twilight instead of a midday sun. */
fun Condition.icon(phase: DayPhase): ImageVector {
    val clearish = this == Condition.CLEAR || this == Condition.PARTLY_CLOUDY
    return if (clearish && phase == DayPhase.TWILIGHT) Icons.Outlined.WbTwilight else icon(phase == DayPhase.DAY)
}
