package com.weatherinsights.daily.forecast.live.radar

import com.weatherinsights.daily.forecast.live.radar.data.location.LocationData
import com.weatherinsights.daily.forecast.live.radar.data.location.LocationTracker
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoCurrent
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoDaily
import com.weatherinsights.daily.forecast.live.radar.data.model.ForecastDay
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoHourly
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoHistoricalResponse
import com.weatherinsights.daily.forecast.live.radar.data.model.OpenMeteoResponse
import com.weatherinsights.daily.forecast.live.radar.data.model.NotificationPreferences
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherData
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherPostPayload
import com.weatherinsights.daily.forecast.live.radar.data.model.WeatherResponse
import com.weatherinsights.daily.forecast.live.radar.data.network.OpenMeteoApiService
import com.weatherinsights.daily.forecast.live.radar.data.network.WeatherApiService
import com.weatherinsights.daily.forecast.live.radar.data.datasource.WeatherLocalSource
import com.weatherinsights.daily.forecast.live.radar.data.repository.WeatherRepository
import com.weatherinsights.daily.forecast.live.radar.ui.viewmodel.WeatherUiState
import com.weatherinsights.daily.forecast.live.radar.ui.viewmodel.WeatherViewModel
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
        var welcomeCompleted = true

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

    class FakeWeatherApiService : WeatherApiService {
        var getResponse: () -> Response<WeatherResponse> = {
            Response.success(WeatherResponse(success = true))
        }
        override suspend fun getWeather(latitude: Double?, longitude: Double?): Response<WeatherResponse> {
            return getResponse()
        }
        override suspend fun uploadMeteoData(payload: WeatherPostPayload): Response<WeatherResponse> {
            return Response.success(WeatherResponse(success = true))
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
        var yesterdayStartDate: String? = null

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

        override suspend fun getYesterdayForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            startDate: String,
            endDate: String,
            timezone: String
        ): Response<OpenMeteoHistoricalResponse> {
            yesterdayStartDate = startDate
            return Response.success(OpenMeteoHistoricalResponse())
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
    fun testViewModelInit_PermissionDenied_FallsBackToIpLocation_Success() = runTest {
        val dummyData = WeatherData("IP Resolved City", 40.0, -74.0, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationPermissionGranted = false
            locationResult = null
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
    fun testViewModelInit_PermissionDenied_FallsBackToIpLocation_Failure_TransitionsToError() = runTest {
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationPermissionGranted = false
            locationResult = null
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
    fun testViewModelInit_LocationNull_FallsBackToIpLocation_Success() = runTest {
        val dummyData = WeatherData("IP Resolved City", 40.0, -74.0, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationPermissionGranted = true
            locationResult = null
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
    fun testViewModelRefresh_FailureKeepsWeatherAndExposesSnackbarMessage() = runTest {
        val cachedWeather = WeatherData("Ankara", 39.93, 32.85, emptyList())
        val fakeLocalSource = FakeWeatherLocalSource().apply {
            this.cachedWeather = cachedWeather
        }
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationResult = LocationData(39.93, 32.85)
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService().apply {
            getForecastResponse = {
                Response.error(500, "Server Error".toResponseBody())
            }
        }
        val repository = WeatherRepository(
            FakeWeatherApiService(),
            fakeOpenMeteoApi,
            fakeLocalSource
        )
        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(cachedWeather, (viewModel.uiState.value as WeatherUiState.Success).weatherData)
        assertEquals(
            "Couldn't refresh weather. Showing saved data.",
            viewModel.refreshError.value
        )
        assertTrue(!viewModel.isRefreshing.value)

        viewModel.consumeRefreshError()
        assertEquals(null, viewModel.refreshError.value)
    }

    @Test
    fun testYesterdayTemperature_UsesFirstForecastDayInsteadOfDeviceDate() = runTest {
        val weather = WeatherData(
            locationName = "Ankara",
            lat = 39.93,
            lon = 32.85,
            forecast = listOf(
                ForecastDay(
                    date = "2026-08-21",
                    temp = 30.0,
                    humidity = 20,
                    windSpeed = 5.0,
                    uvIndex = 7.0,
                    weatherCode = 0,
                    hourly = emptyList()
                )
            )
        )
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = weather))
            }
        }
        val fakeOpenMeteoApi = FakeOpenMeteoApiService()
        val localSource = FakeWeatherLocalSource()
        val repository = WeatherRepository(fakeWeatherApi, fakeOpenMeteoApi, localSource)

        WeatherViewModel(repository, FakeLocationTracker(), localSource)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("2026-08-20", fakeOpenMeteoApi.yesterdayStartDate)
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

    @Test
    fun testViewModel_WelcomeOnboarding_OptInNotifications() = runTest {
        val fakeLocationTracker = FakeLocationTracker()
        val dummyData = WeatherData("Ankara", 39.93, 32.85, emptyList())
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = { Response.success(WeatherResponse(success = true, weather = dummyData)) }
        }
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        val fakeLocalSource = FakeWeatherLocalSource().apply {
            welcomeCompleted = false
        }

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)
        testDispatcher.scheduler.advanceUntilIdle()

        // At init, welcomeCompleted should be false and weather data should not be fetched (loading state remains)
        assertEquals(false, viewModel.isWelcomeCompleted.value)
        assertTrue(viewModel.uiState.value is WeatherUiState.Loading)

        // Complete welcome with notification opt-in
        viewModel.completeWelcome(notificationsEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Welcome should be completed
        assertEquals(true, viewModel.isWelcomeCompleted.value)
        assertEquals(true, fakeLocalSource.welcomeCompleted)

        // All notification prefs should be true
        val prefs = fakeLocalSource.getNotificationPreferences()
        assertTrue(prefs.criticalAlertsEnabled)
        assertTrue(prefs.morningReportEnabled)
        assertTrue(prefs.eveningReportEnabled)
        assertTrue(prefs.weekendSummaryEnabled)
        assertTrue(prefs.tempShockEnabled)

        // Weather should be fetched
        assertTrue(viewModel.uiState.value is WeatherUiState.Success)
        assertEquals(dummyData, (viewModel.uiState.value as WeatherUiState.Success).weatherData)
    }

    @Test
    fun testViewModel_WelcomeOnboarding_OptOutNotifications() = runTest {
        val fakeLocationTracker = FakeLocationTracker()
        val repository = WeatherRepository(FakeWeatherApiService(), FakeOpenMeteoApiService(), FakeWeatherLocalSource())
        val fakeLocalSource = FakeWeatherLocalSource().apply {
            welcomeCompleted = false
        }

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)
        testDispatcher.scheduler.advanceUntilIdle()

        // Complete welcome with notifications disabled
        viewModel.completeWelcome(notificationsEnabled = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // All notification prefs should be false
        val prefs = fakeLocalSource.getNotificationPreferences()
        assertTrue(!prefs.criticalAlertsEnabled)
        assertTrue(!prefs.morningReportEnabled)
        assertTrue(!prefs.eveningReportEnabled)
        assertTrue(!prefs.weekendSummaryEnabled)
        assertTrue(!prefs.tempShockEnabled)
    }

    @Test
    fun testViewModelInit_LocationNameGeneric_TriggersBackgroundReverseGeocoding() = runTest {
        val dummyData = WeatherData("Current Location", 39.93, 32.85, emptyList())
        val fakeLocationTracker = FakeLocationTracker().apply {
            locationPermissionGranted = false
            locationResult = null
            cityNameResult = "Ankara"
        }
        val fakeWeatherApi = FakeWeatherApiService().apply {
            getResponse = {
                Response.success(WeatherResponse(success = true, weather = dummyData))
            }
        }
        val fakeLocalSource = FakeWeatherLocalSource()
        val repository = WeatherRepository(fakeWeatherApi, FakeOpenMeteoApiService(), fakeLocalSource)

        val viewModel = WeatherViewModel(repository, fakeLocationTracker, fakeLocalSource)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Success)
        val successState = state as WeatherUiState.Success
        assertEquals("Ankara", successState.weatherData.locationName)
        assertTrue(!successState.isYesterdayTemperatureLoading)
        val cached = fakeLocalSource.getCachedWeather()
        assertEquals("Ankara", cached?.locationName)
    }
}
