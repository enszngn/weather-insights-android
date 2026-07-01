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
            if (_uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Loading
            }
            if (locationTracker.hasLocationPermission()) {
                val location = locationTracker.getCurrentLocation()
                if (location != null) {
                    // Launch geocoding concurrently in the background
                    var geocodedCityName: String? = null
                    val geocodingJob = launch {
                        geocodedCityName = locationTracker.getCityName(location.latitude, location.longitude)
                    }

                    repository.fetchWeather(location.latitude, location.longitude)
                        .collect { result ->
                            result.fold(
                                onSuccess = { data ->
                                    // Wait for the concurrent geocoding job to complete
                                    geocodingJob.join()

                                    val finalLocationName = if (!geocodedCityName.isNullOrEmpty()) {
                                        geocodedCityName!!
                                    } else {
                                        data.locationName
                                    }
                                    val overriddenData = data.copy(locationName = finalLocationName)
                                    _uiState.value = WeatherUiState.Success(overriddenData)
                                },
                                onFailure = { error ->
                                    if (_uiState.value !is WeatherUiState.Success) {
                                        _uiState.value = WeatherUiState.Error(
                                            error.message ?: "An unknown error occurred"
                                        )
                                    }
                                }
                            )
                        }
                } else {
                    if (_uiState.value !is WeatherUiState.Success) {
                        _uiState.value = WeatherUiState.Error(
                            message = "Could not retrieve device location. Please ensure location services are enabled on your device."
                        )
                    }
                }
            } else {
                if (_uiState.value !is WeatherUiState.Success) {
                    _uiState.value = WeatherUiState.Error(
                        message = "Location permission is required to fetch weather information.",
                        isPermissionRequired = true
                    )
                }
            }
        }
    }
}
