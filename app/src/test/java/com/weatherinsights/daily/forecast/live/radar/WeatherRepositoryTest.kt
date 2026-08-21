package com.weatherinsights.daily.forecast.live.radar

import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoCurrent
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoDaily
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoHourly
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoHistoricalHourly
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoHistoricalResponse
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoResponse
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherPostPayload
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherResponse
import com.weatherinsights.daily.forecast.live.radar.data.network.OpenMeteoApiService
import com.weatherinsights.daily.forecast.live.radar.data.network.WeatherApiService
import com.weatherinsights.daily.forecast.live.radar.data.datasource.WeatherLocalSource
import com.weatherinsights.daily.forecast.live.radar.data.repository.WeatherRepository
import com.weatherinsights.daily.forecast.live.radar.data.mapper.toWeatherData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

import com.weatherinsights.daily.forecast.live.radar.data.model.NotificationPreferences

class WeatherRepositoryTest {

    class FakeWeatherLocalSource : WeatherLocalSource {
        var cachedWeather: WeatherData? = null
        var notificationPrefs = NotificationPreferences()
        private val notificationDates = mutableMapOf<String, String>()
        var welcomeCompleted = true

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
        override suspend fun isWelcomeCompleted(): Boolean = welcomeCompleted
        override suspend fun setWelcomeCompleted(completed: Boolean) {
            welcomeCompleted = completed
        }

        var yesterdayTempDate: String? = null
        var yesterdayTempValues: List<Double>? = null

        override suspend fun getYesterdayHourlyTemps(dateString: String): List<Double>? {
            return if (yesterdayTempDate == dateString) yesterdayTempValues else null
        }
        override suspend fun saveYesterdayHourlyTemps(dateString: String, temps: List<Double>) {
            yesterdayTempDate = dateString
            yesterdayTempValues = temps
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
        var getCallCount: Int = 0

        override suspend fun getWeather(latitude: Double?, longitude: Double?): Response<WeatherResponse> {
            getCallCount++
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
        var getYesterdayForecastResponse: () -> Response<OpenMeteoHistoricalResponse> = {
            Response.success(OpenMeteoHistoricalResponse())
        }
        var getForecastCallCount: Int = 0
        var getYesterdayForecastCallCount: Int = 0

        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            timezone: String,
            forecastDays: Int
        ): Response<OpenMeteoResponse> {
            getForecastCallCount++
            return getForecastResponse()
        }

        override suspend fun getYesterdayForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            startDate: String,
            endDate: String,
            timezone: String
        ): Response<OpenMeteoHistoricalResponse> {
            getYesterdayForecastCallCount++
            return getYesterdayForecastResponse()
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
    fun testFetchWeather_ForceRefresh_BypassesWorkerAndAddsFreshnessTimestamp() = runBlocking {
        val fakeWeatherApi = FakeWeatherApiService()
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getForecastResponse = { Response.success(createDummyMeteoResponse()) }
        }
        val localSource = FakeWeatherLocalSource()
        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi, localSource)

        val beforeFetch = System.currentTimeMillis()
        val result = repository.fetchWeather(
            lat = 52.52,
            lon = 13.41,
            locationName = "Test City",
            forceRefresh = true
        ).first()

        assertTrue(result.isSuccess)
        assertEquals(0, fakeWeatherApi.getCallCount)
        assertEquals(1, fakeOpenMeteoApi.getForecastCallCount)
        assertTrue((result.getOrNull()?.fetchedAtEpochMs ?: 0L) >= beforeFetch)
        assertEquals(result.getOrNull(), localSource.cachedWeather)
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

    @Test
    fun testOpenMeteoMapper_MapsPrecipitationProbabilityCorrectly() {
        val fakeHourly = OpenMeteoHourly(
            time = listOf("2026-06-29T12:00"),
            temperature2m = listOf(20.0),
            relativeHumidity2m = listOf(50),
            windSpeed10m = listOf(10.0),
            weatherCode = listOf(0),
            precipitationProbability = listOf(75)
        )
        val rawResponse = OpenMeteoResponse(
            latitude = 52.52,
            longitude = 13.41,
            generationTimeMs = 0.1,
            utcOffsetSeconds = 0,
            timezone = "UTC",
            timezoneAbbreviation = "UTC",
            elevation = 10.0,
            current = OpenMeteoCurrent("2026-06-29T12:00", 900, 20.0, 50, 10.0, 0),
            hourly = fakeHourly,
            daily = OpenMeteoDaily(listOf("2026-06-29"), listOf(5.0))
        )

        val mapped = rawResponse.toWeatherData()
        val firstDay = mapped.forecast.firstOrNull()
        org.junit.Assert.assertNotNull(firstDay)
        val firstHour = firstDay?.hourly?.firstOrNull()
        org.junit.Assert.assertNotNull(firstHour)
        assertEquals(75, firstHour?.precipitationProbability)
    }

    @Test
    fun testGetYesterdayTemperature_CacheHit() = runBlocking {
        val localSource = FakeWeatherLocalSource().apply {
            yesterdayTempDate = "2026-07-12"
            yesterdayTempValues = List(24) { 24.5 }
        }
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), localSource)
        val result = repository.getYesterdayTemperature(52.52, 13.41, "2026-07-12")
        assertEquals(24.5, result?.firstOrNull() ?: 0.0, 0.001)
    }

    @Test
    fun testGetYesterdayTemperature_CacheMiss_NetworkSuccess() = runBlocking {
        val localSource = FakeWeatherLocalSource()
        val fakeMeteo = OpenMeteoHistoricalResponse(
            hourly = OpenMeteoHistoricalHourly(
                time = List(24) { "2026-07-12T$it:00" },
                temperature2m = List(24) { 20.0 }
            )
        )
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getYesterdayForecastResponse = {
                Response.success(fakeMeteo)
            }
        }
        val repository = WeatherRepository(FakeWeatherApiService(), fakeOpenMeteoApi, localSource)
        val result = repository.getYesterdayTemperature(52.52, 13.41, "2026-07-12")

        assertEquals(20.0, result?.firstOrNull() ?: 0.0, 0.001)
        assertEquals("2026-07-12", localSource.yesterdayTempDate)
        assertEquals(20.0, localSource.yesterdayTempValues?.firstOrNull() ?: 0.0, 0.001)
    }

    @Test
    fun testGetYesterdayTemperature_InvalidCacheAndIncompleteNetworkDataReturnsNull() = runBlocking {
        val localSource = FakeWeatherLocalSource().apply {
            yesterdayTempDate = "2026-07-12"
            yesterdayTempValues = listOf(99.0)
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getYesterdayForecastResponse = {
                Response.success(
                    OpenMeteoHistoricalResponse(
                        hourly = OpenMeteoHistoricalHourly(
                            time = List(23) { "2026-07-12T$it:00" },
                            temperature2m = List(23) { 20.0 }
                        )
                    )
                )
            }
        }
        val repository = WeatherRepository(FakeWeatherApiService(), fakeOpenMeteoApi, localSource)

        val result = repository.getYesterdayTemperature(52.52, 13.41, "2026-07-12")

        assertEquals(null, result)
        assertEquals(1, fakeOpenMeteoApi.getYesterdayForecastCallCount)
        assertEquals(listOf(99.0), localSource.yesterdayTempValues)
    }
}
