package com.example.weather_insights

import com.example.weather_insights.data.model.OpenMeteoCurrent
import com.example.weather_insights.data.model.OpenMeteoDaily
import com.example.weather_insights.data.model.OpenMeteoHourly
import com.example.weather_insights.data.model.OpenMeteoResponse
import com.example.weather_insights.data.model.WeatherData
import com.example.weather_insights.data.model.WeatherPostPayload
import com.example.weather_insights.data.model.WeatherResponse
import com.example.weather_insights.data.network.OpenMeteoApiService
import com.example.weather_insights.data.network.WeatherApiService
import com.example.weather_insights.data.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class WeatherRepositoryTest {

    private fun createDummyWeatherData() = WeatherData(
        locationName = "Test City",
        lat = 52.52,
        lon = 13.41,
        forecast = emptyList()
    )

    private fun createDummyMeteoResponse() = OpenMeteoResponse(
        latitude = 52.52,
        longitude = 13.41,
        generationTimeMs = 0.1,
        utcOffsetSeconds = 0,
        timezone = "UTC",
        timezoneAbbreviation = "UTC",
        elevation = 10.0,
        current = OpenMeteoCurrent("2026-06-29T12:00", 900, 20.0, 50, 10.0, 0),
        hourly = OpenMeteoHourly(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        daily = OpenMeteoDaily(emptyList(), emptyList())
    )

    class FakeWeatherApiService : WeatherApiService {
        var getResponse: () -> Response<WeatherResponse> = {
            Response.success(WeatherResponse(success = true))
        }
        var postResponse: () -> Response<WeatherResponse> = {
            Response.success(WeatherResponse(success = true))
        }

        var lastPostPayload: WeatherPostPayload? = null

        override suspend fun getWeather(latitude: Double, longitude: Double): Response<WeatherResponse> {
            return getResponse()
        }

        override suspend fun uploadMeteoData(payload: WeatherPostPayload): Response<WeatherResponse> {
            lastPostPayload = payload
            return postResponse()
        }
    }

    class FakeOpenMeteoApiService : OpenMeteoApiService {
        var getForecastResponse: () -> Response<OpenMeteoResponse> = {
            Response.success(
                OpenMeteoResponse(
                    latitude = 0.0,
                    longitude = 0.0,
                    generationTimeMs = 0.0,
                    utcOffsetSeconds = 0,
                    timezone = "",
                    timezoneAbbreviation = "",
                    elevation = 0.0,
                    current = OpenMeteoCurrent("", 0, 0.0, 0, 0.0, 0),
                    hourly = OpenMeteoHourly(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
                    daily = OpenMeteoDaily(emptyList(), emptyList())
                )
            )
        }

        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            timezone: String,
            forecastDays: Int
        ): Response<OpenMeteoResponse> {
            return getForecastResponse()
        }
    }

    @Test
    fun testFetchWeather_CacheHit() = runBlocking {
        val fakeWeather = createDummyWeatherData()
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = fakeWeather))
            }
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService()

        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi)
        val result = repository.fetchWeather(52.52, 13.41).first()

        assertTrue(result.isSuccess)
        assertEquals(fakeWeather, result.getOrNull())
    }

    @Test
    fun testFetchWeather_CacheMiss_SuccessFallback() = runBlocking {
        val fakeWeather = createDummyWeatherData()
        val fakeMeteo = createDummyMeteoResponse()

        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.error(404, "Not Found".toResponseBody())
            }
            postResponse = {
                Response.success(WeatherResponse(success = true, weather = fakeWeather))
            }
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getForecastResponse = {
                Response.success(fakeMeteo)
            }
        }

        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi)
        val result = repository.fetchWeather(52.52, 13.41).first()

        assertTrue(result.isSuccess)
        assertEquals(fakeWeather, result.getOrNull())
        assertEquals(fakeMeteo, fakeWeatherApi.lastPostPayload?.meteoData)
    }

    @Test
    fun testFetchWeather_CacheMiss_MeteoError() = runBlocking {
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.error(404, "Not Found".toResponseBody())
            }
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getForecastResponse = {
                Response.error(500, "Server Error".toResponseBody())
            }
        }

        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi)
        val result = repository.fetchWeather(52.52, 13.41).first()

        assertTrue(result.isFailure)
    }
}
