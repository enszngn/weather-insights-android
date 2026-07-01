package com.example.weather_insights.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        loadCachedWeatherAndFetch()
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

    private fun loadCachedWeatherAndFetch() {
        viewModelScope.launch {
            val cached = repository.getCachedWeather()
            if (cached != null && _uiState.value is WeatherUiState.Loading) {
                _uiState.value = WeatherUiState.Success(cached)
            }
            loadWeather()
        }
    }

    fun loadWeather() {
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

            val location = locationTracker.getCurrentLocation()
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
                            // City name is already embedded in `data` (applied by the mapper).
                            // Use it directly; no secondary override needed.
                            _uiState.value = WeatherUiState.Success(data)
                        },
                        onFailure = { error ->
                            setNonSuccessState(
                                WeatherUiState.Error(error.message ?: "An unknown error occurred")
                            )
                        }
                    )
                }
        }
    }
}
