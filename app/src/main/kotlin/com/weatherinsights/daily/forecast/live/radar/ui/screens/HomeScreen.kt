package com.weatherinsights.daily.forecast.live.radar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.weatherinsights.daily.forecast.live.radar.data.model.NotificationPreferences
import com.weatherinsights.daily.forecast.live.radar.ui.components.ErrorView
import com.weatherinsights.daily.forecast.live.radar.ui.components.LoadingView
import com.weatherinsights.daily.forecast.live.radar.ui.components.WeatherContent
import com.weatherinsights.daily.forecast.live.radar.ui.util.getDynamicBackgroundColor
import com.weatherinsights.daily.forecast.live.radar.ui.viewmodel.WeatherUiState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

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
    notificationPreferences: NotificationPreferences,
    onPreferencesChanged: (NotificationPreferences) -> Unit,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    refreshError: String?,
    onRefreshErrorShown: () -> Unit,
    isLocationPermissionGranted: Boolean,
    isAlarmPermissionGranted: Boolean,
    onRequestAlarmPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSettingsOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbarHostState.showSnackbar(refreshError)
            onRefreshErrorShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(getDynamicBackgroundColor(uiState))
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = isSettingsOpen,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { -it }
                    )
                } else {
                    slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { -it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { it }
                    )
                }
            },
            label = "settingsTransition"
        ) { targetIsSettingsOpen ->
            if (targetIsSettingsOpen) {
                SettingsScreen(
                    preferences = notificationPreferences,
                    onPreferencesChanged = onPreferencesChanged,
                    onBack = { isSettingsOpen = false },
                    isLocationPermissionGranted = isLocationPermissionGranted,
                    isAlarmPermissionGranted = isAlarmPermissionGranted,
                    onRequestLocationPermission = onRequestPermission,
                    onRequestAlarmPermission = onRequestAlarmPermission
                )
            } else {
                when (uiState) {
                    is WeatherUiState.Loading -> LoadingView()
                    is WeatherUiState.Success -> WeatherContent(
                        weatherData = uiState.weatherData,
                        yesterdayHourlyTemps = uiState.yesterdayHourlyTemps,
                        isYesterdayTemperatureLoading = uiState.isYesterdayTemperatureLoading,
                        onRefresh = onRefresh,
                        canRefresh = canRefresh,
                        isRefreshing = isRefreshing,
                        onOpenSettings = { isSettingsOpen = true }
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
