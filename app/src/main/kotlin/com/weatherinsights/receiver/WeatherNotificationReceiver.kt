package com.weatherinsights.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.weatherinsights.data.datasource.WeatherLocalSource
import com.weatherinsights.worker.WeatherNotificationWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WeatherNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var localSource: WeatherLocalSource

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_TRIGGER_REPORT) return

        val reportType = intent.getStringExtra(AlarmScheduler.EXTRA_REPORT_TYPE) ?: return

        // 1. Trigger the immediate one-time WorkManager task to fetch weather and post notification
        val inputData = Data.Builder()
            .putString(AlarmScheduler.EXTRA_REPORT_TYPE, reportType)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WeatherNotificationWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)

        // 2. Reschedule the alarm for the next day
        kotlinx.coroutines.runBlocking {
            val prefs = localSource.getNotificationPreferences()
            val timeString = when (reportType) {
                AlarmScheduler.REPORT_MORNING -> {
                    if (prefs.morningReportEnabled) prefs.morningReportTime else null
                }
                AlarmScheduler.REPORT_EVENING -> {
                    if (prefs.eveningReportEnabled) prefs.eveningReportTime else null
                }
                else -> null
            }
            
            if (timeString != null) {
                AlarmScheduler.scheduleReportAlarm(context.applicationContext, reportType, timeString)
            }
        }
    }
}
