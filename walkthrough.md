# Project Walkthrough

Historical log of major changes. One line per change; see `task.md` for the checklist.

## Phase 0: Setup
- Created `agents.md`, `project.md`, `README.md`, `task.md`, `walkthrough.md`.
- Package structure under `app/src/main/kotlin/com/weatherinsights/`:
  - `data/{model,network,repository,datasource,location,mapper}`
  - `ui/{components,screens,theme,util,viewmodel}`
  - `worker/`, `receiver/`, `di/`

## Phase 1.1: Dependency Configuration
- `gradle/libs.versions.toml`: Kotlin 2.0.21, Hilt 2.60, Retrofit 2.11.0, Compose BOM 2024.10.00.
- Root + app `build.gradle.kts`: KSP, Hilt, Serialization, Compose compiler plugins.
- JVM target Java 11 via `kotlin { compilerOptions { jvmTarget } }` (AGP 9.0+).
- `gradle.properties`: `android.disallowKotlinSourceSets=false` (KSP source set fix).
- `AndroidManifest.xml`: Internet permission, `WeatherApplication` (@HiltAndroidApp), `MainActivity` (@AndroidEntryPoint).

## Phase 1.3 & 2: Network & Data Layer
- Models: `data/model/WeatherModels.kt`, `OpenMeteoModels.kt`, `WeatherPostPayload.kt`.
- API: `data/network/WeatherApiService.kt`, `OpenMeteoApiService.kt`.
- DI: `di/NetworkModule.kt` provides JSON parser, OkHttpClient, two Retrofit clients.
- Repository: `data/repository/WeatherRepository.kt` (worker-first caching + fallback).
- Tests: `WeatherRepositoryTest.kt` (API fakes, cache hit/miss/upload/fallback).

## Phase 3: Logic & State
- Location: Google Play Services FusedLocationProviderClient dep added.
- `data/location/LocationTracker.kt` (interface + coords model), `DefaultLocationTracker.kt` (cancellable coroutine wrapper for Google tasks).
- `di/LocationModule.kt` binds `LocationTracker` + provides `FusedLocationProviderClient`.
- `ui/viewmodel/WeatherUiState.kt`: sealed interface (Loading/Success/Error).
- `ui/viewmodel/WeatherViewModel.kt`: location query + state mapping; permission-denied → error state.
- Tests: `WeatherViewModelTest.kt` (permission errors, success/failure flows).

## Phase 1.2 & 4: Glassmorphic UI
- Theme: `ui/theme/{Color,Type,Theme}.kt`.
- `ui/components/WeatherMapper.kt`: weather code → icon/description/gradient.
- `ui/components/GlassyPanel.kt`: custom glass card.
- `ui/screens/HomeScreen.kt`: vertical weights (20/60/20), dynamic gradient background, 6-hour timeline, loading/permission prompts.
- `MainActivity.kt`: binds ViewModel, runtime fine/coarse permission requests, renders HomeScreen.

