package com.weatherinsights.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.weatherinsights.ui.components.ErrorView
import com.weatherinsights.ui.components.LoadingView
import com.weatherinsights.ui.components.WeatherContent
import com.weatherinsights.ui.util.getDynamicBackgroundColor
import com.weatherinsights.ui.viewmodel.WeatherUiState

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
    onRefresh: () -> Unit,
    canRefresh: Boolean,
    isRefreshing: Boolean,
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
            is WeatherUiState.Success -> WeatherContent(
                weatherData = uiState.weatherData,
                onRefresh = onRefresh,
                canRefresh = canRefresh,
                isRefreshing = isRefreshing
            )
            is WeatherUiState.Error -> ErrorView(
                message = uiState.message,
                isPermissionRequired = uiState.isPermissionRequired,
                onRequestPermission = onRequestPermission,
                onRetry = onRetry
            )
        }
    }
}
