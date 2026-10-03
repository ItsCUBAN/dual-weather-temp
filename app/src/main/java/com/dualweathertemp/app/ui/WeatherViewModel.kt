package com.dualweathertemp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualweathertemp.app.data.WeatherCache
import com.dualweathertemp.app.data.WeatherException
import com.dualweathertemp.app.data.WeatherReport
import com.dualweathertemp.app.data.WeatherRepository
import com.dualweathertemp.app.location.LocationProvider
import com.dualweathertemp.app.widget.WidgetRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val report: WeatherReport? = null,
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    val error: WeatherException.Reason? = null,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val cache = WeatherCache(application)
    private val locationProvider = LocationProvider(application)
    private val repository = WeatherRepository()

    private val _uiState = MutableStateFlow(
        WeatherUiState(
            report = cache.loadReport(),
            hasPermission = locationProvider.hasPermission(),
        )
    )
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    /** Called every time the screen becomes visible (also after returning from Settings). */
    fun onResume() {
        val hasPermission = locationProvider.hasPermission()
        _uiState.update { it.copy(hasPermission = hasPermission) }
        if (!hasPermission) return

        val report = _uiState.value.report
        val isStale = report == null ||
            System.currentTimeMillis() - report.timestampMillis > STALE_AFTER_MS
        if (isStale) refresh()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted, permissionDenied = !granted) }
        if (granted) refresh()
    }

    fun refresh() {
        if (!locationProvider.hasPermission()) {
            _uiState.update { it.copy(hasPermission = false) }
            return
        }
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val location = locationProvider.currentLocation()
                    ?: throw WeatherException(WeatherException.Reason.NO_LOCATION)
                cache.saveLocation(location.latitude, location.longitude)

                val report = repository.fetch(location.latitude, location.longitude)
                cache.saveReport(report)
                WidgetRenderer.updateAll(getApplication())

                _uiState.update { it.copy(report = report, isLoading = false) }
            } catch (e: WeatherException) {
                _uiState.update { it.copy(isLoading = false, error = e.reason) }
            }
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 10 * 60 * 1000L
    }
}
