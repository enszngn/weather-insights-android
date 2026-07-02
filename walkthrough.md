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
- `ui/util/BackgroundColorUtil.kt`: added `getDynamicBackgroundColorForDay` to compute dynamic day/night colors on a per-day basis, using it for the full-bleed page backgrounds.
- Deleted `DailyForecastRow.kt` (safe deletion of redundant code).
- Verified compilation and test suite (all tests pass).