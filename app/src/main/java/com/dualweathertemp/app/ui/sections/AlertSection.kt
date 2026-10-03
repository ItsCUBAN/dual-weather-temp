package com.dualweathertemp.app.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.WeatherAlert
import com.dualweathertemp.app.ui.WeatherText
import java.time.ZoneId

@Composable
fun AlertCard(alert: WeatherAlert, zone: ZoneId, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tint = when (WeatherText.alertLevel(alert.event)) {
        WeatherText.AlertLevel.WARNING -> Color(0xCCD32F2F)
        WeatherText.AlertLevel.WATCH -> Color(0xCCE65100)
        WeatherText.AlertLevel.ADVISORY -> Color(0xB3B26A00)
    }
    val source = alert.endsMillis
        ?.let { stringResource(R.string.alert_source_until, WeatherText.formatTime(context, it, zone)) }
        ?: stringResource(R.string.alert_source)

    GlassCard(modifier = modifier, tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = alert.event,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = source, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
