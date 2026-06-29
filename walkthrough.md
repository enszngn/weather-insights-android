# Project Walkthrough

This file contains a historical log of all major changes made to the project.

## Initial Setup - Phase 0
- Created `agents.md` with strict AI guidelines.
- Created `project.md` as a technical roadmap.
- Created `README.md` with project vision and architecture.
- Established persistent logging by creating `task.md` and `walkthrough.md` in the project root.
- Refactored package structure for Jetpack Compose and Repository pattern:
    - `data/model`
    - `data/network`
    - `data/repository`
    - `ui/components`
    - `ui/screens`
    - `ui/theme`
    - `ui/viewmodel`

## Phase 1.1: Dependency Configuration
- Updated `gradle/libs.versions.toml` with Kotlin 2.0.21, Hilt 2.60, Retrofit 2.11.0, and Compose BOM 2024.10.00.
- Declared plugins in root `build.gradle.kts` and applied KSP, Hilt, Serialization, and Compose compiler plugins in `app/build.gradle.kts`.
- Configured JVM target to Java 11 using the new `kotlin { compilerOptions { jvmTarget.set(...) } }` block for AGP 9.0+.
- Configured `android.disallowKotlinSourceSets=false` in `gradle.properties` to ensure KSP registers generated source files correctly.
- Added Internet permission, registered `WeatherApplication` Hilt entry point, and declared launcher `MainActivity` in `AndroidManifest.xml`.
- Created `WeatherApplication` annotated with `@HiltAndroidApp`.
- Created `MainActivity` annotated with `@AndroidEntryPoint` with initial Compose UI layout.

## Phase 1.3 & Phase 2: Network Client & Data Layer
- Created structured and raw forecast serialization models in `data/model` package ([WeatherModels.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/model/WeatherModels.kt), [OpenMeteoModels.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/model/OpenMeteoModels.kt), and [WeatherPostPayload.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/model/WeatherPostPayload.kt)).
- Defined Retrofit API interfaces in `data/network` package ([WeatherApiService.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/network/WeatherApiService.kt) and [OpenMeteoApiService.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/network/OpenMeteoApiService.kt)).
- Configured Dagger Hilt dependency injection for JSON parser, OkHttpClient, and two distinct Retrofit clients inside [NetworkModule.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/di/NetworkModule.kt).
- Implemented worker-first caching and fallback logic inside [WeatherRepository.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/repository/WeatherRepository.kt).
- Created a self-contained unit test suite in [WeatherRepositoryTest.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/test/java/com/example/weather_insights/WeatherRepositoryTest.kt) utilizing API Fakes, verifying cache hits, misses, uploads, and fallback errors.

## Phase 3: Logic & State Management
- Configured Google Play Services Location dependency in version catalogs and `build.gradle.kts`.
- Requested coarse and fine location permissions in `AndroidManifest.xml`.
- Created [LocationTracker.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/location/LocationTracker.kt) interface and coordinates model.
- Created [DefaultLocationTracker.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/data/location/DefaultLocationTracker.kt) using FusedLocationProviderClient, wrapping Google tasks with coroutines cancellable suspend block.
- Configured [LocationModule.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/di/LocationModule.kt) for Hilt to bind `LocationTracker` and provide `FusedLocationProviderClient`.
- Implemented [WeatherUiState.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/ui/viewmodel/WeatherUiState.kt) sealed interface supporting state representing Loading, Success, and Error.
- Implemented [WeatherViewModel.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/main/kotlin/com/example/weather_insights/ui/viewmodel/WeatherViewModel.kt) supporting location querying and mapping results. Added location-permission-denied detection to transition to error states with permission warnings.
- Added `kotlinx-coroutines-test` dependency and implemented unit tests inside [WeatherViewModelTest.kt](file:///Users/eneszengin/Desktop/workspace/weather-insights-android/app/src/test/java/com/example/weather_insights/WeatherViewModelTest.kt) covering location permission errors and success/failure flows.
