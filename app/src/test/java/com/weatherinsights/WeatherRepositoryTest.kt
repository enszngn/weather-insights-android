package com.weatherinsights

import com.weatherinsights.data.model.OpenMeteoCurrent
import com.weatherinsights.data.model.OpenMeteoDaily
import com.weatherinsights.data.model.OpenMeteoHourly
import com.weatherinsights.data.model.OpenMeteoResponse
import com.weatherinsights.data.model.WeatherData
import com.weatherinsights.data.model.WeatherPostPayload
import com.weatherinsights.data.model.WeatherResponse
import com.weatherinsights.data.network.OpenMeteoApiService
import com.weatherinsights.data.network.WeatherApiService
import com.weatherinsights.data.datasource.WeatherLocalSource
import com.weatherinsights.data.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

import com.weatherinsights.data.model.NotificationPreferences

class WeatherRepositoryTest {

    class FakeWeatherLocalSource : WeatherLocalSource {
        var cachedWeather: WeatherData? = null
        var notificationPrefs = NotificationPreferences()
        private val notificationDates = mutableMapOf<String, String>()

        override suspend fun getCachedWeather(): WeatherData? = cachedWeather
        override suspend fun saveWeatherToCache(data: WeatherData) {
            cachedWeather = data
        }
        override suspend fun getRefreshState(): Pair<Int, Long>? = null
        override suspend fun saveRefreshState(count: Int, windowStart: Long) { /* no-op in tests */ }

        override suspend fun getNotificationPreferences(): NotificationPreferences = notificationPrefs
        override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
            notificationPrefs = prefs
        }
        override suspend fun getLastNotificationDate(key: String): String? = notificationDates[key]
        override suspend fun saveLastNotificationDate(key: String, dateString: String) {
            notificationDates[key] = dateString
        }
    }

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

        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi, FakeWeatherLocalSource())
        val result = repository.fetchWeather(52.52, 13.41).first()

        assertTrue(result.isSuccess)
        assertEquals(fakeWeather, result.getOrNull())
    }

    @Test
    fun testFetchWeather_CacheMiss_SuccessFallback() = runBlocking {
        val fakeMeteo = createDummyMeteoResponse()

        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.error(404, "Not Found".toResponseBody())
            }
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getForecastResponse = {
                Response.success(fakeMeteo)
            }
        }

        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi, FakeWeatherLocalSource())
        val result = repository.fetchWeather(52.52, 13.41).first()

        assertTrue(result.isSuccess)
        val emittedWeather = result.getOrNull()
        org.junit.Assert.assertNotNull(emittedWeather)
        assertEquals("Current Location", emittedWeather?.locationName)
        assertEquals(52.52, emittedWeather?.lat ?: 0.0, 0.001)
        assertEquals(13.41, emittedWeather?.lon ?: 0.0, 0.001)

        // Give background coroutine time to execute the POST request
        kotlinx.coroutines.delay(200)
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

        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi, FakeWeatherLocalSource())
        val result = repository.fetchWeather(52.52, 13.41).first()

        assertTrue(result.isFailure)
    }
}
