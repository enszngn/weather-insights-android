package com.weatherinsights.daily.forecast.live.radar.ui.util

import com.weatherinsights.daily.forecast.live.radar.data.model.ForecastDay

internal data class TemperatureComparison(
    val selectedDayTemperature: Double?,
    val previousDayTemperature: Double?
)

/** Returns temperatures for the same local hour on the selected and previous days. */
internal fun buildTemperatureComparison(
    forecast: List<ForecastDay>,
    selectedDayIndex: Int,
    hour: Int,
    yesterdayHourlyTemperatures: List<Double>?
): TemperatureComparison {
    val selectedDayTemperature = forecast.getOrNull(selectedDayIndex)?.temperatureAtHour(hour)
    val previousDayTemperature = if (selectedDayIndex == 0) {
        yesterdayHourlyTemperatures?.getOrNull(hour)
    } else {
        forecast.getOrNull(selectedDayIndex - 1)?.temperatureAtHour(hour)
    }

    return TemperatureComparison(selectedDayTemperature, previousDayTemperature)
}

private fun ForecastDay.temperatureAtHour(hour: Int): Double? {
    return hourly.firstOrNull { forecast ->
        forecast.time.substringBefore(":").toIntOrNull() == hour
    }?.temp
}
