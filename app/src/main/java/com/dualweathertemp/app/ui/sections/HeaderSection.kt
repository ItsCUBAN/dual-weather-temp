package com.dualweathertemp.app.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.ui.LocalUnitOrder
import com.dualweathertemp.app.ui.WeatherText
import com.dualweathertemp.app.ui.icon

/** Current conditions under the top bar; the place name lives in the top bar. */
@Composable
fun HeaderSection(
    report: WeatherReport?,
    phase: DayPhase,
    nowMillis: Long,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val order = LocalUnitOrder.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (report == null) {
            Text(
                text = stringResource(if (isLoading) R.string.loading else R.string.no_data_yet),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 48.dp, start = 24.dp, end = 24.dp),
            )
            return@Column
        }

        val current = report.current
        Icon(
            imageVector = current.condition.icon(phase),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 4.dp)
                .size(56.dp),
        )
        val (firstUnit, secondUnit) = WeatherText.units(order)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BigTemperature(WeatherText.temp(current.celsius, firstUnit))
            Text(
                text = "|",
                fontSize = 28.sp,
                color = secondaryContentColor(),
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            BigTemperature(WeatherText.temp(current.celsius, secondUnit))
        }
        Text(
            text = stringResource(WeatherText.conditionRes(current.condition, phase)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        val today = report.today(nowMillis)
        val high = today?.highCelsius?.let { WeatherText.dualTemp(it, order) }
        val low = today?.lowCelsius?.let { WeatherText.dualTemp(it, order) }
        val highLow = when {
            high != null && low != null -> stringResource(R.string.high_low, high, low)
            high != null -> stringResource(R.string.high_only, high)
            low != null -> stringResource(R.string.low_only, low)
            else -> null
        }
        highLow?.let { SecondaryLine(it) }

        current.feelsLikeCelsius?.let {
            SecondaryLine(stringResource(R.string.feels_like, WeatherText.dualTemp(it, order)))
        }
    }
}

@Composable
private fun BigTemperature(text: String) {
    Text(text = text, fontSize = 52.sp, fontWeight = FontWeight.Medium, lineHeight = 60.sp)
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = secondaryContentColor(),
        textAlign = TextAlign.Center,
    )
}
