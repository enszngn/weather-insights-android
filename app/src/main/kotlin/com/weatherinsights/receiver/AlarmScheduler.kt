package com.weatherinsights.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_TRIGGER_REPORT = "com.weatherinsights.TRIGGER_REPORT"
    const val EXTRA_REPORT_TYPE = "report_type"
    const val REPORT_MORNING = "morning_report"
    const val REPORT_EVENING = "evening_report"

    private fun getRequestCode(reportType: String): Int {
        return if (reportType == REPORT_MORNING) 2001 else 2002
    }

    fun scheduleReportAlarm(context: Context, reportType: String, timeString: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val parts = timeString.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val intent = Intent(context, WeatherNotificationReceiver::class.java).apply {
            action = ACTION_TRIGGER_REPORT
            putExtra(EXTRA_REPORT_TYPE, reportType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(reportType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Only schedule if the user has granted SCHEDULE_EXACT_ALARM.
            // If not granted, MainActivity will redirect the user to grant it.
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelReportAlarm(context: Context, reportType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WeatherNotificationReceiver::class.java).apply {
            action = ACTION_TRIGGER_REPORT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(reportType),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
