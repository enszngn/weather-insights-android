package com.example.weather_insights.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weather_insights.data.datasource.WeatherLocalSource
import com.example.weather_insights.data.location.LocationTracker
import com.example.weather_insights.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val locationTracker: LocationTracker,
    private val localSource: WeatherLocalSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _canRefresh = MutableStateFlow(true)
    val canRefresh: StateFlow<Boolean> = _canRefresh.asStateFlow()

    /** True while a user-triggered refresh is actively in-flight. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    companion object {
        const val MAX_REFRESHES = 3
        const val WINDOW_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }

    /** In-memory window start timestamp. Loaded from DataStore on init. */
    private var refreshWindowStart: Long = 0L
    private var refreshCount: Int = 0

    init {
        viewModelScope.launch {
            restoreRefreshState()
            loadCachedWeatherAndFetch()
        }
    }

    /**
     * Reads the persisted (count, windowStart) from DataStore.
     * Resets the counter if the 15-minute window has expired.
     */
    private suspend fun restoreRefreshState() {
        val state = localSource.getRefreshState()
        if (state != null) {
            val (count, windowStart) = state
            val now = System.currentTimeMillis()
            if (now - windowStart < WINDOW_DURATION_MS) {
                // Window still active — restore the persisted count
                refreshCount = count
                refreshWindowStart = windowStart
                _canRefresh.value = refreshCount < MAX_REFRESHES
            }
            // else: window expired — leave refreshCount = 0, canRefresh = true (defaults)
        }
        // null means no state saved yet — leave defaults
    }

    /**
     * Sets the given state only when the current state is not already a Success.
     * Prevents stale-cache success from being overwritten by transient loading/error states.
     */
    private fun setNonSuccessState(state: WeatherUiState) {
        if (_uiState.value !is WeatherUiState.Success) {
            _uiState.value = state
        }
    }

    private suspend fun loadCachedWeatherAndFetch() {
        val cached = repository.getCachedWeather()
        if (cached != null && _uiState.value is WeatherUiState.Loading) {
            _uiState.value = WeatherUiState.Success(cached)
        }
        loadWeather()
    }

    fun loadWeather(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            setNonSuccessState(WeatherUiState.Loading)

            if (!locationTracker.hasLocationPermission()) {
                setNonSuccessState(
                    WeatherUiState.Error(
                        message = "Location permission is required to fetch weather information.",
                        isPermissionRequired = true
                    )
                )
                return@launch
            }

            // Pass forceRefresh through so manual refresh always gets a live GPS fix.
            val location = locationTracker.getCurrentLocation(forceRefresh = forceRefresh)
            if (location == null) {
                setNonSuccessState(
                    WeatherUiState.Error(
                        message = "Could not retrieve device location. Please ensure location services are enabled on your device."
                    )
                )
                return@launch
            }

            // Geocode before fetching — Android's local Geocoder is fast (~50–150 ms) and
            // the result must be available when the Repository fires the POST to the Worker.
            val cityName = locationTracker.getCityName(location.latitude, location.longitude)

            repository.fetchWeather(location.latitude, location.longitude, cityName)
                .collect { result ->
                    result.fold(
                        onSuccess = { data ->
                            // Always override the city name with the locally geocoded value.
                            // The cloud cache (D1) may contain a stale city name from a
                            // previous session with a different location (e.g. developer's machine).
                            val finalData = if (cityName != null) data.copy(locationName = cityName) else data
                            _uiState.value = WeatherUiState.Success(finalData)
                        },
                        onFailure = { error ->
                            setNonSuccessState(
                                WeatherUiState.Error(error.message ?: "An unknown error occurred")
                            )
                        }
                    )
                }

            if (forceRefresh) _isRefreshing.value = false
        }
    }

    /**
     * Manually refreshes weather data if the user still has remaining refreshes
     * within the current 15-minute window. Persists the updated counter to DataStore.
     */
    fun refresh() {
        if (refreshCount >= MAX_REFRESHES) return

        val now = System.currentTimeMillis()

        // Record window start on the very first refresh
        if (refreshCount == 0) {
            refreshWindowStart = now
        }

        refreshCount++
        _canRefresh.value = refreshCount < MAX_REFRESHES
        _isRefreshing.value = true

        // Persist the updated state
        viewModelScope.launch {
            localSource.saveRefreshState(refreshCount, refreshWindowStart)
        }

        // forceRefresh = true bypasses the lastLocation cache so the new emulator
        // location (or real device position) is always picked up immediately.
        loadWeather(forceRefresh = true)
    }
}
