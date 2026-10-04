package com.dualweathertemp.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.dualweathertemp.app.data.UnitOrder
import com.dualweathertemp.app.sky.SkyTheme

/** Colors for content drawn on top of the background gradient. */
data class Palette(
    val content: Color,
    val card: Color,
    /** Rain chances and other small accents. */
    val accent: Color,
    val isLight: Boolean,
) {
    companion object {
        private val OnDark = Palette(
            content = Color.White,
            card = Color.White.copy(alpha = 0.16f),
            accent = Color(0xFFB3E5FC),
            isLight = false,
        )
        private val OnLight = Palette(
            content = Color(0xFF14212E),
            card = Color.Black.copy(alpha = 0.06f),
            accent = Color(0xFF1565C0),
            isLight = true,
        )

        fun forTheme(theme: SkyTheme): Palette = if (theme.isLight) OnLight else OnDark
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.forTheme(SkyTheme.CLEAR_DAY) }

/** Which unit each temperature shows first, from Settings. */
val LocalUnitOrder = staticCompositionLocalOf { UnitOrder.FAHRENHEIT_FIRST }
