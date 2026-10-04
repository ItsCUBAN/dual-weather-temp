package com.dualweathertemp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualweathertemp.app.alerts.AlertCheckWorker
import com.dualweathertemp.app.alerts.AlertNotifier
import com.dualweathertemp.app.data.AppSettings
import com.dualweathertemp.app.data.PlaceStore
import com.dualweathertemp.app.data.Places
import com.dualweathertemp.app.data.SavedPlace
import com.dualweathertemp.app.data.SettingsStore
import com.dualweathertemp.app.data.WeatherCache
import com.dualweathertemp.app.data.WeatherException
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.data.WeatherRepository
import com.dualweathertemp.app.location.LocationProvider
import com.dualweathertemp.app.widget.WeatherUpdateWorker
import com.dualweathertemp.app.widget.WidgetPlaces
import com.dualweathertemp.app.widget.WidgetRenderer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Weather of one page: the phone's location or a saved city. */
data class PlaceState(
    val report: WeatherReport? = null,
    val isLoading: Boolean = false,
    val error: WeatherException.Reason? = null,
)

data class SearchState(
    val query: String = "",
    val results: List<SavedPlace> = emptyList(),
    val isSearching: Boolean = false,
    val error: WeatherException.Reason? = null,
)

data class WeatherUiState(
    val savedPlaces: List<SavedPlace> = emptyList(),
    val places: Map<String, PlaceState> = emptyMap(),
    val hasPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    val notificationsBlocked: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val search: SearchState = SearchState(),
    /** Page to show next, e.g. after tapping a widget; cleared once shown. */
    val requestedPageId: String? = null,
) {
    /** The phone's location first, then saved cities in the order they were added. */
    val pageIds: List<String> get() = listOf(Places.CURRENT_ID) + savedPlaces.map { it.id }

    fun place(id: String): PlaceState = places[id] ?: PlaceState()

    /** "Louisville, KY"; null for the phone's location before its first report. */
    fun title(id: String): String? =
        if (id == Places.CURRENT_ID) {
            place(id).report?.locationName?.takeIf { it.isNotBlank() }
        } else {
            savedPlaces.firstOrNull { it.id == id }?.shortLabel
        }
}

