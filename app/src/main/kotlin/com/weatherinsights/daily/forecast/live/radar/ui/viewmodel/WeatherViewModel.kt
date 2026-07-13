package com.weatherinsights.daily.forecast.live.radar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherinsights.daily.forecast.live.radar.data.datasource.WeatherLocalSource
import com.weatherinsights.daily.forecast.live.radar.data.location.LocationTracker
import com.weatherinsights.daily.forecast.live.radar.data.model.NotificationPreferences
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData
import com.weatherinsights.daily.forecast.live.radar.data.repository.WeatherRepository
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    private val _isWelcomeCompleted = MutableStateFlow<Boolean?>(null)
    val isWelcomeCompleted: StateFlow<Boolean?> = _isWelcomeCompleted.asStateFlow()

    companion object {
        const val MAX_REFRESHES = 3
        const val WINDOW_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }

    private var refreshWindowStart: Long = 0L
    private var refreshCount: Int = 0

    init {
        viewModelScope.launch {
            val completed = localSource.isWelcomeCompleted()
            _isWelcomeCompleted.value = completed
            if (completed) {
                restoreRefreshState()
                loadCachedWeatherAndFetch()
            }
        }
        viewModelScope.launch {
            _notificationPreferences.value = localSource.getNotificationPreferences()
        }
    }

    fun completeWelcome(notificationsEnabled: Boolean) {
        viewModelScope.launch {
            val defaultPrefs = NotificationPreferences()
            val prefs = if (notificationsEnabled) {
                defaultPrefs
            } else {
                defaultPrefs.copy(
                    criticalAlertsEnabled = false,
                    morningReportEnabled = false,
                    eveningReportEnabled = false,
                    weekendSummaryEnabled = false,
                    tempShockEnabled = false
                )
            }
            localSource.saveNotificationPreferences(prefs)
            _notificationPreferences.value = prefs

            localSource.setWelcomeCompleted(true)
            _isWelcomeCompleted.value = true

            restoreRefreshState()
            loadWeather()
        }
    }

    fun updateNotificationPreferences(prefs: NotificationPreferences) {
        viewModelScope.launch {
            localSource.saveNotificationPreferences(prefs)
            _notificationPreferences.value = prefs
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
            loadYesterdayTemperature(cached.lat, cached.lon)
        }
        loadWeather()
    }

    fun loadWeather(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            setNonSuccessState(WeatherUiState.Loading)

            val hasPermission = locationTracker.hasLocationPermission()
            val location = if (hasPermission) {
                locationTracker.getCurrentLocation(forceRefresh = forceRefresh)
            } else {
                null
            }

            val lat: Double? = location?.latitude
            val lon: Double? = location?.longitude

            // Geocode before fetching — Android's local Geocoder is fast (~50–150 ms) and
            // the result must be available when the Repository fires the POST to the Worker.
            val cityName = if (lat != null && lon != null) {
                locationTracker.getCityName(lat, lon)
            } else {
                null
            }

            repository.fetchWeather(lat, lon, cityName)
                .collect { result ->
                    result.fold(
                        onSuccess = { data ->
                            val finalData = if (cityName != null) data.copy(locationName = cityName) else data
                            _uiState.value = WeatherUiState.Success(finalData)
                            loadYesterdayTemperature(finalData.lat, finalData.lon)

                            // Background fallback: if the city name is still generic ("Current Location" or blank)
                            // but we have valid coordinates, try reverse-geocoding them on the client.
                            if (cityName == null && (finalData.locationName == "Current Location" || finalData.locationName.isBlank())) {
                                viewModelScope.launch {
                                    val resolvedName = locationTracker.getCityName(finalData.lat, finalData.lon)
                                    if (resolvedName != null && resolvedName.isNotBlank() && resolvedName != "Current Location") {
                                        val updatedData = finalData.copy(locationName = resolvedName)
                                        localSource.saveWeatherToCache(updatedData)
                                        
                                        // Update UI if the state is still Success and for the same coordinates
                                        val currentState = _uiState.value
                                        if (currentState is WeatherUiState.Success &&
                                            currentState.weatherData.lat == finalData.lat &&
                                            currentState.weatherData.lon == finalData.lon
                                        ) {
                                            _uiState.value = WeatherUiState.Success(updatedData, currentState.yesterdayHourlyTemps)
                                        }
                                    }
                                }
                            }
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

    private fun loadYesterdayTemperature(lat: Double, lon: Double) {
        viewModelScope.launch {
            val yesterday = java.time.LocalDate.now().minusDays(1)
            val dateString = yesterday.toString()
            val temps = repository.getYesterdayTemperature(lat, lon, dateString)
            val currentState = _uiState.value
            if (currentState is WeatherUiState.Success &&
                currentState.weatherData.lat == lat &&
                currentState.weatherData.lon == lon
            ) {
                _uiState.value = currentState.copy(yesterdayHourlyTemps = temps)
            }
        }
    }
}
