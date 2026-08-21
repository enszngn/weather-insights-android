package com.weatherinsights.daily.forecast.live.radar

import com.weatherinsights.daily.forecast.live.radar.data.model.ForecastDay
import com.weatherinsights.daily.forecast.live.radar.data.model.HourlyForecast
import com.weatherinsights.daily.forecast.live.radar.ui.util.buildTemperatureComparison
import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureComparisonUtilTest {

    @Test
    fun futureDayComparisonUsesSameHourFromPreviousDay() {
        val forecast = listOf(
            forecastDay("2026-08-21", midnightTemp = 1.0, afternoonTemp = 10.0),
            forecastDay("2026-08-22", midnightTemp = 99.0, afternoonTemp = 15.0)
        )

        val comparison = buildTemperatureComparison(
            forecast = forecast,
            selectedDayIndex = 1,
            hour = 14,
            yesterdayHourlyTemperatures = null
        )

        assertEquals(15.0, comparison.selectedDayTemperature ?: 0.0, 0.001)
        assertEquals(10.0, comparison.previousDayTemperature ?: 0.0, 0.001)
    }

    @Test
    fun currentDayComparisonUsesSameHourFromHistoricalArray() {
        val forecast = listOf(
            forecastDay("2026-08-21", midnightTemp = 1.0, afternoonTemp = 10.0)
        )
        val historicalTemperatures = List(24) { hour -> hour.toDouble() }

        val comparison = buildTemperatureComparison(
            forecast = forecast,
            selectedDayIndex = 0,
            hour = 14,
            yesterdayHourlyTemperatures = historicalTemperatures
        )

        assertEquals(10.0, comparison.selectedDayTemperature ?: 0.0, 0.001)
        assertEquals(14.0, comparison.previousDayTemperature ?: 0.0, 0.001)
    }

    private fun forecastDay(
        date: String,
        midnightTemp: Double,
        afternoonTemp: Double
    ): ForecastDay {
        return ForecastDay(
            date = date,
            temp = 0.0,
            humidity = 0,
            windSpeed = 0.0,
            uvIndex = 0.0,
            weatherCode = 0,
            hourly = listOf(
                hourlyForecast("00:00", midnightTemp),
                hourlyForecast("14:00", afternoonTemp)
            )
        )
    }

    private fun hourlyForecast(time: String, temperature: Double): HourlyForecast {
        return HourlyForecast(
            time = time,
            temp = temperature,
            humidity = 0,
            windSpeed = 0.0,
            weatherCode = 0
        )
    }
}
