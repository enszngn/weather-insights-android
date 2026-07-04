package com.weatherinsights.daily.forecast.live.radar.data.network

import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherPostPayload
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WeatherApiService {
    @GET("api/weather")
    suspend fun getWeather(
        @Query("lat") latitude: Double? = null,
        @Query("lon") longitude: Double? = null
    ): Response<WeatherResponse>
 
    @POST("api/weather")
    suspend fun uploadMeteoData(
        @Body payload: WeatherPostPayload
    ): Response<WeatherResponse>
}
