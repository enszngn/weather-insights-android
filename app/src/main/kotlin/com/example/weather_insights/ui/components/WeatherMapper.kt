package com.example.weather_insights.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object WeatherMapper {

    fun mapCodeToIcon(code: Int, isNight: Boolean = false): ImageVector {
        if (isNight) {
            return when (code) {
                0 -> Icons.Rounded.NightsStay
                1, 2, 3 -> Icons.Rounded.Cloud
                else -> mapCodeToIcon(code, false)
            }
        }
        return when (code) {
            0 -> Icons.Rounded.WbSunny
            1, 2, 3 -> Icons.Rounded.Cloud
            45, 48 -> Icons.Rounded.Grain // Foggy/Mist representation
            51, 53, 55 -> Icons.Rounded.WaterDrop // Drizzle
            61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop // Rainy
            71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit // Snowy
            95, 96, 99 -> Icons.Rounded.Thunderstorm // Thunderstorm
            else -> Icons.Rounded.WbSunny
        }
    }

    fun mapCodeToIconColor(code: Int, isNight: Boolean = false): Color {
        if (isNight) {
            return when (code) {
                0 -> Color(0xFFB3E5FC) // Light Ice Blue Moon
                1, 2, 3 -> Color(0xFFECEFF1) // Greyish cloud
                else -> mapCodeToIconColor(code, false)
            }
        }
        return when (code) {
            0 -> Color(0xFFFFEE58) // Yellow Sun
            1, 2, 3 -> Color(0xFFECEFF1) // Greyish cloud
            45, 48 -> Color(0xFFB0BEC5) // Foggy medium grey
            51, 53, 55 -> Color(0xFF81D4FA) // Light blueish drizzle
            61, 63, 65, 80, 81, 82 -> Color(0xFF81D4FA) // Light blueish rain
            71, 73, 75, 77, 85, 86 -> Color.White // Snowy white
            95, 96, 99 -> Color(0xFF78909C) // Dark greyish thundered cloud
            else -> Color(0xFFFFEE58)
        }
    }

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