@OptIn(FlowPreview::class)
class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val cache = WeatherCache(application)
    private val placeStore = PlaceStore(application)
    private val settingsStore = SettingsStore(application)
    private val locationProvider = LocationProvider(application)
    private val repository = WeatherRepository()
    private val alertNotifier = AlertNotifier(application)
    private val searchQuery = MutableStateFlow("")

    /** The page on screen; refreshed when the app comes back to the foreground. */
    private var visiblePageId = Places.CURRENT_ID

    private val _uiState: MutableStateFlow<WeatherUiState>
    val uiState: StateFlow<WeatherUiState>

    init {
        val saved = placeStore.load()
        val ids = listOf(Places.CURRENT_ID) + saved.map { it.id }
        _uiState = MutableStateFlow(
            WeatherUiState(
                savedPlaces = saved,
                places = ids.associateWith { PlaceState(report = cache.loadReport(it)) },
                hasPermission = locationProvider.hasPermission(),
                notificationsBlocked = !alertNotifier.canNotify(),
                settings = settingsStore.load(),
            )
        )
        uiState = _uiState.asStateFlow()

        // Keeps checking for severe weather alerts in the background (KEEP: no-op if already scheduled).
        AlertCheckWorker.schedule(application)

        viewModelScope.launch {
            searchQuery.debounce(SEARCH_DEBOUNCE_MS).collectLatest { runSearch(it) }
        }
    }

    // region Pages and refreshing

    /** Called every time the screen becomes visible (also after returning from Settings). */
    fun onResume() {
        _uiState.update {
            it.copy(hasPermission = locationProvider.hasPermission(), notificationsBlocked = !alertNotifier.canNotify())
        }
        refreshIfStale(visiblePageId)
    }

    fun onPageShown(placeId: String) {
        visiblePageId = placeId
        refreshIfStale(placeId)
    }

    fun requestPage(placeId: String) {
        _uiState.update { it.copy(requestedPageId = placeId) }
    }

    fun onPageRequestHandled() {
        _uiState.update { it.copy(requestedPageId = null) }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted, permissionDenied = !granted) }
        if (granted) refresh(Places.CURRENT_ID)
    }

    fun refresh(placeId: String) {
        val state = _uiState.value
        if (state.place(placeId).isLoading) return
        if (placeId == Places.CURRENT_ID && !locationProvider.hasPermission()) {
            _uiState.update { it.copy(hasPermission = false) }
            return
        }
        val savedPlace = state.savedPlaces.firstOrNull { it.id == placeId }
        if (placeId != Places.CURRENT_ID && savedPlace == null) return

        updatePlace(placeId) { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val (latitude, longitude) = if (savedPlace != null) {
                    savedPlace.latitude to savedPlace.longitude
                } else {
                    val location = locationProvider.currentLocation()
                        ?: throw WeatherException(WeatherException.Reason.NO_LOCATION)
                    cache.saveLocation(location.latitude, location.longitude)
                    location.latitude to location.longitude
                }

                val report = repository.fetch(latitude, longitude)
                cache.saveReport(report, placeId)
                WidgetRenderer.updateAll(getApplication())
                alertNotifier.notifyNew(
                    alerts = report.alerts,
                    placeId = placeId,
                    locationName = savedPlace?.shortLabel ?: report.locationName,
                    zone = report.zoneId,
                )
                updatePlace(placeId) { PlaceState(report = report) }
            } catch (e: WeatherException) {
                updatePlace(placeId) { it.copy(isLoading = false, error = e.reason) }
            }
        }
    }

    private fun refreshIfStale(placeId: String) {
        if (placeId == Places.CURRENT_ID && !locationProvider.hasPermission()) return
        val report = _uiState.value.place(placeId).report
        val isStale = report == null || System.currentTimeMillis() - report.timestampMillis > STALE_AFTER_MS
        if (isStale) refresh(placeId)
    }

    private fun updatePlace(placeId: String, transform: (PlaceState) -> PlaceState) {
        _uiState.update { state ->
            state.copy(places = state.places + (placeId to transform(state.place(placeId))))
        }
    }

    // endregion

    // region Notifications

    /** True once per install, on Android 13+, until the user answers the notification dialog. */
    fun shouldAskNotificationPermission(): Boolean = alertNotifier.shouldAskPermission()

    fun onNotificationPermissionAsked() = alertNotifier.markPermissionAsked()

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(notificationsBlocked = !granted) }
        if (!granted) return
        // Notify right away about alerts already active, instead of waiting for the next check.
        val state = _uiState.value
        state.pageIds.forEach { id ->
            val report = state.place(id).report ?: return@forEach
            alertNotifier.notifyNew(report.alerts, id, state.title(id) ?: report.locationName, report.zoneId)
        }
    }

    // endregion

    // region Saved cities

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(search = it.search.copy(query = query, error = null)) }
        searchQuery.value = query
    }

    private suspend fun runSearch(query: String) {
        if (query.trim().length < Places.MIN_SEARCH_LENGTH) {
            _uiState.update { it.copy(search = it.search.copy(results = emptyList(), isSearching = false)) }
            return
        }
        _uiState.update { it.copy(search = it.search.copy(isSearching = true, error = null)) }
        try {
            val results = repository.searchPlaces(query)
            _uiState.update { it.copy(search = it.search.copy(results = results, isSearching = false)) }
        } catch (e: WeatherException) {
            _uiState.update { it.copy(search = it.search.copy(results = emptyList(), isSearching = false, error = e.reason)) }
        }
    }

    /** Saves [place] (ignored if already saved or the list is full) and loads its weather. */
    fun addPlace(place: SavedPlace) {
        val current = _uiState.value.savedPlaces
        if (current.any { it.id == place.id } || current.size >= Places.MAX_SAVED) return
        val updated = current + place
        placeStore.save(updated)
        _uiState.update { it.copy(savedPlaces = updated, search = SearchState()) }
        searchQuery.value = ""
        refresh(place.id)
    }

    fun removePlace(placeId: String) {
        val updated = _uiState.value.savedPlaces.filterNot { it.id == placeId }
        placeStore.save(updated)
        cache.removeReport(placeId)
        alertNotifier.forgetPlace(placeId)
        WidgetPlaces(getApplication()).forgetPlace(placeId)
        WidgetRenderer.updateAll(getApplication())
        _uiState.update { it.copy(savedPlaces = updated, places = it.places - placeId) }
    }

    // endregion

    // region Settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = _uiState.value.settings
        val after = transform(before)
        if (after == before) return
        settingsStore.save(after)
        _uiState.update { it.copy(settings = after) }

        val app = getApplication<Application>()
        if (after.widgetRefreshMinutes != before.widgetRefreshMinutes && WidgetRenderer.hasAnyWidget(app)) {
            WeatherUpdateWorker.schedulePeriodic(app)
        }
        // Unit order and background also apply to the widgets.
        WidgetRenderer.updateAll(app)
    }

    // endregion

    private companion object {
        const val STALE_AFTER_MS = 10 * 60 * 1000L
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
