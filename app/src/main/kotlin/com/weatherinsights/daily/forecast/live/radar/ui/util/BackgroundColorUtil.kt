package com.weatherinsights.daily.forecast.live.radar.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.weatherinsights.daily.forecast.live.radar.data.model.ForecastDay
import com.weatherinsights.daily.forecast.live.radar.ui.viewmodel.WeatherUiState
import kotlin.math.abs

/**
 * Computes a dynamic background color based on the current time of day relative to
 * the actual sunrise and sunset times from the weather data.
 *
 * Interpolates linearly from a bright blue (#009AFF) at solar midday to a deep
 * midnight blue (#001533) at midnight, creating a natural day/night gradient.
 */
fun getDynamicBackgroundColor(uiState: WeatherUiState): Color {
    return when (uiState) {
        is WeatherUiState.Success -> {
            val currentDay = uiState.weatherData.forecast.firstOrNull()
            if (currentDay != null) {
                getDynamicBackgroundColorForDay(currentDay)
            } else {
                Color(0xFF009AFF)
            }
        }
        else -> {
            val now = java.time.LocalTime.now()
            val hour = now.hour
            val minute = now.minute
            val currentAbsoluteHour = hour + (minute / 60.0)
            val sunrise = 6.0
            val sunset = 20.0
            val dayLength = sunset - sunrise
            val midDay = sunrise + dayLength / 2.0
            val diff = abs(currentAbsoluteHour - midDay) % 24.0
            val circularDistance = if (diff > 12.0) 24.0 - diff else diff
            val factor = (circularDistance / 12.0).toFloat().coerceIn(0f, 1f)
            lerp(Color(0xFF009AFF), Color(0xFF001533), factor)
        }
    }
}

/**
 * Computes the dynamic background color for a specific ForecastDay using the current time of day.
 */
fun getDynamicBackgroundColorForDay(day: ForecastDay): Color {
    val now = java.time.LocalTime.now()
    val hour = now.hour
    val minute = now.minute
    val currentAbsoluteHour = hour + (minute / 60.0)

    val rTime = day.sunrise
    val sTime = day.sunset

    val rHour = rTime?.substringBefore(":")?.toIntOrNull() ?: 6
    val rMin = rTime?.substringAfter(":")?.toIntOrNull() ?: 0
    val sHour = sTime?.substringBefore(":")?.toIntOrNull() ?: 20
    val sMin = sTime?.substringAfter(":")?.toIntOrNull() ?: 0

    val sunrise = rHour + (rMin / 60.0)
    val sunset = sHour + (sMin / 60.0)

    val dayLength = if (sunset > sunrise) sunset - sunrise else 24.0 - (sunrise - sunset)
    val midDay = (sunrise + dayLength / 2.0) % 24.0

    val diff = abs(currentAbsoluteHour - midDay) % 24.0
    val circularDistance = if (diff > 12.0) 24.0 - diff else diff

    val factor = (circularDistance / 12.0).toFloat().coerceIn(0f, 1f)

    val dayColor = Color(0xFF009AFF)
    val nightColor = Color(0xFF001533)

    return lerp(dayColor, nightColor, factor)
}
