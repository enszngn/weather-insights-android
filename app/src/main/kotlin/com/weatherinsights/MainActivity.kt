package com.weatherinsights

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
import com.weatherinsights.receiver.AlarmScheduler
import com.weatherinsights.ui.screens.HomeScreen
import com.weatherinsights.ui.theme.WeatherInsightsTheme
import com.weatherinsights.ui.viewmodel.WeatherViewModel
import com.weatherinsights.worker.WeatherNotificationWorker
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

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (locationGranted) {
                        viewModel.loadWeather()
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(buildRequiredPermissions())
                    scheduleWeatherNotificationWorker()
                }

                LaunchedEffect(notificationPrefs) {
                    syncAlarms(notificationPrefs)
                }

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HomeScreen(
                        uiState = uiState,
                        notificationPreferences = notificationPrefs,
                        onPreferencesChanged = { updated ->
                            viewModel.updateNotificationPreferences(updated)
                        },
                        onRequestPermission = {
                            permissionLauncher.launch(buildRequiredPermissions())
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

    private fun buildRequiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun syncAlarms(prefs: com.weatherinsights.data.model.NotificationPreferences) {
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
