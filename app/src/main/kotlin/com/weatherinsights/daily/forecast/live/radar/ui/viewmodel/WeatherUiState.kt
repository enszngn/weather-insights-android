package com.weatherinsights.daily.forecast.live.radar.ui.viewmodel

import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weatherData: WeatherData) : WeatherUiState
    data class Error(val message: String, val isPermissionRequired: Boolean = false) : WeatherUiState
}
