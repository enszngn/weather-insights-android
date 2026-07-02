package com.weatherinsights.daily.forecast.live.radar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherinsights.daily.forecast.live.radar.data.model.TimelineEntry
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData
import com.weatherinsights.daily.forecast.live.radar.ui.theme.TextPrimary
import com.weatherinsights.daily.forecast.live.radar.ui.util.WeatherMapper
import com.weatherinsights.daily.forecast.live.radar.ui.util.getDynamicBackgroundColorForDay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Root composable for the weather success state. Renders a vertical pager that allows the user
 * to swipe between different forecast days like Instagram Reels.
 */
@Composable
internal fun WeatherContent(
    weatherData: WeatherData,
    onRefresh: () -> Unit,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    onOpenSettings: () -> Unit
) {
    val forecast = weatherData.forecast
    val pagerState = rememberPagerState(pageCount = { forecast.size })

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val selectedDay = forecast[pageIndex]
            val currentTemp = selectedDay.temp
            val currentWindSpeed = selectedDay.windSpeed

            val currentHour = java.time.LocalTime.now().hour
            val finalTimeline = buildTimelineForDay(weatherData, pageIndex, currentHour)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(getDynamicBackgroundColorForDay(selectedDay))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header: city name + current day + temperature (top 20%)
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
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        val dayLabel = if (pageIndex == 0) {
                            "Today"
                        } else {
                            runCatching { LocalDate.parse(selectedDay.date) }
                                .map { it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
                                .getOrDefault(selectedDay.date)
                        }
                        Text(
                            text = dayLabel,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.8f),
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

                    // Timeline panel (centre 35%)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassyPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Horizontally scrollable row with fading edges
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                        .drawWithContent {
                                            drawContent()
                                            val colors = listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)
                                            val stops = listOf(0f, 0.08f, 0.92f, 1f)
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    colorStops = stops.mapIndexed { idx, stop -> stop to colors[idx] }.toTypedArray()
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        },
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(finalTimeline) { entry ->
                                        TimelineColumn(entry)
                                    }
                                }
                            }
                        }
                    }

                    // Bottom: wind speed dashboard (bottom 45%)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f),
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
        }

        // Pager indicators (vertical dots on the right side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(forecast.size) { index ->
                val isActive = pagerState.currentPage == index
                
                val width = 6.dp
                val height by animateDpAsState(
                    targetValue = if (isActive) 12.dp else 6.dp,
                    animationSpec = tween(durationMillis = 250),
                    label = "indicator_height"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1.0f else 0.4f,
                    animationSpec = tween(durationMillis = 250),
                    label = "indicator_alpha"
                )

                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .alpha(alpha)
                        .background(Color.White, shape = CircleShape)
                )
            }
        }
    }
}

/**
 * Merges hourly forecasts with solar events (sunrise/sunset) into a single chronologically
 * sorted list, spanning 24 hours from the start hour (current hour for today, midnight for future days).
 */
private fun buildTimelineForDay(weatherData: WeatherData, dayIndex: Int, currentHour: Int): List<TimelineEntry> {
    val targetForecast = weatherData.forecast.drop(dayIndex).take(2)
    if (targetForecast.isEmpty()) return emptyList()

    // Flatten hourly data from the target day and the day after (to support day-boundary crossings)
    val allHourly = targetForecast.flatMapIndexed { dayIdx, forecastDay ->
        forecastDay.hourly.map { hourly ->
            val hour = hourly.time.substringBefore(":").toIntOrNull() ?: 0
            (dayIdx * 24 + hour) to hourly
        }
    }

    // Compute absolute hour positions for sunset/sunrise events
    val sunsets = targetForecast.mapIndexedNotNull { dayIdx, forecastDay ->
        forecastDay.sunset?.let { timeStr ->
            val time = com.weatherinsights.daily.forecast.live.radar.data.util.TimeUtils.parseTimeToHourMinute(timeStr)
            val h = time?.first ?: 20
            val m = time?.second ?: 0
            (dayIdx * 24 + h + m / 60.0) to timeStr
        }
    }
    val sunrises = targetForecast.mapIndexedNotNull { dayIdx, forecastDay ->
        forecastDay.sunrise?.let { timeStr ->
            val time = com.weatherinsights.daily.forecast.live.radar.data.util.TimeUtils.parseTimeToHourMinute(timeStr)
            val h = time?.first ?: 5
            val m = time?.second ?: 0
            (dayIdx * 24 + h + m / 60.0) to timeStr
        }
    }

    // Start at currentHour for today, start at 00:00 (midnight) for future days
    val startHour = if (dayIndex == 0) currentHour else 0

    // Filter to the 24 hours starting from startHour
    val hoursToDisplay = allHourly.filter { (absHour, _) -> absHour >= startHour }.take(24)
    if (hoursToDisplay.isEmpty()) return emptyList()

    val minHour = hoursToDisplay.first().first.toDouble()
    val maxHour = hoursToDisplay.last().first.toDouble()

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
private fun TimelineColumn(entry: TimelineEntry) {
    when (entry) {
        is TimelineEntry.Hour -> HourColumn(entry)
        is TimelineEntry.Sunset -> SolarEventColumn(time = entry.time, icon = Icons.Rounded.WbTwilight, label = "Sunset")
        is TimelineEntry.Sunrise -> SolarEventColumn(time = entry.time, icon = Icons.Rounded.WbTwilight, label = "Sunrise")
    }
}

@Composable
private fun HourColumn(entry: TimelineEntry.Hour) {
    val item = entry.forecast
    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.time,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Icon(
            imageVector = WeatherMapper.mapCodeToIcon(item.weatherCode, entry.isNight),
            contentDescription = WeatherMapper.mapCodeToDescription(item.weatherCode),
            tint = WeatherMapper.mapCodeToIconColor(item.weatherCode, entry.isNight),
            modifier = Modifier.size(24.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = "Humidity",
                tint = Color.White,
                modifier = Modifier.size(8.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "%${item.humidity}",
                fontSize = 9.sp,
                color = Color.White,
                fontWeight = FontWeight.Normal
            )
        }
        Text(
            text = "${item.temp.roundToInt()}°",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

@Composable
private fun SolarEventColumn(time: String, icon: ImageVector, label: String) {
    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = time,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD166)
        )
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFFFD166),
            modifier = Modifier.size(24.dp)
        )
        // Humidity row equivalent space-wise to align items horizontally
        Box(
            modifier = Modifier.height(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // empty space filler
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD166)
        )
    }
}
