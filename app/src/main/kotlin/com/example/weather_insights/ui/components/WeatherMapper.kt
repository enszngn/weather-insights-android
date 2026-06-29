package com.example.weather_insights.ui.components

import androidx.compose.ui.graphics.Color

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

    fun mapCodeToGradient(code: Int): List<Color> {
        return when (code) {
            0 -> listOf(Color(0xFFFFD166), Color(0xFF4A90E2)) // Sunny: Warm orange to soft blue
            1, 2, 3 -> listOf(Color(0xFF83A4D4), Color(0xFFB6FBFF)) // Cloudy: Steel blue to soft cyan
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> listOf(Color(0xFF2C3E50), Color(0xFF3498DB)) // Rain: Dark slate to blue
            95, 96, 99 -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)) // Thunderstorm: Dark purple/teal gradient
            else -> listOf(Color(0xFF8E9EAB), Color(0xFFEEF2F3)) // Default / Mist: Light gray / fog
        }
    }
}
