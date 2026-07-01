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
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                    scheduleWeatherNotificationWorker()
                }

                LaunchedEffect(notificationPrefs) {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val needsExactAlarm = notificationPrefs.morningReportEnabled ||
                            notificationPrefs.eveningReportEnabled

                    // On API 31+, SCHEDULE_EXACT_ALARM must be explicitly granted by the user.
                    // If any report is enabled but the permission is missing, open the system
                    // settings page so the user can grant it. Alarms will be scheduled in onResume.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        needsExactAlarm && !alarmManager.canScheduleExactAlarms()
                    ) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        return@LaunchedEffect
                    }

                    if (notificationPrefs.morningReportEnabled) {
                        AlarmScheduler.scheduleReportAlarm(
                            applicationContext,
                            AlarmScheduler.REPORT_MORNING,
                            notificationPrefs.morningReportTime
                        )
                    } else {
                        AlarmScheduler.cancelReportAlarm(
                            applicationContext,
                            AlarmScheduler.REPORT_MORNING
                        )
                    }

                    if (notificationPrefs.eveningReportEnabled) {
                        AlarmScheduler.scheduleReportAlarm(
                            applicationContext,
                            AlarmScheduler.REPORT_EVENING,
                            notificationPrefs.eveningReportTime
                        )
                    } else {
                        AlarmScheduler.cancelReportAlarm(
                            applicationContext,
                            AlarmScheduler.REPORT_EVENING
                        )
                    }
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
                            val permissions = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permissions.toTypedArray())
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

    /**
     * Re-schedules alarms when returning from the SCHEDULE_EXACT_ALARM settings page.
     * The user may have just granted the permission, so we attempt to set alarms again
     * using the preferences already loaded in the ViewModel.
     */
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                val prefs = viewModel.notificationPreferences.value
                if (prefs.morningReportEnabled) {
                    AlarmScheduler.scheduleReportAlarm(
                        applicationContext,
                        AlarmScheduler.REPORT_MORNING,
                        prefs.morningReportTime
                    )
                }
                if (prefs.eveningReportEnabled) {
                    AlarmScheduler.scheduleReportAlarm(
                        applicationContext,
                        AlarmScheduler.REPORT_EVENING,
                        prefs.eveningReportTime
                    )
                }
            }
        }
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
}
