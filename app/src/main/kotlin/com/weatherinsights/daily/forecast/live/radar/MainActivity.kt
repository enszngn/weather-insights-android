package com.weatherinsights.daily.forecast.live.radar

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.weatherinsights.daily.forecast.live.radar.ui.screens.WelcomeScreen
import com.weatherinsights.daily.forecast.live.radar.ui.components.LoadingView
import com.weatherinsights.daily.forecast.live.radar.receiver.AlarmScheduler
import com.weatherinsights.daily.forecast.live.radar.ui.screens.HomeScreen
import com.weatherinsights.daily.forecast.live.radar.ui.theme.WeatherInsightsTheme
import com.weatherinsights.daily.forecast.live.radar.ui.viewmodel.WeatherViewModel
import com.weatherinsights.daily.forecast.live.radar.worker.WeatherNotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

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
                val notificationPrefs by viewModel.notificationPreferences.collectAsState()
                val isWelcomeCompleted by viewModel.isWelcomeCompleted.collectAsState()

                var isLocationGranted by remember { mutableStateOf(hasLocationPermission()) }
                var isNotificationGranted by remember { mutableStateOf(hasNotificationPermission()) }

                val locationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    isLocationGranted = hasLocationPermission()
                    if (isWelcomeCompleted == true) {
                        viewModel.loadWeather()
                    }
                }

                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    isNotificationGranted = isGranted
                }

                LaunchedEffect(isWelcomeCompleted) {
                    if (isWelcomeCompleted == true) {
                        scheduleWeatherNotificationWorker()
                    }
                }

                LaunchedEffect(notificationPrefs) {
                    if (isWelcomeCompleted == true) {
                        syncAlarms(notificationPrefs)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (isWelcomeCompleted) {
                        null -> LoadingView()
                        false -> {
                            WelcomeScreen(
                                isLocationPermissionGranted = isLocationGranted,
                                isNotificationPermissionGranted = isNotificationGranted,
                                onRequestLocationPermission = {
                                    locationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onGetStarted = { notificationsEnabled ->
                                    viewModel.completeWelcome(notificationsEnabled)
                                }
                            )
                        }
                        true -> {
                            HomeScreen(
                                uiState = uiState,
                                notificationPreferences = notificationPrefs,
                                onPreferencesChanged = { updated ->
                                    viewModel.updateNotificationPreferences(updated)
                                },
                                onRequestPermission = {
                                    locationLauncher.launch(
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
    }

    override fun onResume() {
        super.onResume()
        syncAlarms(viewModel.notificationPreferences.value)
    }


    private fun scheduleWeatherNotificationWorker() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
            1, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "WeatherNotificationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun syncAlarms(prefs: com.weatherinsights.daily.forecast.live.radar.data.model.NotificationPreferences) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val needsExactAlarm = prefs.morningReportEnabled || prefs.eveningReportEnabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            needsExactAlarm && !alarmManager.canScheduleExactAlarms()
        ) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }

        if (prefs.morningReportEnabled) {
            AlarmScheduler.scheduleReportAlarm(
                applicationContext,
                AlarmScheduler.REPORT_MORNING,
                prefs.morningReportTime
            )
        } else {
            AlarmScheduler.cancelReportAlarm(
                applicationContext,
                AlarmScheduler.REPORT_MORNING
            )
        }

        if (prefs.eveningReportEnabled) {
            AlarmScheduler.scheduleReportAlarm(
                applicationContext,
                AlarmScheduler.REPORT_EVENING,
                prefs.eveningReportTime
            )
        } else {
            AlarmScheduler.cancelReportAlarm(
                applicationContext,
                AlarmScheduler.REPORT_EVENING
            )
        }
    }
}
