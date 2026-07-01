package com.example.weather_insights

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.weather_insights.ui.screens.HomeScreen
import com.example.weather_insights.ui.theme.WeatherInsightsTheme
import com.example.weather_insights.ui.viewmodel.WeatherViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherInsightsTheme {
                val uiState by viewModel.uiState.collectAsState()
                val canRefresh by viewModel.canRefresh.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // Reload weather if any location permission is granted
                    val granted = permissions.values.any { it }
                    if (granted) {
                        viewModel.loadWeather()
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeScreen(
                        uiState = uiState,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onRetry = {
                            viewModel.loadWeather()
                        },
                        onRefresh = {
                            viewModel.refresh()
                        },
                        canRefresh = canRefresh,
                        isRefreshing = isRefreshing
                    )
                }
            }
        }
    }
}
