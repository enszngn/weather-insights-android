package com.example.weather_insights.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weather_insights.data.model.WeatherData
import com.example.weather_insights.data.model.HourlyForecast
import com.example.weather_insights.ui.components.GlassyPanel
import com.example.weather_insights.ui.components.WeatherMapper
import com.example.weather_insights.ui.theme.TextPrimary
import com.example.weather_insights.ui.theme.TextSecondary
import com.example.weather_insights.ui.viewmodel.WeatherUiState
import kotlin.math.roundToInt

sealed interface TimelineEntry {
    data class Hour(val forecast: HourlyForecast, val isNight: Boolean) : TimelineEntry
    data class Sunset(val time: String) : TimelineEntry
    data class Sunrise(val time: String) : TimelineEntry
}

@Composable
fun HomeScreen(
    uiState: WeatherUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = getDynamicBackgroundColor(uiState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding()
    ) {
        when (uiState) {
            is WeatherUiState.Loading -> LoadingView()
            is WeatherUiState.Success -> MainWeatherContent(uiState.weatherData)
            is WeatherUiState.Error -> ErrorView(
                message = uiState.message,
                isPermissionRequired = uiState.isPermissionRequired,
                onRequestPermission = onRequestPermission,
                onRetry = onRetry
            )
        }
    }
}

private fun getDynamicBackgroundColor(uiState: WeatherUiState): Color {
    val calendar = java.util.Calendar.getInstance()
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    val currentAbsoluteHour = hour + (minute / 60.0)

    val (sunrise, sunset) = when (uiState) {
        is WeatherUiState.Success -> {
            val currentDay = uiState.weatherData.forecast.firstOrNull()
            val rTime = currentDay?.sunrise
            val sTime = currentDay?.sunset

            val rHour = rTime?.substringBefore(":")?.toIntOrNull() ?: 6
            val rMin = rTime?.substringAfter(":")?.toIntOrNull() ?: 0
            val sHour = sTime?.substringBefore(":")?.toIntOrNull() ?: 20
            val sMin = sTime?.substringAfter(":")?.toIntOrNull() ?: 0

            (rHour + (rMin / 60.0)) to (sHour + (sMin / 60.0))
        }
        else -> 6.0 to 20.0
    }

    val dayLength = if (sunset > sunrise) sunset - sunrise else 24.0 - (sunrise - sunset)
    val midDay = (sunrise + dayLength / 2.0) % 24.0

    val diff = Math.abs(currentAbsoluteHour - midDay) % 24.0
    val circularDistance = if (diff > 12.0) 24.0 - diff else diff

    val factor = (circularDistance / 12.0).toFloat().coerceIn(0f, 1f)

    val startColor = Color(0xFF009AFF)
    val endColor = Color(0xFF001533)

    return lerp(startColor, endColor, factor)
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GlassyPanel(
            modifier = Modifier.padding(32.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Retrieving Weather...",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    isPermissionRequired: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GlassyPanel(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isPermissionRequired) "📍 Location Access Required" else "⚠️ An Error Occurred",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = if (isPermissionRequired) onRequestPermission else onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isPermissionRequired) "Grant Location Permission" else "Try Again",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MainWeatherContent(weatherData: WeatherData) {
    val currentDay = weatherData.forecast.firstOrNull()
    val currentTemp = currentDay?.temp ?: 0.0
    val currentWindSpeed = currentDay?.windSpeed ?: 0.0

    // Get current hour of the day
    val calendar = java.util.Calendar.getInstance()
    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

    // Flatten hourly forecasts of the first two days to support day-boundaries
    val allHourly = weatherData.forecast.take(2).flatMapIndexed { index, forecastDay ->
        forecastDay.hourly.map { hourly ->
            val hour = hourly.time.substringBefore(":").toIntOrNull() ?: 0
            val absoluteHour = index * 24 + hour
            absoluteHour to hourly
        }
    }

    // Determine sunset absolute hours for today and tomorrow
    val sunsets = weatherData.forecast.take(2).mapIndexedNotNull { index, forecastDay ->
        forecastDay.sunset?.let { timeStr ->
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 20
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val absoluteSunsetHour = index * 24 + hour + (minute / 60.0)
            absoluteSunsetHour to timeStr
        }
    }

    // Determine sunrise absolute hours for today and tomorrow (to calculate night duration)
    val sunrises = weatherData.forecast.take(2).mapIndexedNotNull { index, forecastDay ->
        forecastDay.sunrise?.let { timeStr ->
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 5
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val absoluteSunriseHour = index * 24 + hour + (minute / 60.0)
            absoluteSunriseHour to timeStr
        }
    }

    // Filter hours starting from current hour of day 0
    val hoursToDisplay = allHourly
        .filter { (absHour, _) -> absHour >= currentHour }
        .take(24)

    // Merge hours and sunsets chronologically
    val merged = mutableListOf<Pair<Double, TimelineEntry>>()

    hoursToDisplay.forEach { (absHour, hourly) ->
        val dayIndex = absHour / 24
        val hourOfDay = absHour % 24
        val localSunset = sunsets.getOrNull(dayIndex)?.first?.minus(dayIndex * 24) ?: 20.0
        val localSunrise = sunrises.getOrNull(dayIndex)?.first?.minus(dayIndex * 24) ?: 5.0
        val isNight = hourOfDay >= localSunset || hourOfDay < localSunrise

        merged.add(absHour.toDouble() to TimelineEntry.Hour(hourly, isNight))
    }

    sunsets.forEach { (absSunsetHour, timeStr) ->
        val minHour = hoursToDisplay.firstOrNull()?.first ?: currentHour
        val maxHour = hoursToDisplay.lastOrNull()?.first ?: (currentHour + 23)
        if (absSunsetHour >= minHour && absSunsetHour <= maxHour) {
            merged.add(absSunsetHour to TimelineEntry.Sunset(timeStr))
        }
    }

    sunrises.forEach { (absSunriseHour, timeStr) ->
        val minHour = hoursToDisplay.firstOrNull()?.first ?: currentHour
        val maxHour = hoursToDisplay.lastOrNull()?.first ?: (currentHour + 23)
        if (absSunriseHour >= minHour && absSunriseHour <= maxHour) {
            merged.add(absSunriseHour to TimelineEntry.Sunrise(timeStr))
        }
    }

    val finalTimeline = merged.sortedBy { it.first }.map { it.second }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = weatherData.locationName,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${currentTemp.roundToInt()}°",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassyPanel(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(finalTimeline) { entry ->
                            TimelineRow(entry)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "💨",
                    fontSize = 38.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Wind Speed",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${currentWindSpeed.roundToInt()} km/h",
                    fontSize = 26.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    when (entry) {
        is TimelineEntry.Hour -> {
            val item = entry.forecast
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.time,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.width(60.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = WeatherMapper.mapCodeToEmoji(item.weatherCode, entry.isNight),
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💧",
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "%${item.humidity}",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Text(
                    text = "${item.temp.roundToInt()}°",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(60.dp)
                )
            }
        }
        is TimelineEntry.Sunset -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.time,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD166),
                    modifier = Modifier.width(60.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🌇",
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sunset",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166)
                    )
                }

                Spacer(modifier = Modifier.width(60.dp))
            }
        }
        is TimelineEntry.Sunrise -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.time,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD166),
                    modifier = Modifier.width(60.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🌅",
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sunrise",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166)
                    )
                }

                Spacer(modifier = Modifier.width(60.dp))
            }
        }
    }
}
