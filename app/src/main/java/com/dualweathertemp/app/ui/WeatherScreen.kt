package com.dualweathertemp.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.location.LocationProvider
import com.dualweathertemp.app.sky.DayPhase
import com.dualweathertemp.app.sky.SkyTheme
import com.dualweathertemp.app.sky.dayPhase
import com.dualweathertemp.app.sky.skyTheme
import com.dualweathertemp.app.ui.sections.AlertCard
import com.dualweathertemp.app.ui.sections.DailySection
import com.dualweathertemp.app.ui.sections.DetailsSection
import com.dualweathertemp.app.ui.sections.GlassCard
import com.dualweathertemp.app.ui.sections.HeaderSection
import com.dualweathertemp.app.ui.sections.HourlySection
import com.dualweathertemp.app.ui.sections.secondaryContentColor

private val CardSpacing = 10.dp
private val SidePadding = 12.dp

@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val state by viewModel.uiState.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> viewModel.onPermissionResult(results.values.any { it }) }

    val nowMillis = System.currentTimeMillis()
    val theme = state.report?.skyTheme(nowMillis) ?: SkyTheme.CLEAR_DAY
    val topColor by animateColorAsState(Color(theme.topColor), tween(800), label = "skyTop")
    val bottomColor by animateColorAsState(Color(theme.bottomColor), tween(800), label = "skyBottom")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            if (state.hasPermission) {
                WeatherContent(state, nowMillis, onRefresh = viewModel::refresh)
            } else {
                PermissionPrompt(
                    permissionDenied = state.permissionDenied,
                    onRequest = { permissionLauncher.launch(LocationProvider.PERMISSIONS) },
                )
            }
        }
        // Keeps scrolled cards from running under the clock and status icons.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(topColor),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherContent(state: WeatherUiState, nowMillis: Long, onRefresh: () -> Unit) {
    val report = state.report
    val isDay = report?.dayPhase(nowMillis) != DayPhase.NIGHT

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SidePadding,
                end = SidePadding,
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            item { HeaderSection(report, isDay, nowMillis, state.isLoading) }

            state.error?.let { reason ->
                item {
                    GlassCard(tint = Color(0x66000000)) {
                        Text(stringResource(WeatherText.errorRes(reason)), textAlign = TextAlign.Center)
                    }
                }
            }

            if (report != null) reportItems(report, nowMillis)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(report: WeatherReport, nowMillis: Long) {
    val zone = report.zoneId
    items(report.alerts) { AlertCard(it, zone) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(24.dp))
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
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1E5AA8)),
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}
