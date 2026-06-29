package com.example.weather_insights

import com.example.weather_insights.data.location.LocationData
import com.example.weather_insights.data.location.LocationTracker
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
import com.example.weather_insights.ui.viewmodel.WeatherUiState
import com.example.weather_insights.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    class FakeLocationTracker : LocationTracker {
        var locationResult: LocationData? = null
        override suspend fun getCurrentLocation(): LocationData? {
            return locationResult
        }
    }

    class FakeWeatherApiService : WeatherApiService {
        var getResponse: () -> Response<WeatherResponse> = {
            Response.success(WeatherResponse(success = true))
        }
        override suspend fun getWeather(latitude: Double, longitude: Double): Response<WeatherResponse> {
            return getResponse()
        }
        override suspend fun uploadMeteoData(payload: WeatherPostPayload): Response<WeatherResponse> {
            return Response.success(WeatherResponse(success = true))
        }
    }

    class FakeOpenMeteoApiService : OpenMeteoApiService {
        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            timezone: String,
            forecastDays: Int
        ): Response<OpenMeteoResponse> {
            return Response.success(
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
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModelInit_PermissionDenied_TransitionsToPermissionError() = runTest {
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = null
        }
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        assertEquals("Location permission is required to fetch weather information.", (state as WeatherUiState.Error).message)
        assertTrue(state.isPermissionRequired)
    }

    @Test
    fun testViewModelInit_LocationSuccess_TransitionsToSuccess() = runTest {
        val dummyData = WeatherData("Ankara", 39.93, 32.85, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85, "Ankara")
        }
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = dummyData))
            }
        }
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Success)
        assertEquals(dummyData, (state as WeatherUiState.Success).weatherData)
    }

    @Test
    fun testViewModelInit_LocationSuccess_FetchError_TransitionsToError() = runTest {
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.error(500, "Server Error".toResponseBody())
            }
        }
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        assertEquals("Worker error: Server Error", (state as WeatherUiState.Error).message)
        assertTrue(!state.isPermissionRequired)
    }
}
