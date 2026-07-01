package com.example.weather_insights.data.mapper

import com.example.weather_insights.data.model.ForecastDay
import com.example.weather_insights.data.model.HourlyForecast
import com.example.weather_insights.data.model.OpenMeteoResponse
import com.example.weather_insights.data.model.WeatherData

/**
 * Maps a raw Open-Meteo API response to the app's internal [WeatherData] model.
 *
 * For day 0, current conditions are taken from the `current` block.
 * For subsequent days, daily aggregates are computed from the hourly data.
 */
fun OpenMeteoResponse.toWeatherData(): WeatherData {
    val forecastDays = daily.time.mapIndexed { i, dateStr ->
        val startIndex = i * 24
        val dayHourly = mutableListOf<HourlyForecast>()
        for (h in 0..23) {
            val idx = startIndex + h
            if (idx < hourly.time.size) {
                val hourlyTime = hourly.time[idx].substringAfter('T').take(5)
                dayHourly.add(
                    HourlyForecast(
                        time = hourlyTime,
                        temp = hourly.temperature2m.getOrNull(idx) ?: 0.0,
                        humidity = hourly.relativeHumidity2m.getOrNull(idx) ?: 0,
                        windSpeed = hourly.windSpeed10m.getOrNull(idx) ?: 0.0,
                        weatherCode = hourly.weatherCode.getOrNull(idx) ?: 0
                    )
                )
            }
        }

        val dailyTemp = if (i == 0) current.temperature2m
            else (dayHourly.map { it.temp }.average().takeIf { !it.isNaN() } ?: 0.0)
        val dailyHumidity = if (i == 0) current.relativeHumidity2m
            else (dayHourly.map { it.humidity }.average().takeIf { !it.isNaN() }?.toInt() ?: 0)
        val dailyWindSpeed = if (i == 0) current.windSpeed10m
            else (dayHourly.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0)
        val dailyWeatherCode = if (i == 0) current.weatherCode
            else (dayHourly.firstOrNull()?.weatherCode ?: 0)

        val sunriseTime = daily.sunrise?.getOrNull(i)?.substringAfter('T')?.take(5) ?: "06:00"
        val sunsetTime = daily.sunset?.getOrNull(i)?.substringAfter('T')?.take(5) ?: "20:00"

        ForecastDay(
            date = dateStr,
            temp = dailyTemp,
            humidity = dailyHumidity,
            windSpeed = dailyWindSpeed,
            uvIndex = daily.uvIndexMax.getOrNull(i) ?: 0.0,
            weatherCode = dailyWeatherCode,
            hourly = dayHourly,
            sunrise = sunriseTime,
            sunset = sunsetTime
        )
    }

    return WeatherData(
        locationName = "Current Location",
        lat = latitude,
        lon = longitude,
        forecast = forecastDays
    )
}
