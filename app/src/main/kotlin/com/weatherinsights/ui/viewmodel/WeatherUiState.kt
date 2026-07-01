package com.weatherinsights.ui.viewmodel

import com.weatherinsights.data.model.WeatherData

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weatherData: WeatherData) : WeatherUiState
    data class Error(val message: String, val isPermissionRequired: Boolean = false) : WeatherUiState
}
