# Weather Insights

Weather Insights is a modern, high-performance Android application designed with a Cloud-First architecture. It focuses on providing accurate weather data while optimizing network resources through a centralized shared caching mechanism.

## Project Overview

The primary goal of this application is to deliver weather information using a smart caching strategy. Instead of fetching data directly from weather providers for every user, the application communicates with a custom Cloudflare Worker backend. This backend acts as a central hub, managing a shared cache stored in a Cloudflare D1 (SQLite) database.

### Key Features

- Location-based weather updates using device GPS.
- Centralized shared cache: Weather data is cached for 3-4 hours per location.
- Optimized API usage: Multiple users in the same vicinity benefit from the same cached data in the cloud.
- Modern UI: Built entirely with Jetpack Compose for a reactive and smooth user experience.
- Offline-ready architecture: Designed to handle various network conditions gracefully.

## Technical Architecture

The application follows the MVVM (Model-View-ViewModel) architectural pattern combined with the Repository pattern to ensure a clean separation of concerns and maintainability.

### Layers

- **UI Layer (Jetpack Compose):** Handles the presentation and user interactions. It observes state from the ViewModel.
- **ViewModel Layer:** Manages UI state and handles business logic. It communicates with the Repository to fetch data.
- **Data Layer (Repository):** The single source of truth for data. It manages network requests to the Cloudflare Worker.
- **Network Layer (Retrofit):** Defines the API interface for communicating with the backend services.

### Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Asynchronous Programming:** Kotlin Coroutines and Flow
- **Networking:** Retrofit with Kotlinx Serialization
- **Dependency Injection:** Hilt
- **Backend:** Cloudflare Workers and Cloudflare D1 (SQLite)

## Development Guidelines

All developers and AI agents working on this project must adhere to the following principles:

- **Modular Design:** Keep components decoupled and reusable.
- **Surgical Edits:** Modify only what is necessary to maintain stability.
- **Continuous Verification:** Ensure the project compiles and passes analysis after every change.
- **Strict Communication:** Never assume requirements. Always seek clarification for any ambiguity.

## Setup and Installation

Detailed setup instructions will be provided as the project progresses through its foundational phases.
