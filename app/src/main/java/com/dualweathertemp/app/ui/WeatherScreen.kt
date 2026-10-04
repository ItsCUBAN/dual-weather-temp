package com.dualweathertemp.app.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.location.LocationProvider
import com.dualweathertemp.app.sky.SkyTheme
import kotlinx.coroutines.launch

private enum class Screen { HOME, CITIES, SETTINGS }

@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val state by viewModel.uiState.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> viewModel.onPermissionResult(results.values.any { it }) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    // Asked once, after the first weather has loaded, so it doesn't pile on the location dialog.
    val hasReport = state.place(Places.CURRENT_ID).report != null
    LaunchedEffect(state.hasPermission, hasReport) {
        if (state.hasPermission && hasReport && viewModel.shouldAskNotificationPermission()) {
            viewModel.onNotificationPermissionAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    val pagerState = rememberPagerState { state.pageIds.size }
    val scope = rememberCoroutineScope()
    val visibleId = state.pageIds.getOrElse(pagerState.currentPage) { Places.CURRENT_ID }
    LaunchedEffect(visibleId) { viewModel.onPageShown(visibleId) }

    // Opening a place from the cities list or from a widget.
    LaunchedEffect(state.requestedPageId, state.pageIds) {
        val requested = state.requestedPageId ?: return@LaunchedEffect
        val index = state.pageIds.indexOf(requested)
        if (index >= 0) {
            screen = Screen.HOME
            pagerState.scrollToPage(index)
        }
        viewModel.onPageRequestHandled()
    }

    val nowMillis = System.currentTimeMillis()
    val theme = SkyTheme.resolve(state.settings.background, state.place(visibleId).report, nowMillis)
    val palette = Palette.forTheme(theme)
    val topColor by animateColorAsState(Color(theme.topColor), tween(800), label = "skyTop")
    val bottomColor by animateColorAsState(Color(theme.bottomColor), tween(800), label = "skyBottom")
    SystemBarIcons(darkIcons = palette.isLight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides palette.content,
            LocalPalette provides palette,
            LocalUnitOrder provides state.settings.unitOrder,
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    state = state,
                    pagerState = pagerState,
                    nowMillis = nowMillis,
                    onRefresh = viewModel::refresh,
                    onOpenCities = { screen = Screen.CITIES },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onRequestPermission = { permissionLauncher.launch(LocationProvider.PERMISSIONS) },
                )
                Screen.CITIES -> CitiesScreen(
                    state = state,
                    onBack = { screen = Screen.HOME },
                    onQueryChange = viewModel::onSearchQueryChange,
                    onAdd = viewModel::addPlace,
                    onRemove = viewModel::removePlace,
                    onOpen = { id ->
                        screen = Screen.HOME
                        val index = state.pageIds.indexOf(id)
                        if (index >= 0) scope.launch { pagerState.scrollToPage(index) }
                    },
                )
                Screen.SETTINGS -> SettingsScreen(
                    settings = state.settings,
                    notificationsBlocked = state.notificationsBlocked,
                    onChange = viewModel::updateSettings,
                    onBack = { screen = Screen.HOME },
                )
            }
        }
    }
}

/** Dark status and navigation bar icons on the light background, light ones otherwise. */
@Composable
private fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
