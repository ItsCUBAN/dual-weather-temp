package com.dualweathertemp.app.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.DailyForecast
import com.dualweathertemp.app.data.HourlyForecast
import com.dualweathertemp.app.ui.WeatherText
import com.dualweathertemp.app.ui.icon
import java.time.Instant
import java.time.ZoneId

private val RainChanceColor = Color(0xFFB3E5FC)
private const val MIN_RAIN_CHANCE_SHOWN = 10

@Composable
fun HourlySection(hours: List<HourlyForecast>, zone: ZoneId, nowMillis: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    GlassCard(modifier = modifier) {
        SectionTitle(stringResource(R.string.hourly_title))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(hours) { index, hour ->
                val label = if (index == 0 && hour.startMillis <= nowMillis) {
                    stringResource(R.string.hourly_now)
                } else {
                    WeatherText.formatHour(context, hour.startMillis, zone)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(min = 44.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Icon(
                        imageVector = hour.condition.icon(hour.isDaytime),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .size(20.dp),
                    )
                    Text(stringResource(R.string.degrees, WeatherText.fahrenheit(hour.celsius)))
                    Text(
                        text = stringResource(R.string.degrees, WeatherText.celsius(hour.celsius)),
                        color = secondaryContentColor(),
                    )
                    RainChance(hour.precipitationChance)
                }
            }
        }
    }
}

@Composable
fun DailySection(days: List<DailyForecast>, zone: ZoneId, nowMillis: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val unknown = stringResource(R.string.unknown_value)

    GlassCard(modifier = modifier) {
        SectionTitle(stringResource(R.string.daily_title))
        // After midnight the forecast still starts with yesterday evening's "Tonight" period.
        val todayIso = today.toString()
        days.filter { it.date >= todayIso }.forEach { day ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = WeatherText.dayLabel(context, day.date, today),
                    modifier = Modifier.width(60.dp),
                )
                Icon(
                    imageVector = day.condition.icon(isDay = true),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                RainChance(day.precipitationChance, modifier = Modifier.width(36.dp))
                Text(
                    text = stringResource(
                        R.string.daily_range,
                        day.highCelsius?.let(WeatherText::fahrenheit) ?: unknown,
                        day.lowCelsius?.let(WeatherText::fahrenheit) ?: unknown,
                        day.highCelsius?.let(WeatherText::celsius) ?: unknown,
                        day.lowCelsius?.let(WeatherText::celsius) ?: unknown,
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RainChance(chance: Int?, modifier: Modifier = Modifier) {
    val text = chance?.takeIf { it >= MIN_RAIN_CHANCE_SHOWN }?.let { stringResource(R.string.percent, it) } ?: ""
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = RainChanceColor,
        modifier = modifier,
    )
}