## Sunset Integration & UI Redesign
- Added `sunrise`/`sunset` to `WeatherModels.kt` + `OpenMeteoModels.kt`; default `uv_index_max,sunrise,sunset` param in `OpenMeteoApiService.kt`.
- `WeatherMapper.mapCodeToEmoji(isNight)` for night icons.
- HomeScreen overhaul: 44.sp city name, chronological current-hour filtering, Sunset row insertion, 24-hour timeline, time-of-day gradient interpolation (#009AFF midday → #001533 midnight).
- `LocationTracker`: live GPS via `getCurrentLocation` + `PRIORITY_HIGH_ACCURACY`; client-side `Geocoder` reverse-geocoding for city name.

## Phase 5.3: Launch Latency
- `DefaultLocationTracker.kt`: fast fallback — cached `lastLocation` if <15 min old, else `PRIORITY_BALANCED_POWER_ACCURACY` with 5s timeout.
- `WeatherRepository.kt`: app-lifespan `CoroutineScope` (SupervisorJob + IO); `OpenMeteoResponse.toWeatherData()` client-side mapper; 404 miss path emits localmapped response instantly; Cloudflare upload fire-and-forget on scope.
- Tests updated for async caching/local mapping.

## Phase 5.4: Parallel Geocoding & DataStore
- Dep: `androidx.datastore:datastore-preferences`.
- `data/datasource/WeatherLocalSource.kt` + `DataStoreWeatherLocalSource` impl; `di/LocalModule.kt` binding.
- `WeatherRepository.kt`: injected `WeatherLocalSource` (Context decoupled); saves to cache on success.
- `DefaultLocationTracker.kt`: `getCurrentLocation()` returns coords only; `getCityName()` separate.
- `WeatherViewModel.kt`: reads cached weather on init (instant Success); parallel `getCityName()` + `fetchWeather()`.
- Tests updated to mock local source + parallel geocoding.

## Phase 6: Redundancy Cleanup & God Module Refactor
- Removed dead `LocationData.cityName`; made `saveWeatherToCache()` private; deleted dead `mapCodeToGradient()`.
- Extracted `hasPermission()` in `DefaultLocationTracker.kt`; extracted `setNonSuccessState()` in `WeatherViewModel.kt` (early-return flattening).
- Splits:
  - `data/model/TimelineEntry.kt` (from HomeScreen).
  - `data/mapper/OpenMeteoMapper.kt` (from Repository).
  - `ui/util/BackgroundColorUtil.kt` (from HomeScreen).
  - `ui/components/{LoadingView,ErrorView,WeatherTimeline}.kt`.
- HomeScreen: 484 → 40 lines (orchestration only). Tests unchanged.

## Bugfix: locationName Always "Çankaya"
- Root cause: POST payload missing `locationName`; geocoding raced with fire-and-forget POST.
- Fix: added `locationName: String?` to `WeatherPostPayload.kt`; threaded through `OpenMeteoMapper.toWeatherData()` + `WeatherRepository.fetchWeather()`; ViewModel now awaits `getCityName()` before `fetchWeather()`.

## Manual Refresh + Rate Limiting
- `WeatherLocalSource.kt`: `getRefreshState()` / `saveRefreshState(count, windowStart)` via DataStore.
- `WeatherViewModel.kt`: injected `WeatherLocalSource`; `canRefresh: StateFlow<Boolean>`; `refresh()` guards `MAX_REFRESHES = 3` per 15-min rolling window; persists count + window start; resets on expiry.
- `WeatherTimeline.kt`: refresh IconButton in header, dimmed at alpha 0.35f when disabled.
- Tests: comprehensive refresh/rate-limit/expired-window coverage; 11 tests pass.

## Material Icons Migration & Styling
- Dep: `compose-material-icons-extended`.
- `WeatherMapper.kt`: `mapCodeToIcon(code, isNight)` + `mapCodeToIconColor(code, isNight)` (yellow sun, grey clouds, light blue moon, dark grey storm).
- `WeatherTimeline.kt`: all emojis → `Icons.Rounded.*`; humidity `WaterDrop`; sunrise/sunset `WbTwilight`; wind icon 54.dp, label removed.
- `GlassyPanel.kt`: default `cornerRadius` 0.dp (sharp corners).
- `ErrorView.kt`: emojis → `Icons.Rounded.{LocationOn,Warning}`.

## Package Rename: com.example.weather_insights → com.weatherinsights
- Moved `app/src/main/kotlin/com/example/weather_insights/` → `com/weatherinsights/` (and test tree).
- Global replace in all Kotlin, Gradle, Manifest.
- `./gradlew clean` to purge stale Hilt/KSP generated code. 11 tests pass.

## App Display Name
- `res/values/strings.xml`: `app_name` → "Weather Insights".

## Customizable Weather Notifications
- Deps/Manifest: WorkManager; `POST_NOTIFICATIONS` permission.
- `data/model/NotificationPreferences.kt`: config switches + time strings.
- `WeatherLocalSource.kt`: serialize/deserialize prefs + last-notification-date tracking.
- `worker/WeatherNotificationWorker.kt`: critical alerts (storm codes 95/96/99, imminent rain) bypass quiet hours; morning report (default 08:00); evening report (default 20:00); weekend summary (Fri 17-18:00); temperature shock (Δ ≥ 10°C); quiet hours silencing.
- `ui/screens/SettingsScreen.kt`: Compose TimePicker, iOS-style wheel pickers, test notification button.
- `WeatherTimeline.kt`: settings gear icon in header.
- `HomeScreen.kt`: routes/toggles SettingsScreen.
- `WeatherViewModel.kt`: exposes + persists preferences.
- `MainActivity.kt`: runtime POST_NOTIFICATIONS request; periodic WorkManager enqueue; immediate channel registration in `onCreate()`; `REPLACE` policy for reschedule on startup.
- Worker dynamic fetch: if no cached weather, uses last location + repository fetch.
- Decoupled `Context`/notifications from `WeatherViewModel.kt` → `sendTestNotification()` moved to `MainActivity.kt`.
- 12 tests pass. All UI + worker strings English.

## Phase 7: Exact Alarm Scheduling
- `AndroidManifest.xml`: `SCHEDULE_EXACT_ALARM` permission; `WeatherNotificationReceiver` registered.
- `receiver/AlarmScheduler.kt`: set/cancel exact wakeup alarms via `AlarmManager`.
- `receiver/WeatherNotificationReceiver.kt`: on alarm → one-shot WorkManager report → schedule next day.
- `worker/WeatherNotificationWorker.kt`: distinguishes direct report triggers (morning/evening only) from periodic runs (critical alerts + caching only).
- `data/model/NotificationPreferences.kt`: removed `healthAlertsEnabled`.
- `ui/screens/SettingsScreen.kt`: removed Health & Allergy Alerts toggle.
- `MainActivity.kt`: dynamic alarm scheduling in preferences-change `LaunchedEffect`.
- Build clean + tests pass.

## Phase 9: Reels-Style Vertical Page Navigation
- `ui/components/WeatherTimeline.kt`: replaced static layout with a `VerticalPager` across `ForecastDay` items. Swiping vertically transitions the entire layout (background + header + timeline + details) per day. Added the day label ("Today", "Friday", etc.) under the city name in smaller semi-transparent font.
- `ui/components/WeatherTimeline.kt`: added vertical dot pager indicators on the right side of the screen using `animateDpAsState` for active height stretching (vertical pill shape) and `animateFloatAsState` for active/inactive opacity.
- `ui/components/WeatherTimeline.kt`: converted the hourly timeline from a vertical `LazyColumn` to a horizontal `LazyRow`, redesigning the timeline items as vertical columns (`HourColumn` and `SolarEventColumn`). Applied a horizontal gradient alpha mask using `drawWithContent` and `BlendMode.DstIn` to make the leftmost and rightmost elements fade out subtly.
- `ui/util/BackgroundColorUtil.kt`: added `getDynamicBackgroundColorForDay` to compute dynamic day/night colors on a per-day basis, using it for the full-bleed page backgrounds.
- Deleted `DailyForecastRow.kt` (safe deletion of redundant code).
- `ui/components/GlassyPanel.kt`: changed default `cornerRadius` parameter from `0.dp` (sharp) to `12.dp` (subtly rounded) to make all card corners throughout the app look more circleish and modern.
- `ui/components/WeatherTimeline.kt`: compacted the hourly weather horizontal timeline by ~40% by reducing the glassy card height from `180.dp` to `130.dp`, vertical spacing from `8.dp` to `4.dp`, item widths from `64.dp` to `52.dp`, horizontal padding from `12.dp` to `6.dp`, and scaling down text sizes (e.g., time to `12.sp`, temp to `15.sp`) and icon size to `24.dp`.
- `ui/components/WeatherTimeline.kt`: shifted the horizontal timeline panel upwards by changing layout weights (timeline weight from `0.6f` to `0.35f` and bottom dashboard weight from `0.2f` to `0.45f`), positioning the card center at 37.5% from the top.
- `app/build.gradle.kts`: updated the app's `versionName` to `1.0.0`.
- `app/build.gradle.kts`: updated the app's `versionName` to `1.1.0` and `versionCode` to `2`.
- `app/build.gradle.kts`: raised the app's `minSdk` to `26`.
- Verified compilation and test suite (all tests pass).

## Bugfix: Android Lint MissingPermission Errors
- Added `@SuppressLint("MissingPermission")` to `getCurrentLocation` in `DefaultLocationTracker.kt` to suppress static compile-time lint warnings since permission checks are dynamically verified inside the function.
- Verified `./gradlew build` compiles and passes all checks.

## IP-Based Location Fallback Implementation
- Updated `WeatherApiService.kt` to call `/api/weather` instead of `/api/mobile/weather` and support nullable query parameters (defaulting to null), allowing Retrofit to omit them.
- Updated `WeatherRepository.kt` to support nullable `Double?` coordinates, parse resolved IP coordinates and location name from the Worker's `404` cache miss error body using Kotlinx Serialization, and use them for the Open-Meteo fallback.
- Refactored `WeatherViewModel.kt` to fetch weather unconditionally even if permissions are denied or GPS is disabled by executing coordinates-free API requests.
- Updated `MainActivity.kt` to run `loadWeather` unconditionally on launcher completion.
- Updated `WeatherViewModelTest.kt` to verify successful IP fallback logic and check all tests pass.

## Welcome Onboarding Page & Worker IP Fallback Fixes
- `data/datasource/WeatherLocalSource.kt`: added `welcome_completed` flag with DataStore persistence.
- `ui/viewmodel/WeatherViewModel.kt`: exposed `isWelcomeCompleted` StateFlow. Added `completeWelcome()` to save user notification preferences (opt-in/opt-out) and set onboarding completed status.
- `ui/screens/WelcomeScreen.kt`: built onboarding flow using a 3-page split design. Page 1 displays app info and Get Started; Page 2 requests location permission or fallback with distinct Continue (grey) and Grant (blue) buttons; Page 3 requests notification/alarm settings similarly.
- `MainActivity.kt`: routed starting screen between loading, onboarding steps, and `HomeScreen`. Configured launchers to advance step indices on permission response. Decoupled exact alarm setting redirects from `onResume` and general syncs, prompting only upon explicit user opt-in (on onboarding page 3 or settings screen toggle) to prevent loops. Added class-level reactive permission state variables (location, notification, alarm) updated during `onResume`.
- `ui/screens/HomeScreen.kt`: threaded location/alarm permission states and redirection trigger functions from `MainActivity` down to `SettingsScreen`.
- `ui/screens/SettingsScreen.kt`: added Location warning card ("Location permissions will make the app more reliable.") at the top with a Settings redirection button. Added Alarm warning card ("Alarm permission is required for these features.") when exact alarms are missing, dynamically greying out and intercepting tap gestures on Routine & Smart notification sections.
- `receiver/AlarmScheduler.kt`: implemented non-exact scheduling fallback (`setAndAllowWhileIdle`) on Android S+ when exact alarm permission is missing.
- `worker/WeatherNotificationWorker.kt`: resolved background worker location lookup on cache miss when location permission is not granted by calling IP location fallback.
- `WeatherViewModelTest.kt` & `WeatherRepositoryTest.kt`: updated stubs and added unit tests for onboarding completion and notification preferences logic. All tests compile and pass.

## Client-Side Geocoding Fallback for Generic Location Names
- **Problem**: When using IP-based location fallback or when client-side geocoding initially fails/times out, the app could display the generic name "Current Location" instead of the resolved city or region name.
- **Fix**:
  - `WeatherRepository.kt`: Modified 404 cache miss block to parse `WorkerErrorResponse` for `resolvedLocationName` even if coordinates were passed, allowing the app to fallback to the IP location name if client-side geocoding failed.
  - `WeatherViewModel.kt`: Added background reverse geocoding inside `onSuccess` block. If `cityName` is `null` but the returned weather location name is generic (`"Current Location"` or blank), it starts a background coroutine to reverse-geocode the returned coordinates, saves the resolved name to the local cache, and updates the UI state.
  - `WeatherNotificationWorker.kt`: Added the same background reverse-geocoding fallback inside `resolveWeatherData` so background notifications also benefit from resolved location names.
  - `WeatherViewModelTest.kt`: Added `testViewModelInit_LocationNameGeneric_TriggersBackgroundReverseGeocoding` to verify that generic location names are resolved and cached correctly. All tests pass.

## Custom Notification Icon Setup
- **Problem**: Notifications previously used the Android system's generic warning/danger sign drawable (`android.R.drawable.stat_sys_warning`).
- **Fix**:
  - `WeatherNotificationWorker.kt`: Imported `com.weatherinsights.daily.forecast.live.radar.R` and changed the notification builder to use `R.drawable.ic_weather_notification` which the user added under the drawable folder. Verified successful compilation.

## Rain Probability & Dashboard Panels Integration (Issue #8)
- **Goal**: Show precipitation probability in the hourly weather forecast timeline instead of humidity, and create 3 detailed glassy metric panels below it to display Humidity, Wind Speed, and UV Index (with horizontal progress tracks for Humidity and UV).
- **Implementation**:
  - `OpenMeteoApiService.kt`: Updated the Open-Meteo API query to request `precipitation_probability` in the `hourly` query parameters.
  - `OpenMeteoModels.kt`: Added `precipitation_probability` list to `OpenMeteoHourly` data class.
  - `WeatherModels.kt`: Added `precipitationProbability` property (defaulting to `0`) to `HourlyForecast` model to support backward-compatibility.
  - `OpenMeteoMapper.kt`: Mapped `precipitationProbability` from Open-Meteo response into the app's `HourlyForecast` model.
  - `WeatherTimeline.kt`:
    - Updated `HourColumn` to render precipitation probability (using the `Icons.Rounded.Thunderstorm` raining cloud icon and blue tint) and removed the humidity row.
    - Updated `SolarEventColumn`'s empty Box spacer to `12.dp` height to maintain layout alignment.
    - Created the `MetricPanel` reusable component with an icon, title, value text, and an optional custom horizontal progress bar.
    - Replaced the bottom wind speed dashboard with a horizontal `Row` containing three instances of `MetricPanel` for **Humidity** (with a progress bar from 0% to 100%), **Wind Speed** (without progress bar), and **UV Index** (with a progress bar from 0 to 12).
  - `WeatherMapper.kt`: Mapped drizzle and rainy weather codes to `Icons.Rounded.Thunderstorm` (raining cloud icon) instead of `Icons.Rounded.WaterDrop`.
  - `WeatherRepositoryTest.kt`: Added `testOpenMeteoMapper_MapsPrecipitationProbabilityCorrectly` to verify correct mapper behavior. All unit tests compiled and passed.

## Yesterday's Temperature Comparison & UI Optimizations (Issue #10)
- **Goal**: Implement a temperature comparison panel against yesterday's weather with a short sentence describing the difference, and eliminate empty vertical spaces in the success screen UI by positioning components contiguously.
- **Implementation**:
  - `OpenMeteoApiService.kt`: Defined `getYesterdayForecast` to request hourly historical temperatures from Open-Meteo with `start_date` and `end_date`.
  - `OpenMeteoModels.kt` & `OpenMeteoMapper.kt`: Marked `current`, `hourly`, and `daily` optional in `OpenMeteoResponse` to support query responses for historical ranges that omit current/daily blocks. Added corresponding safe nullability mappings in the weather data mapper.
  - `WeatherLocalSource.kt`: Implemented `getYesterdayTemp` and `saveYesterdayTemp` methods to retrieve and cache yesterday's average temperature in DataStore with its date key to prevent outdated cache usage.
  - `WeatherRepository.kt`: Implemented `getYesterdayTemperature` which checks local DataStore storage first and requests Open-Meteo via `getYesterdayForecast` if not cached, averaging and caching the hourly temperatures.
  - `WeatherViewModel.kt` & `WeatherUiState.kt`: Added `yesterdayTemp` parameter to `WeatherUiState.Success`. In the ViewModel, triggered yesterday's temperature loading on success flows (from cache or fresh network fetches) and preserved the value during client-side reverse-geocoding updates.
  - `HomeScreen.kt`: Passed the resolved `yesterdayTemp` from UI state to the `WeatherContent` composable.
  - `WeatherTimeline.kt`:
    - Refactored `WeatherContent` to nest the Timeline, YesterdayComparisonPanel, and Metric panels in a contiguous container with fixed `10.dp` gaps, resolving empty stretch spaces.
    - Added a private `YesterdayComparisonPanel` composable displaying relative warm/cool descriptions and a color-tinted temperature difference badge.
    - Adjusted screen top/bottom padding and used a weighted spacer between the header and bottom panels to center/align them nicely.
  - `WeatherRepositoryTest.kt` & `WeatherViewModelTest.kt`: Updated testing fakes to support new interface methods, and added new unit tests `testGetYesterdayTemperature_CacheHit` and `testGetYesterdayTemperature_CacheMiss_NetworkSuccess` in `WeatherRepositoryTest.kt`. All tests compiled and passed successfully.

## Centered Weather Bubbles & Header Sizing Optimizations
- **Goal**: Center the unified weather bubbles exactly in the middle of the area between the temperature text and the bottom screen edge, and adaptively handle long location names and temperature texts to prevent clipping on narrower devices.
- **Implementation**:
  - `WeatherTimeline.kt`:
    - Imported `TextOverflow` for text truncation.
    - Updated outer `Column` layout to use two equal-weighted `Spacer`s (one above and one below the unified panels container) to center the weather bubbles container exactly in the middle of the space under the temperature text and the bottom screen edge.
    - Set outer `Column` padding to `24.dp` on all sides to establish consistent alignment.
    - Added `vertical = 12.dp` padding to the Header `Column` to increase its allocated space.
    - Configured the location name Row to `fillMaxWidth()` and set the `locationName` `Text` to `fontSize = 36.sp`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and assigned it `Modifier.weight(1f, fill = false)`. This allows the text to center properly when short, while safely truncating and preventing it from pushing refresh/settings buttons off-screen when long.
    - Reduced temperature text size from `64.sp` to `56.sp` to scale better on smaller mobile screens.
  - Verified compilation and ran regression tests to ensure stability.

## Hourly Yesterday Weather Comparison & Open-Meteo Archive API Integration
- **Goal**: Persist and fetch yesterday's full 24 hourly temperature data points from the Open-Meteo Archive API (`https://archive-api.open-meteo.com/v1/archive`), and compare the current hour's temperature against yesterday's corresponding hour temperature.
- **Implementation**:
  - `OpenMeteoApiService.kt`: Refactored `getYesterdayForecast` using Retrofit's `@Url` annotation to allow querying the absolute archive API endpoint dynamically.
  - `WeatherLocalSource.kt`: Replaced single yesterday temperature storage methods with `getYesterdayHourlyTemps` and `saveYesterdayHourlyTemps` to retrieve and save yesterday's 24 hourly temperatures as a comma-separated CSV string in `DataStore` preferences.
  - `WeatherRepository.kt`: Refactored `getYesterdayTemperature` to fetch 24 hourly temperatures from the Archive API using the dynamic URL when not found in the local `DataStore` cache.
  - `WeatherUiState.kt` & `WeatherViewModel.kt`: Updated `WeatherUiState.Success` to hold `yesterdayHourlyTemps` list. Modified the ViewModel to retrieve and assign the 24 hourly temperatures list on success paths.
  - `HomeScreen.kt` & `WeatherTimeline.kt`: Passed the list of 24 hourly temperatures to `WeatherContent`. In the UI, resolved the current hour (`LocalTime.now().hour`) and queried yesterday's corresponding temperature from the list to pass to `YesterdayComparisonPanel`.
  - `WeatherRepositoryTest.kt` & `WeatherViewModelTest.kt`: Refactored unit tests and stubs to accommodate the new method signatures and test caching and mapping of yesterday's hourly temperatures list. All tests passed.

## Hourly Weather Timeline Panel Height Optimization
- **Goal**: Make the hourly weather timeline window ~30% taller by extending its upper limit upward without changing any other component alignments.
- **Implementation**:
  - `WeatherTimeline.kt`: Changed the hourly weather timeline `GlassyPanel` height from `130.dp` to `170.dp`. This moves the top limit of the timeline panel upward (closer to the temperature text), while keeping its center alignment and other screen elements untouched.
  - Verified compilation and ran test suite to ensure successful validation.

## App Version Bump (versionCode 3, versionName 1.1.1)
- **Goal**: Update app versioning parameters in preparation for release or testing deployment.
- **Implementation**:
  - `build.gradle.kts`: Bumped `versionCode` to `3` and `versionName` to `"1.1.1"`.
  - Verified successful project compilation and ran test suite.