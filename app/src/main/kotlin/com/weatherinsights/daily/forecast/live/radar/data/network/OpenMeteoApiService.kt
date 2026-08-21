package com.weatherinsights.daily.forecast.live.radar.data.network

import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoResponse
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoHistoricalResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "uv_index_max,sunrise,sunset",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 8
    ): Response<OpenMeteoResponse>

    @GET("v1/forecast")
    suspend fun getYesterdayForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m",
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("timezone") timezone: String = "auto"
    ): Response<OpenMeteoHistoricalResponse>
}
