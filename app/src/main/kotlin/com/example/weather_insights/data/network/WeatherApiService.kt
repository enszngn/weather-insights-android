package com.example.weather_insights.data.network

import com.example.weather_insights.data.model.WeatherPostPayload
import com.example.weather_insights.data.model.WeatherResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WeatherApiService {
    @GET("api/mobile/weather")
    suspend fun getWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): Response<WeatherResponse>

    @POST("api/mobile/weather")
    suspend fun uploadMeteoData(
        @Body payload: WeatherPostPayload
    ): Response<WeatherResponse>
}
