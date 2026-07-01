package com.example.weather_insights.ui.components

object WeatherMapper {

    fun mapCodeToEmoji(code: Int, isNight: Boolean = false): String {
        if (isNight) {
            return when (code) {
                0 -> "🌙"
                1, 2, 3 -> "☁️"
                else -> mapCodeToEmoji(code, false)
            }
        }
        return when (code) {
            0 -> "☀️"
            1, 2, 3 -> "🌤️"
            45, 48 -> "🌫️"
            51, 53, 55 -> "🌦️"
            61, 63, 65, 80, 81, 82 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "🌨️"
            95, 96, 99 -> "⚡"
            else -> "☀️"
        }
    }

    fun mapCodeToDescription(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rainy"
            71, 73, 75, 77 -> "Snowy"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Clear"
        }
    }
}
