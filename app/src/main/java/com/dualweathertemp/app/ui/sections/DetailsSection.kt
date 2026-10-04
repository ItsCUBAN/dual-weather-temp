package com.dualweathertemp.app.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.astro.MoonCalculator
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.sky.sunTimes
import com.dualweathertemp.app.ui.LocalUnitOrder
import com.dualweathertemp.app.ui.WeatherText
import com.dualweathertemp.app.util.TemperatureUtils
import com.dualweathertemp.app.util.Units
import java.util.Locale
import kotlin.math.roundToInt

private data class Detail(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val detail: String? = null,
    /** Short numeric values are shown larger than text values. */
    val large: Boolean = true,
)

private const val CALM_WIND_KMH = 1.0

@Composable
fun DetailsSection(report: WeatherReport, nowMillis: Long, modifier: Modifier = Modifier) {
    val details = buildDetails(report, nowMillis)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        details.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { DetailCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun buildDetails(report: WeatherReport, nowMillis: Long): List<Detail> {
    val context = LocalContext.current
    val current = report.current
    val compass = stringArrayResource(R.array.compass_directions)
    val details = mutableListOf<Detail>()

    report.uvIndex?.let { uv ->
        details += Detail(
            icon = Icons.Outlined.LightMode,
            title = stringResource(R.string.uv_title),
            value = uv.roundToInt().toString(),
            detail = listOfNotNull(
                stringResource(WeatherText.uvLevelRes(uv)),
                report.uvIndexMax?.let { stringResource(R.string.uv_max_today, it.roundToInt().toString()) },
            ).joinToString(" · "),
        )
    }
    report.usAqi?.let { aqi ->
        details += Detail(
            icon = Icons.Outlined.Eco,
            title = stringResource(R.string.aqi_title),
            value = aqi.toString(),
            detail = stringResource(WeatherText.aqiLevelRes(aqi)),
        )
    }
    current.humidityPercent?.let { humidity ->
        details += Detail(
            icon = Icons.Outlined.WaterDrop,
            title = stringResource(R.string.humidity_title),
            value = stringResource(R.string.percent, humidity.roundToInt()),
            detail = current.dewpointCelsius?.let {
                stringResource(R.string.dewpoint, WeatherText.dualTemp(it, LocalUnitOrder.current))
            },
        )
    }
    current.windKmh?.let { kmh ->
        val calm = kmh < CALM_WIND_KMH
        val kmhText = TemperatureUtils.format(kmh)
        details += Detail(
            icon = Icons.Outlined.Air,
            title = stringResource(R.string.wind_title),
            value = if (calm) {
                stringResource(R.string.wind_calm)
            } else {
                stringResource(R.string.wind_mph, TemperatureUtils.format(Units.kmhToMph(kmh)))
            },
            detail = when {
                calm -> null
                current.windDirectionDegrees != null ->
                    stringResource(R.string.wind_detail, kmhText, compass[Units.compassIndex(current.windDirectionDegrees)])
                else -> stringResource(R.string.wind_kmh, kmhText)
            },
        )
    }
    current.pressurePa?.let { pa ->
        details += Detail(
            icon = Icons.Outlined.Speed,
            title = stringResource(R.string.pressure_title),
            value = stringResource(R.string.pressure_inhg, String.format(Locale.US, "%.2f", Units.pascalsToInHg(pa))),
            detail = stringResource(R.string.pressure_hpa, Units.pascalsToHpa(pa).roundToInt().toString()),
        )
    }
    current.visibilityMeters?.let { meters ->
        details += Detail(
            icon = Icons.Outlined.Visibility,
            title = stringResource(R.string.visibility_title),
            value = stringResource(R.string.visibility_miles, TemperatureUtils.format(Units.metersToMiles(meters))),
            detail = stringResource(R.string.visibility_km, TemperatureUtils.format(Units.metersToKm(meters))),
        )
    }

    val sun = report.sunTimes(nowMillis)
    if (sun.sunriseMillis != null && sun.sunsetMillis != null) {
        details += Detail(
            icon = Icons.Outlined.WbTwilight,
            title = stringResource(R.string.sun_title),
            value = stringResource(R.string.sunrise_at, WeatherText.formatTime(context, sun.sunriseMillis, report.zoneId)),
            detail = stringResource(R.string.sunset_at, WeatherText.formatTime(context, sun.sunsetMillis, report.zoneId)),
            large = false,
        )
    }

    val moon = MoonCalculator.at(nowMillis)
    details += Detail(
        icon = Icons.Outlined.Bedtime,
        title = stringResource(R.string.moon_title),
        value = stringResource(WeatherText.moonRes(moon.phase)),
        detail = if (moon.daysUntilFull == 0) {
            stringResource(R.string.full_moon_today)
        } else {
            pluralStringResource(R.plurals.full_moon_in, moon.daysUntilFull, moon.daysUntilFull)
        },
        large = false,
    )
    return details
}

@Composable
private fun DetailCard(detail: Detail, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(detail.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = detail.title,
                style = MaterialTheme.typography.labelMedium,
                color = secondaryContentColor(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail.value,
            style = if (detail.large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        detail.detail?.let {
            Text(
                text = it,
                style = if (detail.large) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                fontWeight = if (detail.large) null else FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}
