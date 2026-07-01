package com.weatherinsights

import com.weatherinsights.data.location.LocationData
import com.weatherinsights.data.location.LocationTracker
import com.weatherinsights.data.model.OpenMeteoCurrent
import com.weatherinsights.data.model.OpenMeteoDaily
import com.weatherinsights.data.model.OpenMeteoHourly
import com.weatherinsights.data.model.OpenMeteoResponse
import com.weatherinsights.data.model.NotificationPreferences
import com.weatherinsights.data.model.WeatherData
import com.weatherinsights.data.model.WeatherPostPayload
import com.weatherinsights.data.model.WeatherResponse
import com.weatherinsights.data.network.OpenMeteoApiService
import com.weatherinsights.data.network.WeatherApiService
import com.weatherinsights.data.datasource.WeatherLocalSource
import com.weatherinsights.data.repository.WeatherRepository
import com.weatherinsights.ui.viewmodel.WeatherUiState
import com.weatherinsights.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import android.content.Context
import org.mockito.kotlin.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockContext: Context = mock()

    class FakeLocationTracker : LocationTracker {
        var locationResult: LocationData? = null
        var cityNameResult: String? = null
        var locationPermissionGranted = true
        var lastForceRefreshReceived: Boolean? = null

        override suspend fun getCurrentLocation(forceRefresh: Boolean): LocationData? {
            lastForceRefreshReceived = forceRefresh
            return locationResult
        }
        override suspend fun getCityName(latitude: Double, longitude: Double): String? {
            return cityNameResult
        }
        override fun hasLocationPermission(): Boolean {
            return locationPermissionGranted
        }
    }

    class FakeWeatherLocalSource : WeatherLocalSource {
        var cachedWeather: WeatherData? = null
        private var refreshCount: Int = 0
        private var refreshWindowStart: Long = 0L
        var notificationPrefs = NotificationPreferences()
        private val notificationDates = mutableMapOf<String, String>()

        override suspend fun getCachedWeather(): WeatherData? = cachedWeather
        override suspend fun saveWeatherToCache(data: WeatherData) {
            cachedWeather = data
        }
        override suspend fun getRefreshState(): Pair<Int, Long>? {
            return if (refreshCount == 0 && refreshWindowStart == 0L) null
            else refreshCount to refreshWindowStart
        }
        override suspend fun saveRefreshState(count: Int, windowStart: Long) {
            refreshCount = count
            refreshWindowStart = windowStart
        }

        override suspend fun getNotificationPreferences(): NotificationPreferences = notificationPrefs
        override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {
            notificationPrefs = prefs
        }
        override suspend fun getLastNotificationDate(key: String): String? = notificationDates[key]
        override suspend fun saveLastNotificationDate(key: String, dateString: String) {
            notificationDates[key] = dateString
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
            locationPermissionGranted = false
            locationResult = null
        }
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), FakeWeatherLocalSource())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, FakeWeatherLocalSource())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        assertEquals("Location permission is required to fetch weather information.", (state as WeatherUiState.Error).message)
        assertTrue(state.isPermissionRequired)
    }

    @Test
    fun testViewModelInit_LocationNull_TransitionsToLocationError() = runTest {
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationPermissionGranted = true
            locationResult = null
        }
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), FakeWeatherLocalSource())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, FakeWeatherLocalSource())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        assertEquals("Could not retrieve device location. Please ensure location services are enabled on your device.", (state as WeatherUiState.Error).message)
        assertTrue(!state.isPermissionRequired)
    }

    @Test
    fun testViewModelInit_LocationSuccess_TransitionsToSuccess() = runTest {
        val dummyData = WeatherData("Ankara", 39.93, 32.85, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = dummyData))
            }
        }
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService(), FakeWeatherLocalSource())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, FakeWeatherLocalSource())

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
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService(), FakeWeatherLocalSource())

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, FakeWeatherLocalSource())

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        assertEquals("Worker error: Server Error", (state as WeatherUiState.Error).message)
        assertTrue(!state.isPermissionRequired)
    }

    @Test
    fun testViewModelRefresh_WithinLimit_IncrementsCountAndTriggersFetchWithForceRefresh() = runTest {
        val dummyData = WeatherData("Ankara", 39.93, 32.85, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = dummyData))
            }
        }
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        val fakeLocalSource = FakeWeatherLocalSource()
        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.canRefresh.value)
        viewModel.refresh()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Assert that the tracker received forceRefresh = true
        assertEquals(true, fakeLocationTracker.lastForceRefreshReceived)
        
        // State should be success
        assertTrue(viewModel.uiState.value is WeatherUiState.Success)
        
        // Persisted state check
        val refreshState = fakeLocalSource.getRefreshState()
        assertEquals(1, refreshState?.first)
    }

    @Test
    fun testViewModelRefresh_HitLimit_PreventsFurtherRefreshes() = runTest {
        val dummyData = WeatherData("Ankara", 39.93, 32.85, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = dummyData))
            }
        }
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        val fakeLocalSource = FakeWeatherLocalSource()
        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)

        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger 3 refreshes
        assertTrue(viewModel.canRefresh.value)
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(viewModel.canRefresh.value)
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(viewModel.canRefresh.value)
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // After 3 refreshes, canRefresh should be false
        assertTrue(!viewModel.canRefresh.value)
        
        // Try a 4th refresh
        fakeLocationTracker.lastForceRefreshReceived = null
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Tracker shouldn't have been called on 4th refresh
        assertEquals(null, fakeLocationTracker.lastForceRefreshReceived)
    }

    @Test
    fun testViewModelRefresh_RestoreActiveWindow_KeepsCounter() = runTest {
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        
        val fakeLocalSource = FakeWeatherLocalSource()
        // Save state: 2 refreshes, window started 1 minute ago
        fakeLocalSource.saveRefreshState(2, System.currentTimeMillis() - 60_000)
        
        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // It should start with canRefresh = true (since 2 < 3)
        assertTrue(viewModel.canRefresh.value)
        
        // Do 1 refresh -> counter reaches 3 -> canRefresh becomes false
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(!viewModel.canRefresh.value)
    }

    @Test
    fun testViewModelRefresh_RestoreExpiredWindow_ResetsCounter() = runTest {
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        
        val fakeLocalSource = FakeWeatherLocalSource()
        // Save state: 3 refreshes, window started 20 minutes ago (expired)
        fakeLocalSource.saveRefreshState(3, System.currentTimeMillis() - 20 * 60_000)
        
        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Even though count was 3, the window expired, so it resets, and we can refresh
        assertTrue(viewModel.canRefresh.value)
    }

    @Test
    fun testViewModel_NotificationPreferences_Flow() = runTest {
        val fakeLocationTracker = FakeLocationTracker()
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        val fakeLocalSource = FakeWeatherLocalSource()
        
        val initialPrefs = NotificationPreferences(
            criticalAlertsEnabled = true,
            morningReportEnabled = false,
            morningReportTime = "09:00"
        )
        fakeLocalSource.saveNotificationPreferences(initialPrefs)
        
        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Expose preferences and assert loaded correctly
        assertEquals(initialPrefs, viewModel.notificationPreferences.value)
        
        // Modify preferences and save
        val updatedPrefs = initialPrefs.copy(morningReportEnabled = true, morningReportTime = "10:30")
        viewModel.updateNotificationPreferences(updatedPrefs)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(updatedPrefs, viewModel.notificationPreferences.value)
        assertEquals(updatedPrefs, fakeLocalSource.getNotificationPreferences())
    }
}
