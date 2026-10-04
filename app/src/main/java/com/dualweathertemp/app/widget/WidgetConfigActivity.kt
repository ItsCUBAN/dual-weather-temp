package com.dualweathertemp.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.PlaceStore
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.SavedPlace
import com.dualweathertemp.app.sky.SkyTheme
import com.dualweathertemp.app.ui.sections.GlassCard
import com.dualweathertemp.app.ui.sections.secondaryContentColor
import com.dualweathertemp.app.ui.theme.DualWeatherTempTheme

/**
 * Lets the user pick which place a widget shows. Opens when a widget is added (on Android 12+
 * it's optional and can be reopened by long-pressing the widget).
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without choosing cancels adding the widget, as Android expects.
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // White text on the blue sky, so the status bar icons are light too.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val places = PlaceStore(this).load()
        setContent {
            DualWeatherTempTheme {
                PlacePicker(places, onPick = ::choose)
            }
        }
    }

    private fun choose(placeId: String) {
        WidgetPlaces(this).set(appWidgetId, placeId)
        WidgetRenderer.updateAll(this)
        WeatherUpdateWorker.schedulePeriodic(this)
        WeatherUpdateWorker.refreshNow(this)
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun PlacePicker(places: List<SavedPlace>, onPick: (String) -> Unit) {
    val sky = SkyTheme.CLEAR_DAY
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(sky.topColor), Color(sky.bottomColor))))
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.titleLarge)
            PickRow(stringResource(R.string.my_location), isCurrentLocation = true) { onPick(Places.CURRENT_ID) }
            places.forEach { place ->
                PickRow(place.shortLabel, isCurrentLocation = false) { onPick(place.id) }
            }
            if (places.isEmpty()) {
                Text(
                    stringResource(R.string.widget_config_no_cities),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor(),
                )
            }
        }
    }
}

@Composable
private fun PickRow(title: String, isCurrentLocation: Boolean, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (isCurrentLocation) Icons.Outlined.NearMe else Icons.Outlined.LocationCity,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
