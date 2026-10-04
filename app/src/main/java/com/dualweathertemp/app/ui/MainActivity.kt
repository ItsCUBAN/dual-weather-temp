package com.dualweathertemp.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dualweathertemp.app.ui.theme.DualWeatherTempTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light icons by default; WeatherScreen switches them for the light background.
        // Before Android 10 the system adds no scrim behind the ◁ ○ □ buttons, so add a light one.
        val navigationScrim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Color.TRANSPARENT else OLD_NAV_SCRIM
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(navigationScrim),
        )
        if (savedInstanceState == null) openRequestedPlace(intent)
        setContent {
            DualWeatherTempTheme {
                WeatherScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedPlace(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    /** A widget tap opens the app on the place that widget shows. */
    private fun openRequestedPlace(intent: Intent?) {
        intent?.getStringExtra(EXTRA_PLACE_ID)?.let(viewModel::requestPage)
    }

    companion object {
        const val EXTRA_PLACE_ID = "com.dualweathertemp.app.PLACE_ID"
        private const val OLD_NAV_SCRIM = 0x66000000
    }
}
