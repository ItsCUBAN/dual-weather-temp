package com.dualweathertemp.app.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.HourlyForecast
import com.dualweathertemp.app.ui.LocalUnitOrder
import com.dualweathertemp.app.ui.WeatherText
import java.time.ZoneId

/** Labels every third hour so they don't overlap on a phone-width card. */
private const val LABEL_EVERY_HOURS = 3

/** [first] is drawn above the point and [second] just under it, in the unit order from Settings. */
private data class ChartLabel(val first: String, val second: String, val time: String)

/** Line chart of the next 24 hours: both units above each labeled point, time along the bottom. */
@Composable
fun TemperatureChartSection(
    hours: List<HourlyForecast>,
    zone: ZoneId,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val lineColor = LocalContentColor.current
    val nowLabel = stringResource(R.string.hourly_now)

    val order = LocalUnitOrder.current
    val labels = remember(hours, nowMillis, order) {
        val (firstUnit, secondUnit) = WeatherText.units(order)
        hours.mapIndexed { index, hour ->
            ChartLabel(
                first = "${WeatherText.number(hour.celsius, firstUnit)}°",
                second = "${WeatherText.number(hour.celsius, secondUnit)}°",
                time = if (index == 0 && hour.startMillis <= nowMillis) nowLabel
                else WeatherText.formatHour(context, hour.startMillis, zone),
            )
        }
    }
    val lowest = hours.minOf { it.celsius }
    val highest = hours.maxOf { it.celsius }
    val description = stringResource(
        R.string.chart_description,
        WeatherText.dualTemp(lowest, order),
        WeatherText.dualTemp(highest, order),
    )

    GlassCard(modifier = modifier) {
        SectionTitle(stringResource(R.string.chart_title))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { this.contentDescription = description },
        ) {
            drawChart(hours, labels, lowest, highest, lineColor, textMeasurer)
        }
    }
}

private fun DrawScope.drawChart(
    hours: List<HourlyForecast>,
    labels: List<ChartLabel>,
    lowest: Double,
    highest: Double,
    lineColor: Color,
    textMeasurer: TextMeasurer,
) {
    val side = 14.dp.toPx()
    val top = 30.dp.toPx() // room for the °F/°C labels above the highest point
    val bottom = size.height - 20.dp.toPx() // room for the time labels
    // A flat day would divide by zero; give the line a little range.
    val range = (highest - lowest).coerceAtLeast(1.0)

    val points = hours.mapIndexed { index, hour ->
        Offset(
            x = side + index * (size.width - 2 * side) / (hours.size - 1),
            y = (bottom - (hour.celsius - lowest) / range * (bottom - top)).toFloat(),
        )
    }

    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val area = Path().apply {
        addPath(line)
        lineTo(points.last().x, bottom)
        lineTo(points.first().x, bottom)
        close()
    }
    drawPath(
        path = area,
        brush = Brush.verticalGradient(
            listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
            startY = top,
            endY = bottom,
        ),
    )
    drawPath(
        path = line,
        color = lineColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Every third hour, plus the day's lowest and highest points so the extremes always show a
    // number; a regular label right next to an extreme is skipped so the two don't overlap.
    val extremes = setOf(
        hours.indices.minBy { hours[it].celsius },
        hours.indices.maxBy { hours[it].celsius },
    )
    val labeled = hours.indices.filter { index ->
        index in extremes ||
            (index % LABEL_EVERY_HOURS == 0 && extremes.none { it != index && kotlin.math.abs(it - index) < 2 })
    }.toSet()

    val firstStyle = TextStyle(color = lineColor, fontSize = 11.sp)
    val secondStyle = TextStyle(color = lineColor.copy(alpha = 0.75f), fontSize = 10.sp)
    val timeStyle = TextStyle(color = lineColor.copy(alpha = 0.85f), fontSize = 10.sp)

    val gap = 4.dp.toPx()
    points.forEachIndexed { index, point ->
        // The time axis stays on a regular 3-hour rhythm, extremes included or not.
        if (index % LABEL_EVERY_HOURS == 0) {
            drawCentered(textMeasurer.measure(labels[index].time, timeStyle), point.x, bottom + gap)
        }
        if (index !in labeled) return@forEachIndexed

        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = point)
        val second = textMeasurer.measure(labels[index].second, secondStyle)
        val first = textMeasurer.measure(labels[index].first, firstStyle)
        drawCentered(second, point.x, point.y - gap - second.size.height)
        drawCentered(first, point.x, point.y - gap - second.size.height - first.size.height)
    }
}

/** Draws text centered on [centerX], kept inside the canvas at both edges. */
private fun DrawScope.drawCentered(text: TextLayoutResult, centerX: Float, top: Float) {
    val left = (centerX - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
    drawText(text, topLeft = Offset(left, top))
}
