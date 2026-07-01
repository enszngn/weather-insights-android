package com.weatherinsights.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherinsights.data.model.TimelineEntry
import com.weatherinsights.data.model.WeatherData
import com.weatherinsights.ui.theme.TextPrimary
import com.weatherinsights.ui.util.WeatherMapper
import kotlin.math.roundToInt

/**
 * Root composable for the weather success state. Renders the city header, the scrollable
 * 24-hour timeline panel, and the wind speed dashboard at the bottom.
 */
@Composable
internal fun WeatherContent(
    weatherData: WeatherData,
    onRefresh: () -> Unit,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    onOpenSettings: () -> Unit
) {
    val currentDay = weatherData.forecast.firstOrNull()
    val currentTemp = currentDay?.temp ?: 0.0
    val currentWindSpeed = currentDay?.windSpeed ?: 0.0

    val currentHour = java.time.LocalTime.now().hour
    val finalTimeline = buildTimeline(weatherData, currentHour)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header: city name + current temperature (top 20%)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = weatherData.locationName,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Infinite rotation when actively refreshing
                val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 700, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "refresh_rotation"
                )

                val buttonEnabled = canRefresh && !isRefreshing
                IconButton(
                    onClick = { if (buttonEnabled) onRefresh() },
                    modifier = Modifier
                        .size(36.dp)
                        .alpha(if (buttonEnabled) 1f else 0.35f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = when {
                            isRefreshing -> "Refreshing..."
                            !canRefresh -> "Refresh limit reached"
                            else -> "Refresh weather"
                        },
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(if (isRefreshing) rotation else 0f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${currentTemp.roundToInt()}°",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary
            )
        }

        // Timeline panel (centre 60%)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassyPanel(modifier = Modifier.fillMaxSize()) {
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

        // Bottom: wind speed dashboard (bottom 20%)
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
                Icon(
                    imageVector = Icons.Rounded.Air,
                    contentDescription = "Wind icon",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
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

/**
 * Merges hourly forecasts with solar events (sunrise/sunset) into a single chronologically
 * sorted list, spanning the next 24 hours from [currentHour].
 */
private fun buildTimeline(weatherData: WeatherData, currentHour: Int): List<TimelineEntry> {
    // Flatten hourly data from the first two days (to support day-boundary crossings)
    val allHourly = weatherData.forecast.take(2).flatMapIndexed { dayIdx, forecastDay ->
        forecastDay.hourly.map { hourly ->
            val hour = hourly.time.substringBefore(":").toIntOrNull() ?: 0
            (dayIdx * 24 + hour) to hourly
        }
    }

    // Compute absolute hour positions for sunset/sunrise events
    val sunsets = weatherData.forecast.take(2).mapIndexedNotNull { dayIdx, forecastDay ->
        forecastDay.sunset?.let { timeStr ->
            val time = com.weatherinsights.data.util.TimeUtils.parseTimeToHourMinute(timeStr)
            val h = time?.first ?: 20
            val m = time?.second ?: 0
            (dayIdx * 24 + h + m / 60.0) to timeStr
        }
    }
    val sunrises = weatherData.forecast.take(2).mapIndexedNotNull { dayIdx, forecastDay ->
        forecastDay.sunrise?.let { timeStr ->
            val time = com.weatherinsights.data.util.TimeUtils.parseTimeToHourMinute(timeStr)
            val h = time?.first ?: 5
            val m = time?.second ?: 0
            (dayIdx * 24 + h + m / 60.0) to timeStr
        }
    }

    // Filter to the 24 hours starting from now
    val hoursToDisplay = allHourly.filter { (absHour, _) -> absHour >= currentHour }.take(24)
    val minHour = hoursToDisplay.firstOrNull()?.first?.toDouble() ?: currentHour.toDouble()
    val maxHour = hoursToDisplay.lastOrNull()?.first?.toDouble() ?: (currentHour + 23).toDouble()

    // Merge hourly entries and solar events into one list
    val merged = mutableListOf<Pair<Double, TimelineEntry>>()

    hoursToDisplay.forEach { (absHour, hourly) ->
        val dayIdx = absHour / 24
        val hourOfDay = absHour % 24
        val localSunset = sunsets.getOrNull(dayIdx)?.first?.minus(dayIdx * 24) ?: 20.0
        val localSunrise = sunrises.getOrNull(dayIdx)?.first?.minus(dayIdx * 24) ?: 5.0
        val isNight = hourOfDay >= localSunset || hourOfDay < localSunrise
        merged.add(absHour.toDouble() to TimelineEntry.Hour(hourly, isNight))
    }

    sunsets.forEach { (absHour, timeStr) ->
        if (absHour in minHour..maxHour) merged.add(absHour to TimelineEntry.Sunset(timeStr))
    }
    sunrises.forEach { (absHour, timeStr) ->
        if (absHour in minHour..maxHour) merged.add(absHour to TimelineEntry.Sunrise(timeStr))
    }

    return merged.sortedBy { it.first }.map { it.second }
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    when (entry) {
        is TimelineEntry.Hour -> HourRow(entry)
        is TimelineEntry.Sunset -> SolarEventRow(time = entry.time, icon = Icons.Rounded.WbTwilight, label = "Sunset")
        is TimelineEntry.Sunrise -> SolarEventRow(time = entry.time, icon = Icons.Rounded.WbTwilight, label = "Sunrise")
    }
}

@Composable
private fun HourRow(entry: TimelineEntry.Hour) {
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
            Icon(
                imageVector = WeatherMapper.mapCodeToIcon(item.weatherCode, entry.isNight),
                contentDescription = WeatherMapper.mapCodeToDescription(item.weatherCode),
                tint = WeatherMapper.mapCodeToIconColor(item.weatherCode, entry.isNight),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = "Humidity",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
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

/**
 * Shared layout for sunrise and sunset timeline entries. The original [HomeScreen.kt]
 * had duplicate code for these two cases; this composable eliminates that duplication.
 */
@Composable
private fun SolarEventRow(time: String, icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD166),
            modifier = Modifier.width(60.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFFFFD166),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD166)
            )
        }
        Spacer(modifier = Modifier.width(60.dp))
    }
}
