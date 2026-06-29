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
        loadWeather()
    }

    fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            val location = locationTracker.getCurrentLocation()
            if (location != null) {
                repository.fetchWeather(location.latitude, location.longitude)
                    .collect { result ->
                        result.fold(
                            onSuccess = { data ->
                                val geocodedName = location.cityName
                                val finalLocationName = if (!geocodedName.isNullOrEmpty()) {
                                    geocodedName
                                } else {
                                    if (data.locationName == "Çankaya" || data.locationName == "Ankara") {
                                        "Current Location"
                                    } else {
                                        data.locationName
                                    }
                                }
                                val overriddenData = data.copy(locationName = finalLocationName)
                                _uiState.value = WeatherUiState.Success(overriddenData)
                            },
                            onFailure = { error ->
                                _uiState.value = WeatherUiState.Error(
                                    error.message ?: "An unknown error occurred"
                                )
                            }
                        )
                    }
            } else {
                _uiState.value = WeatherUiState.Error(
                    message = "Location permission is required to fetch weather information.",
                    isPermissionRequired = true
                )
            }
        }
    }
}
