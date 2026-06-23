# AI Agent Guidelines & Practices

This document defines the strict rules and practices that every AI Agent working on this project must follow.

## Core Rules

1.  **Language:** ALWAYS write comments, documentation, and commit messages in English.
2.  **No Assumptions:** NEVER fill in gaps or uncertainties with your own assumptions. If a requirement is unclear, ALWAYS ask the user for clarification. **NEVER ASSUME ANYTHING.**
3.  **Modular Programming:** ALWAYS follow modular programming principles. Keep concerns separated (MVVM + Repository).
4.  **No Redundancy:** Avoid duplicate code. Use shared components and utility functions where appropriate.
5.  **Verification:** After every prompt/task, you MUST verify that the code compiles and is free of errors. Use `gradle_build` or `analyze_file` tools.
6.  **Logging & Persistence:** ALWAYS log your progress. Update `task.md` and `walkthrough.md` in the PROJECT ROOT continuously. These files serve as the persistent memory of the project across different sessions. Every new session MUST start by reading these files to understand the current state.

## Technical Standards

- **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern.
- **UI:** Jetpack Compose (Modern Declarative UI).
- **Concurrency:** Kotlin Coroutines & Flow (Web-like async/await).
- **Network:** Retrofit + Kotlinx Serialization (Cloudflare API Gateway).
- **Dependency Injection:** Hilt (to be implemented).

## Communication Protocol

- If an API endpoint is unknown, ASK.
- If a UI design is unspecified, ASK.
- Before making breaking changes to the project structure, ASK.
