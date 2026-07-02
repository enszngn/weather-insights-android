package com.weatherinsights.daily.forecast.live.radar.data.util

object TimeUtils {
    /**
     * Parses a "HH:mm" time string into total minutes since midnight.
     * Returns null if parsing fails.
     */
    fun parseTimeToMinutes(timeString: String): Int? {
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /**
     * Parses a "HH:mm" time string into a Pair of (hour, minute).
     * Returns null if parsing fails.
     */
    fun parseTimeToHourMinute(timeString: String): Pair<Int, Int>? {
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return Pair(h, m)
    }
}
