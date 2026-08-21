package com.weatherinsights.daily.forecast.live.radar.ui.viewmodel

import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(
        val weatherData: WeatherData,
        val yesterdayHourlyTemps: List<Double>? = null,
        val isYesterdayTemperatureLoading: Boolean = true
    ) : WeatherUiState
    data class Error(val message: String, val isPermissionRequired: Boolean = false) : WeatherUiState
}
