package com.example.weather_insights.ui.viewmodel

import com.example.weather_insights.data.model.WeatherData

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weatherData: WeatherData) : WeatherUiState
    data class Error(val message: String, val isPermissionRequired: Boolean = false) : WeatherUiState
}
