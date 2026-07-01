package com.example.weather_insights.data.model

import com.example.weather_insights.data.model.HourlyForecast

/**
 * Represents a single entry in the hourly weather timeline shown on the HomeScreen.
 * Entries are sorted chronologically and may include solar events between hourly rows.
 */
sealed interface TimelineEntry {
    data class Hour(val forecast: HourlyForecast, val isNight: Boolean) : TimelineEntry
    data class Sunset(val time: String) : TimelineEntry
    data class Sunrise(val time: String) : TimelineEntry
}
