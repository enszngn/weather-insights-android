# Weather Insights — Project State

Single source of truth for architecture, progress, and pending work.
Change history lives in `walkthrough.md`; update both files together.

## Overview

Cloud-first Android weather app. Client talks to a custom Cloudflare Worker
backed by Cloudflare D1 (SQLite) with a shared per-location cache (3–4 hour TTL).
Multiple users in the same vicinity share cached data.

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose
- Async: Coroutines + Flow
- Network: Retrofit + Kotlinx Serialization
- DI: Hilt
- Persistence: Jetpack DataStore Preferences
- Background: WorkManager + AlarmManager (exact alarms)
- Location: Google Play Services FusedLocationProviderClient
- Backend: Cloudflare Workers + Cloudflare D1

## Architecture

MVVM + Repository. Layers:
- UI (Compose) observes ViewModel state.
- ViewModel manages UI state + business logic; Context-free.
- Repository is single source of truth; handles network + local cache.
- Network layer (Retrofit) defines backend interfaces.
- Worker/Receiver handle background notifications + scheduling.

## Package Layout

`app/src/main/kotlin/com/weatherinsights/`:
- `data/{model,network,repository,datasource,location,mapper}`
- `di/`
- `ui/{components,screens,theme,util,viewmodel}`
- `worker/`
- `receiver/`

Tests: `app/src/test/java/com/weatherinsights/` (WeatherRepositoryTest, WeatherViewModelTest).

## Completed Work

- [x] Project documentation (agents.md, project.md, README.md)
- [x] Persistent logging established (now project.md + walkthrough.md)
- [x] Phase 1.1: Dependency Configuration (Compose, Retrofit, Hilt)
- [x] Phase 1.2: Base Theme Setup
- [x] Phase 1.3: Network Client Setup
- [x] Phase 2.1: Data Models (Cloudflare & Open-Meteo)
- [x] Phase 2.2: API Service Interface
- [x] Phase 2.3: Repository Implementation
- [x] Phase 3.1: ViewModel Implementation
- [x] Phase 3.2: Location Integration
- [x] Phase 4.1: Atomic Components
- [x] Phase 4.2: Main Screen Development
- [x] Phase 5.3: Performance Optimization (Fast Location Fallback & Async Caching)
- [x] Phase 5.4: Parallel Geocoding & Jetpack DataStore Local Caching
- [x] Phase 6: Redundancy Cleanup & God Module Refactor
- [x] Bugfix: locationName always "Çankaya" in analytics
- [x] Feature: Manual refresh button with 15-minute persistent rate limiting (max 3 refreshes)
- [x] UI: Replace all remaining Unicode emojis with Material Icons
- [x] UI: Make all glassy panels and bubbles have sharp edges (0.dp corner radius)
- [x] UI: Enlarge wind speed icon to 54.dp and delete redundant text label
- [x] UI: Color-tint weather timeline icons (yellow sun, grey clouds, blue moon, dark grey storm)
- [x] Project: Rename package name to com.weatherinsights globally (move files and update references)
- [x] Project: Change app display name in strings.xml to Weather Insights
- [x] Feature: Add WorkManager dependency to version catalog and app build.gradle.kts
- [x] Feature: Create NotificationPreferences data model
- [x] Feature: Update WeatherLocalSource with DataStore preferences for notifications
- [x] Feature: Implement WeatherNotificationWorker for checks and notifications
- [x] Feature: Design and build SettingsScreen UI for notification configuration
- [x] Feature: Integrate SettingsScreen navigation/toggle from HomeScreen
- [x] Feature: Request POST_NOTIFICATIONS permission in MainActivity
- [x] Feature: Write unit tests for notification preferences and worker logic
- [x] Verification: Test notification scheduling and verify layout visual quality
- [x] Bugfix: Decouple WeatherViewModel from Context & fix test notification build compilation
- [x] Feature: Temporary test notification button in the Home screen header
- [x] Phase 7.1: Add SCHEDULE_EXACT_ALARM permission and receiver to AndroidManifest.xml
- [x] Phase 7.2: Remove healthAlertsEnabled from NotificationPreferences.kt
- [x] Phase 7.3: Create AlarmScheduler.kt to handle exact alarms
- [x] Phase 7.4: Create WeatherNotificationReceiver.kt to receive alarm triggers
- [x] Phase 7.5: Refactor WeatherNotificationWorker.kt for direct report execution and health removal
- [x] Phase 7.6: Remove health alerts toggle from SettingsScreen.kt
- [x] Phase 7.7: Integrate AlarmScheduler in MainActivity.kt and WeatherViewModel.kt
- [x] Phase 7.8: Update WeatherViewModelTest.kt and verify all tests pass
- [x] Phase 8.1: Group A - Safe Deletions (#4, #5, #7, #8, #12, #16)
- [x] Phase 8.2: Group B - Extractions (#1, #2, #10)
- [x] Phase 8.3: Group C - Fix bad patterns (#3, #9, #11, #13, #14, #15)
- [x] Phase 8.4: Group D - Move/restructure (#6)
- [x] Phase 8.5: Group E - Visibility/access tightening (#17)
- [x] Phase 9.1: Integrate VerticalPager in WeatherContent for Reels-style daily navigation
- [x] Phase 9.2: Render formatted day label under city name in weather header
- [x] Phase 9.3: Modularize getDynamicBackgroundColorForDay in BackgroundColorUtil.kt
- [x] Phase 9.4: Safe deletion of unused DailyForecastRow.kt
- [x] Phase 9.5: Add vertical dot indicators on the right side of the screen
- [x] Phase 9.6: Convert hourly timeline to horizontal scrolling with fade edges masking
- [x] Phase 9.7: Make glassy panel card corners subtly rounded (12.dp corner radius)
- [x] Phase 9.8: Compact hourly weather tab (reduced panel height, item widths, padding, and text/icon sizing)
- [x] Phase 9.9: Reposition hourly weather timeline panel higher up (centered at 37.5% vertically from top)
- [x] Project: Update app version to 1.0.0 in build.gradle.kts
- [x] Project: Raise min API level to 26 in build.gradle.kts

## Not Started (from original roadmap, never completed)

- [ ] Phase 4.3: Compose Navigation (if more screens are added)
- [ ] Phase 5.1: Error Handling — user-friendly messages + Retry logic
- [ ] Phase 5.2: Animation — smooth transitions and loading animations
- [ ] Final: Full Integration Test (GPS → Cloudflare → UI)
- [ ] Final: UI/UX Polish pass against best practices

## Verification

- Build: `./gradlew clean` clears stale Hilt/KSP generated code after package/dependency changes.
- Tests: `WeatherRepositoryTest.kt` + `WeatherViewModelTest.kt` — 11–12 unit tests, all passing as of last logged phase.