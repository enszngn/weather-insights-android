package com.example.weather_insights.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.weather_insights.ui.components.ErrorView
import com.example.weather_insights.ui.components.LoadingView
import com.example.weather_insights.ui.components.WeatherContent
import com.example.weather_insights.ui.util.getDynamicBackgroundColor
import com.example.weather_insights.ui.viewmodel.WeatherUiState

/**
 * Root screen composable. Responsible only for:
 * 1. Computing the ambient background color from the current UI state.
 * 2. Routing to the correct full-screen child composable based on state.
 *
 * All content is delegated to focused components in ui/components.
 */
@Composable
fun HomeScreen(
    uiState: WeatherUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(getDynamicBackgroundColor(uiState))
            .systemBarsPadding()
    ) {
        when (uiState) {
            is WeatherUiState.Loading -> LoadingView()
            is WeatherUiState.Success -> WeatherContent(uiState.weatherData)
            is WeatherUiState.Error -> ErrorView(
                message = uiState.message,
                isPermissionRequired = uiState.isPermissionRequired,
                onRequestPermission = onRequestPermission,
                onRetry = onRetry
            )
        }
    }
}
