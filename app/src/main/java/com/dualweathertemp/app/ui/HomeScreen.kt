package com.dualweathertemp.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.sky.dayPhase
import com.dualweathertemp.app.ui.sections.AlertCard
import com.dualweathertemp.app.ui.sections.DailySection
import com.dualweathertemp.app.ui.sections.DetailsSection
import com.dualweathertemp.app.ui.sections.GlassCard
import com.dualweathertemp.app.ui.sections.HeaderSection
import com.dualweathertemp.app.ui.sections.HourlySection
import com.dualweathertemp.app.ui.sections.TemperatureChartSection
import com.dualweathertemp.app.ui.sections.secondaryContentColor

private val CardSpacing = 10.dp
private val SidePadding = 12.dp

/** Swipeable pages, one per place, under a top bar with the place name and page dots. */
@Composable
fun HomeScreen(
    state: WeatherUiState,
    pagerState: PagerState,
    nowMillis: Long,
    onRefresh: (String) -> Unit,
    onOpenCities: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val pageIds = state.pageIds
    val visibleId = pageIds.getOrElse(pagerState.currentPage) { Places.CURRENT_ID }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        HomeTopBar(
            title = state.title(visibleId) ?: stringResource(R.string.my_location),
            isCurrentLocation = visibleId == Places.CURRENT_ID,
            pageCount = pageIds.size,
            currentPage = pagerState.currentPage,
            onOpenCities = onOpenCities,
            onOpenSettings = onOpenSettings,
        )
        HorizontalPager(
            state = pagerState,
            key = { pageIds.getOrElse(it) { Places.CURRENT_ID } },
            modifier = Modifier.weight(1f),
        ) { page ->
            val id = pageIds.getOrElse(page) { Places.CURRENT_ID }
            if (id == Places.CURRENT_ID && !state.hasPermission) {
                PermissionPrompt(permissionDenied = state.permissionDenied, onRequest = onRequestPermission)
            } else {
                PlacePage(state.place(id), nowMillis, onRefresh = { onRefresh(id) })
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    title: String,
    isCurrentLocation: Boolean,
    pageCount: Int,
    currentPage: Int,
    onOpenCities: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onOpenCities) {
            Icon(Icons.Outlined.LocationCity, contentDescription = stringResource(R.string.saved_cities))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrentLocation) {
                    Icon(Icons.Outlined.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pageCount > 1) PageDots(pageCount, currentPage)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

@Composable
private fun PageDots(pageCount: Int, currentPage: Int) {
    val description = stringResource(R.string.page_indicator, currentPage + 1, pageCount)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { contentDescription = description },
    ) {
        repeat(pageCount) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(secondaryContentColor().copy(alpha = if (index == currentPage) 1f else 0.35f)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePage(place: PlaceState, nowMillis: Long, onRefresh: () -> Unit) {
    val report = place.report
    val phase = report?.dayPhase(nowMillis) ?: DayPhase.DAY

    PullToRefreshBox(
        isRefreshing = place.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SidePadding,
                end = SidePadding,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            item { HeaderSection(report, phase, nowMillis, place.isLoading) }

            place.error?.let { reason ->
                item {
                    GlassCard(tint = Color(0x66000000)) {
                        Text(stringResource(WeatherText.errorRes(reason)), color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }

            if (report != null) reportItems(report, nowMillis)
        }
    }
}

private fun LazyListScope.reportItems(report: WeatherReport, nowMillis: Long) {
    val zone = report.zoneId
    // Several areas can carry the same alert type; one card per type is enough on screen.
    items(report.alerts.distinctBy { it.event }) { AlertCard(it, zone) }
    if (report.hourly.size >= 2) item { TemperatureChartSection(report.hourly, zone, nowMillis) }
    if (report.hourly.isNotEmpty()) item { HourlySection(report.hourly, zone, nowMillis) }
    if (report.daily.isNotEmpty()) item { DailySection(report.daily, zone, nowMillis) }
    item { DetailsSection(report, nowMillis) }
    item { Footer(report) }
}

@Composable
private fun Footer(report: WeatherReport) {
    val context = LocalContext.current
    val time = WeatherText.formatTime(context, report.timestampMillis, report.zoneId)
    val station = report.current.stationId
    val text = if (report.source == WeatherReport.Source.OBSERVATION && station != null) {
        stringResource(R.string.footer_station, time, station)
    } else {
        stringResource(R.string.footer_forecast, time)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = secondaryContentColor(),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun PermissionPrompt(permissionDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (permissionDenied) R.string.permission_denied else R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.content,
                contentColor = if (palette.isLight) Color.White else Color(0xFF1E5AA8),
            ),
        ) {
            Text(stringResource(R.string.permission_button))
        }
        if (permissionDenied) {
            Spacer(Modifier.height(8.dp))
            // After repeated denials Android stops showing the dialog; Settings is the only way back.
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.content),
            ) {
                Text(stringResource(R.string.open_settings))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permission_saved_cities_hint),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryContentColor(),
            textAlign = TextAlign.Center,
        )
    }
}
