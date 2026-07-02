package com.weatherinsights.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherinsights.data.model.ForecastDay
import com.weatherinsights.ui.theme.TextPrimary
import com.weatherinsights.ui.util.WeatherMapper
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Horizontally scrollable row showing the full multi-day forecast (today + next 7 days).
 * Each item displays the day name, weather icon, and average temperature.
 */
@Composable
internal fun DailyForecastRow(
    forecast: List<ForecastDay>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(forecast) { index, day ->
            DailyForecastItem(day = day, isToday = index == 0)
        }
    }
}

@Composable
private fun DailyForecastItem(day: ForecastDay, isToday: Boolean) {
    val dayLabel = if (isToday) {
        "Today"
    } else {
        runCatching { LocalDate.parse(day.date) }
            .map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            .getOrDefault(day.date)
    }

    Column(
        modifier = Modifier
            .width(64.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayLabel,
            fontSize = 13.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = WeatherMapper.mapCodeToIcon(day.weatherCode),
            contentDescription = WeatherMapper.mapCodeToDescription(day.weatherCode),
            tint = WeatherMapper.mapCodeToIconColor(day.weatherCode),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${day.temp.roundToInt()}°",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}