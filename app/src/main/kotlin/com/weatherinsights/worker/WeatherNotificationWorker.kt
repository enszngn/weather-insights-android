package com.weatherinsights.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weatherinsights.MainActivity
import com.weatherinsights.data.datasource.WeatherLocalSource
import com.weatherinsights.data.model.ForecastDay
import com.weatherinsights.data.model.NotificationPreferences
import com.weatherinsights.data.model.WeatherData
import com.weatherinsights.data.repository.WeatherRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class WeatherNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun weatherRepository(): WeatherRepository
        fun localSource(): WeatherLocalSource
    }

    companion object {
        private const val CHANNEL_CRITICAL_ID = "weather_critical_alerts"
        private const val CHANNEL_ROUTINE_ID = "weather_routine_reports"
        
        private const val NOTIF_ID_CRITICAL = 1001
        private const val NOTIF_ID_MORNING = 1002
        private const val NOTIF_ID_EVENING = 1003
        private const val NOTIF_ID_WEEKEND = 1004
        private const val NOTIF_ID_SHOCK = 1005
        private const val NOTIF_ID_HEALTH = 1006
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val repository = entryPoint.weatherRepository()
        val localSource = entryPoint.localSource()

        val prefs = localSource.getNotificationPreferences()
        
        // 1. Retrieve last known weather data from local source.
        val weatherData = localSource.getCachedWeather() ?: return Result.success()

        createNotificationChannels()

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val isQuietHour = checkQuietHours(currentHour, currentMinute, prefs.sleepStartTime, prefs.sleepEndTime)

        // Evaluate and send notifications
        evaluateCriticalAlerts(weatherData, prefs, localSource, isQuietHour)
        evaluateMorningReport(weatherData, prefs, localSource, currentHour, currentMinute, isQuietHour)
        evaluateEveningReport(weatherData, prefs, localSource, currentHour, currentMinute, isQuietHour)
        evaluateWeekendSummary(weatherData, prefs, localSource, now, isQuietHour)
        evaluateTemperatureShock(weatherData, prefs, localSource, isQuietHour)
        evaluateHealthAlerts(weatherData, prefs, localSource, isQuietHour)

        return Result.success()
    }

    private fun checkQuietHours(
        currentHour: Int,
        currentMinute: Int,
        startTime: String,
        endTime: String
    ): Boolean {
        return try {
            val startParts = startTime.split(":").map { it.toInt() }
            val endParts = endTime.split(":").map { it.toInt() }
            
            val currentVal = currentHour * 60 + currentMinute
            val startVal = startParts[0] * 60 + startParts[1]
            val endVal = endParts[0] * 60 + endParts[1]
            
            if (startVal < endVal) {
                // E.g., 22:00 to 23:59 (same day)
                currentVal in startVal..endVal
            } else {
                // E.g., 22:00 to 07:00 (spans midnight)
                currentVal >= startVal || currentVal <= endVal
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun evaluateCriticalAlerts(
        data: WeatherData,
        prefs: NotificationPreferences,
        localSource: WeatherLocalSource,
        isQuietHour: Boolean
    ) {
        if (!prefs.criticalAlertsEnabled) return

        // Critical alerts bypass quiet hours by default
        val todayForecast = data.forecast.firstOrNull() ?: return
        val currentHourIndex = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        // Look at upcoming 3 hours
        val upcomingHours = todayForecast.hourly.filterIndexed { index, _ -> 
            index in currentHourIndex..(currentHourIndex + 2)
        }

        var stormAlertSent = false
        var rainAlertSent = false

        for (hour in upcomingHours) {
            val code = hour.weatherCode
            // Check for severe storm / extreme weather codes
            if (code in listOf(95, 96, 99) && !stormAlertSent) {
                val lastSent = localSource.getLastNotificationDate("critical_storm")
                val todayStr = getTodayString()
                if (lastSent != todayStr) {
                    sendNotification(
                        NOTIF_ID_CRITICAL,
                        CHANNEL_CRITICAL_ID,
                        "Severe Weather Alert ⚠️",
                        "Severe storm / thunderstorm expected at ${hour.time.substringAfter("T")}. Stay in a safe place!"
                    )
                    localSource.saveLastNotificationDate("critical_storm", todayStr)
                    stormAlertSent = true
                }
            }

            // Check for rain starting soon
            if (code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) && !rainAlertSent) {
                val lastSent = localSource.getLastNotificationDate("critical_rain")
                val todayStr = getTodayString()
                if (lastSent != todayStr) {
                    sendNotification(
                        NOTIF_ID_CRITICAL,
                        CHANNEL_CRITICAL_ID,
                        "Imminent Rain Alert 🌧️",
                        "Precipitation is starting soon in your area. Don't forget your umbrella!"
                    )
                    localSource.saveLastNotificationDate("critical_rain", todayStr)
                    rainAlertSent = true
                }
            }
        }
    }

    private suspend fun evaluateMorningReport(
        data: WeatherData,
        prefs: NotificationPreferences,
        localSource: WeatherLocalSource,
        currentHour: Int,
        currentMinute: Int,
        isQuietHour: Boolean
    ) {
        if (!prefs.morningReportEnabled || isQuietHour) return

        val targetTime = prefs.morningReportTime.split(":")
        if (targetTime.size != 2) return
        val targetHour = targetTime[0].toIntOrNull() ?: 8
        
        if (currentHour == targetHour) {
            val todayStr = getTodayString()
            val lastSent = localSource.getLastNotificationDate("morning_report")
            if (lastSent != todayStr) {
                val todayForecast = data.forecast.firstOrNull() ?: return
                
                // Find high/low temps from hourly forecast
                val temps = todayForecast.hourly.map { it.temp }
                val maxTemp = temps.maxOrNull() ?: todayForecast.temp
                val minTemp = temps.minOrNull() ?: todayForecast.temp

                val clothingAdvice = when {
                    minTemp < 10.0 -> "It is quite cold today. You may want to wear a thick coat."
                    maxTemp > 28.0 -> "It is quite hot today. You might want to wear light clothing."
                    todayForecast.weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) -> "Rain is expected today, remember to take your umbrella."
                    else -> "The weather is mild and pleasant, comfortable clothes are recommended."
                }

                sendNotification(
                    NOTIF_ID_MORNING,
                    CHANNEL_ROUTINE_ID,
                    "Morning Report ☀️",
                    "Today's high ${maxTemp.roundToInt()}°, low ${minTemp.roundToInt()}°. $clothingAdvice"
                )
                localSource.saveLastNotificationDate("morning_report", todayStr)
            }
        }
    }

    private suspend fun evaluateEveningReport(
        data: WeatherData,
        prefs: NotificationPreferences,
        localSource: WeatherLocalSource,
        currentHour: Int,
        currentMinute: Int,
        isQuietHour: Boolean
    ) {
        if (!prefs.eveningReportEnabled || isQuietHour) return

        val targetTime = prefs.eveningReportTime.split(":")
        if (targetTime.size != 2) return
        val targetHour = targetTime[0].toIntOrNull() ?: 20

        if (currentHour == targetHour) {
            val todayStr = getTodayString()
            val lastSent = localSource.getLastNotificationDate("evening_report")
            if (lastSent != todayStr) {
                // Retrieve tomorrow's forecast (day index 1)
                val tomorrowForecast = data.forecast.getOrNull(1) ?: return
                val weatherCode = tomorrowForecast.weatherCode
                
                val summary = when {
                    weatherCode in listOf(95, 96, 99) -> "A severe storm is expected tomorrow. You may want to postpone outdoor plans."
                    weatherCode in listOf(45, 48) -> "Heavy fog is expected tomorrow morning. You may want to leave for work a bit early."
                    weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) -> "Rain is expected all day tomorrow, prepare your umbrella."
                    else -> "Tomorrow's weather looks calm and pleasant. Have a great day!"
                }

                sendNotification(
                    NOTIF_ID_EVENING,
                    CHANNEL_ROUTINE_ID,
                    "Evening Report 🌙",
                    "Tomorrow's high ${tomorrowForecast.temp.roundToInt()}°. $summary"
                )
                localSource.saveLastNotificationDate("evening_report", todayStr)
            }
        }
    }

    private suspend fun evaluateWeekendSummary(
        data: WeatherData,
        prefs: NotificationPreferences,
        localSource: WeatherLocalSource,
        now: Calendar,
        isQuietHour: Boolean
    ) {
        if (!prefs.weekendSummaryEnabled || isQuietHour) return

        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        
        if (dayOfWeek == Calendar.FRIDAY && hour in 17..18) {
            val currentYear = now.get(Calendar.YEAR)
            val currentWeek = now.get(Calendar.WEEK_OF_YEAR)
            val weekStr = "${currentYear}_W${currentWeek}"
            val lastSent = localSource.getLastNotificationDate("weekend_summary")
            
            if (lastSent != weekStr) {
                val saturday = data.forecast.getOrNull(1)
                val sunday = data.forecast.getOrNull(2)
                
                if (saturday != null && sunday != null) {
                    val satRain = saturday.weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82)
                    val sunRain = sunday.weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82)
                    
                    val activityAdvice = if (!satRain && !sunRain) {
                        "The weekend looks clear; perfect for a picnic or walk!"
                    } else {
                        "There could be scattered rain this weekend; consider indoor activities."
                    }

                    sendNotification(
                        NOTIF_ID_WEEKEND,
                        CHANNEL_ROUTINE_ID,
                        "Weekend Summary 🏖️",
                        "Saturday high ${saturday.temp.roundToInt()}°, Sunday high ${sunday.temp.roundToInt()}°. $activityAdvice"
                    )
                    localSource.saveLastNotificationDate("weekend_summary", weekStr)
                }
            }
        }
    }

    private suspend fun evaluateTemperatureShock(
        data: WeatherData,
        prefs: NotificationPreferences,
        localSource: WeatherLocalSource,
        isQuietHour: Boolean
    ) {
        if (!prefs.tempShockEnabled || isQuietHour) return

        val today = data.forecast.getOrNull(0) ?: return
        val tomorrow = data.forecast.getOrNull(1) ?: return

        val tempDifference = tomorrow.temp - today.temp
        if (abs(tempDifference) >= 10.0) {
            val todayStr = getTodayString()
            val lastSent = localSource.getLastNotificationDate("temp_shock")
            if (lastSent != todayStr) {
                val action = if (tempDifference < 0) "dropping" else "rising"
                sendNotification(
                    NOTIF_ID_SHOCK,
                    CHANNEL_ROUTINE_ID,
                    "Temperature Shock Alert 🌡️",
                    "The temperature tomorrow is $action by ${abs(tempDifference).roundToInt()} degrees compared to today! Be prepared."
                )
                localSource.saveLastNotificationDate("temp_shock", todayStr)
            }
        }
    }

    private suspend fun evaluateHealthAlerts(
        data: WeatherData,
        prefs: NotificationPreferences,
        localSource: WeatherLocalSource,
        isQuietHour: Boolean
    ) {
        if (!prefs.healthAlertsEnabled || isQuietHour) return

        val today = data.forecast.firstOrNull() ?: return
        val todayStr = getTodayString()

        // 1. UV Alert
        if (today.uvIndex >= 6.0) {
            val lastSent = localSource.getLastNotificationDate("health_uv")
            if (lastSent != todayStr) {
                sendNotification(
                    NOTIF_ID_HEALTH,
                    CHANNEL_ROUTINE_ID,
                    "Health Alert ☀️",
                    "UV index is very high today (${today.uvIndex.roundToInt()}). Don't forget sunscreen and try to stay out of direct sun!"
                )
                localSource.saveLastNotificationDate("health_uv", todayStr)
                return
            }
        }

        // 2. Pollen Alert Simulation
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        if (month in 3..7 && today.temp > 20.0 && today.humidity < 60 && today.windSpeed > 15.0) {
            val lastSent = localSource.getLastNotificationDate("health_pollen")
            if (lastSent != todayStr) {
                sendNotification(
                    NOTIF_ID_HEALTH,
                    CHANNEL_ROUTINE_ID,
                    "Allergy Alert 🌸",
                    "Pollen levels look very high today. Take precautions if you have pollen allergies!"
                )
                localSource.saveLastNotificationDate("health_pollen", todayStr)
                return
            }
        }

        // 3. AQI Alert Simulation
        if (today.temp > 32.0 && today.humidity > 70 && today.windSpeed < 6.0) {
            val lastSent = localSource.getLastNotificationDate("health_aqi")
            if (lastSent != todayStr) {
                sendNotification(
                    NOTIF_ID_HEALTH,
                    CHANNEL_ROUTINE_ID,
                    "Air Quality Alert 🌫️",
                    "Air quality (AQI) may reach unhealthy levels for sensitive groups today. Consider limiting outdoor activities."
                )
                localSource.saveLastNotificationDate("health_aqi", todayStr)
            }
        }
    }

    private fun sendNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        text: String
    ) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                if (channelId == CHANNEL_CRITICAL_ID) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL_ID,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Severe storms, hurricanes, and imminent precipitation warnings."
                enableVibration(true)
            }

            val routineChannel = NotificationChannel(
                CHANNEL_ROUTINE_ID,
                "Routine Reports and Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily morning/evening reports, weekend summaries, and health warnings."
            }

            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(criticalChannel)
            manager.createNotificationChannel(routineChannel)
        }
    }

    private fun getTodayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
