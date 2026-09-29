# Aivance

Aivance is a production-grade Android application foundation built with a multi-module Clean Architecture and a premium dark-first design system. It serves as a robust starting point for high-performance, maintainable, and scalable Android applications.

## Overview

Aivance leverages modern Android development practices to provide a seamless user experience and developer ergonomics. The project is designed with a focus on:
- **Clean Architecture**: Separation of concerns into data, domain, and UI layers.
- **Modularity**: A feature-based multi-module structure for improved build times and code isolation.
- **Premium Design**: A dark-first, Material 3-compliant design system that is both expressive and adaptive.

## Key Features

- **Resume Optimizer & ATS Matcher**: Analyze and optimize your resume to pass Applicant Tracking Systems (ATS) with ease. Get **live-reactive scoring** — the ATS engine recalculates automatically (debounced 800 ms) whenever your resume version or job description changes, and **streams analysis progress token-by-token** so you watch the keywords light up instead of staring at a spinner.
- **AI Career Roadmap**: Generate personalized career paths based on your current skills and target roles.
- **Dynamic Cover Letter Generator**: Create tailored cover letters for every job application using advanced AI models. From any job detail screen the engine auto-generates a letter for that job (Copy / Edit / Export to PDF wired).
- **AI Interview Coach**: Practice interviews with a real-time AI coach that provides feedback on your answers and delivery.
- **Real-Time AI Career Assistant**: A context-aware assistant that streams responses token-by-token (Groq/OpenAI/Gemini/Claude SSE), routes intent-based commands (resume analysis, job search, roadmap, mock interview) to the right engine, and gracefully falls back to one-shot generation when a provider lacks streaming.
- **Universal Job Tracker / Pipeline**: Manage your entire job search funnel in one place, from discovery to offer, as a Kanban board.
- **Real Job Search & Filters**: Search across multiple providers (RemoteOK, Remotive, Apify, Arbeitnow, Jobicy, Adzuna, USAJobs) with genuine client-side filtering — **Location (Country / State / City from a ~80-country world catalog), Employment Type (Full-time, Part-time, Internship, Apprenticeship, Contract), Workplace (On-site, Remote, Hybrid), and Experience (0–15+ years)** — plus relevance-ranked results (title > company > description) instead of random provider dumps. Searches fire only when you commit the query (Enter / search button) or apply a filter, so results always match your intent.
- **Working Apply Flow**: "Apply & Track" opens the real application page via a normalized apply URL (explicit URL → `sourceUrl` → href inside the description HTML), and one tap creates an application in the Pipeline.
- **Linked Intelligence Engines**: Job detail → ATS (pre-filled with the job description), Job detail → Cover Letter (auto-generates for that job), Job detail → Recruiter Discovery, Company detail → Recruiters + open roles — every button navigates somewhere real.
- **Streaming Everywhere**: Beyond the Assistant, the **Cover Letter generator and Resume Engine optimization step now stream tokens live** with a typewriter caret — you watch the letter / improved section being written instead of staring at a spinner, with graceful fallback for non-streaming providers.
- **Career HQ Pie Chart**: The Dashboard renders an animated **career-breakdown pie chart** (Career Score · ATS Score · Applied · Saved Jobs) alongside the hero gauge and quick stats.
- **Masked Provider Keys**: Provider Management shows a **masked credential preview** (`sk-••••abcd`) next to the live health chip — the full API key is never rendered on screen.
- **About AiVance**: A new About screen with creator contact (email `iamshaikhazmathulla@outlook.com` · Instagram `@Iamazmathulla`), clickable third-party license links, and a "How AiVance is Made" tech section.
- **Fully Localized UI + Working Language Picker**: Every user-facing string across all feature modules (plus navigation and worker notifications) was extracted into `res/values*` string resources with a **complete Hindi (`values-hi`) translation**. Settings → Language (English / हिन्दी) is persisted to the encrypted DataStore and applied at app startup — switching language now translates the entire app, not just system-formatted values. Other languages are not yet translated, so they are not offered in the picker.
- **Always-On Bottom Navigation**: The 5-tab shell stays visible across the whole main graph (including detail screens) and system back pops one screen at a time — no more accidental app exits from a random tab.
- **Pipeline Manual Add**: Add an application by hand (company + role + stage) straight from the Kanban board via the FAB.
- **Reliable Resume Parsing**: Section parsing no longer dead-ends — versions are fully hydrated with their sections and a deterministic heading-based parser guarantees usable output even without an AI provider.

## Architecture

Aivance is built upon the principles of **Clean Architecture**, ensuring a clear separation of concerns, testability, and maintainability.

Detailed design decisions and architectural evolutions are documented in our [Architecture Decision Records (ADRs)](docs/adr/).

### Core Pillars
- **Multi-module Architecture**: The project is organized into feature and core modules to promote isolation, reusability, and faster build times.
- **Dependency Injection (DI)**: Using [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) for robust and scalable dependency management.
- **Unidirectional Data Flow (UDF)**: UI state management follows UDF principles using [StateFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/) and [Compose](https://developer.android.com/jetpack/compose) to ensure predictable UI updates.

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Architecture**: Clean Architecture, MVVM, Multi-module
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Persistence**: [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Navigation**: [Jetpack Navigation 3 (Adaptive)](https://developer.android.com/guide/navigation/navigation-3)
- **Asynchronous**: [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Logging**: [Timber](https://github.com/JakeWharton/timber)
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Package Manager**: [Gradle](https://gradle.org/) with [Version Catalog](https://developer.android.com/build/migrate-to-catalogs)

## Project Structure

The project is divided into several modules to ensure a clean separation of concerns:

- **`:app`**: The entry point of the application (containing `MainActivity` and `AivanceApp`). It handles global dependency injection, application configuration, and orchestrates the navigation.
- **`:core`**: Shared infrastructure and utility modules.
  - `:core:common`: Shared models, constants, enums, and basic utilities used across modules.
  - `:core:database`: Local data persistence using Room.
  - `:core:datastore`: Key-value storage for user preferences and settings.
  - `:core:designsystem`: The design system containing UI components, themes, and design tokens (Material 3).
  - `:core:network`: Remote data source handling using Retrofit (includes certificate-pinned OkHttp clients).
  - `:core:util`: Low-level helpers and extension functions (PDF/DOCX export, backup/restore).
  - `:core:sdk`: The Provider SDK (ProviderRegistry, ProviderManager, AIProvider abstraction).
  - `:core:ai-providers`: OpenAI-compatible providers (OpenAI, Groq, OpenRouter, Ollama) with streaming SSE.
  - `:core:job-providers`: Job source integrations (RemoteOK, Remotive, Apify, Arbeitnow, Jobicy, Adzuna, USAJobs, Lever, Greenhouse).
  - `:core:enrichment-providers`: Recruiter/email enrichment (Hunter.io).
- **`:feature`**: Independent feature modules, each containing its own UI, ViewModel, and business logic.
  - `:feature:assistant`: The real-time streaming AI Career Assistant.
  - `:feature:ats`: Applicant Tracking System engine (live-reactive + streaming analysis).
  - `:feature:coverletter`: Tools for generating and managing cover letters.
  - `:feature:dashboard`: The central Career HQ hub and overview screen.
  - `:feature:interview`: Prep Studio (practice, history, question bank).
  - `:feature:jobs`: Job search, filters, job detail, company detail, and recruiter discovery.
  - `:feature:recruiter`: Recruiter intelligence and outreach drafts.
  - `:feature:analytics`: Career analytics dashboard.
  - `:feature:profile`: User profile management and settings.
  - `:feature:resume`: **Resume Optimizer & ATS Matcher** engine (7-step pipeline).
  - `:feature:tracker`: Job application tracking and the Pipeline Kanban.
- **`:navigation`**: Centralized navigation logic using the Jetpack Navigation 3 APIs (Auth + Main graphs, 5-tab shell).

## 🚀 Usage Guide

Maximize your career growth with Aivance by following these five steps:

1.  **Step 1: Define Your North Star (Profile/Roadmap)**
    Start by completing your profile and generating an AI Career Roadmap. This helps Aivance understand your goals and provide personalized recommendations.
2.  **Step 2: Optimize Your Assets (Resume & ATS)**
    Upload your resume to the **Resume Optimizer & ATS Matcher**. Use the AI-driven feedback to ensure your resume is perfectly tailored for modern ATS filters.
3.  **Step 3: Tailor Your Outreach (Cover Letter)**
    For every job you're interested in, use the Cover Letter Generator to create a compelling, personalized application that stands out to recruiters.
4.  **Step 4: Prepare for the Battle (Interview Coach)**
    Once you land an interview, head over to the **Interview Coach**. Practice common questions and get real-time feedback on your performance and technical accuracy.
5.  **Step 5: Manage the Funnel (Tracker)**
    Keep all your applications organized in the **Tracker**. Monitor your progress, set follow-up reminders, and analyze your conversion rates to optimize your job search strategy.

## Requirements

- **Android Studio**: Ladybug (2024.2.1) or later
- **JDK**: 17+
- **Android SDK**:
  - Minimum SDK: 26 (Android 8.0)
  - Target SDK: 37 (Android 16)

## Build Status

[![Aivance CI/CD](https://github.com/BangerSoul/Aivance/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/BangerSoul/Aivance/actions/workflows/ci.yml)
[![Dependabot](https://img.shields.io/badge/dependabot-active-brightgreen?logo=dependabot)](https://github.com/BangerSoul/Aivance/network/dependencies)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-purple?logo=kotlin)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.3.1-green?logo=android)](https://developer.android.com/build/releases/gradle-plugin)
[![API](https://img.shields.io/badge/minSdk-26-green)](https://developer.android.com/studio)
[![License](https://img.shields.io/badge/license-proprietary-lightgrey)](LICENSE)

✅ **Current build: COMPILATION SUCCESSFUL** — All 26 Gradle modules compile without errors.

## Getting Started

### 1. Cloning the Repository
```bash
git clone https://github.com/BangerSoul/Aivance.git
cd Aivance
```

### 2. Syncing Gradle
Open the project in Android Studio. Gradle should automatically start syncing. If not, go to `File > Sync Project with Gradle Files`.

### 3. Running the App
Select the `app` configuration and choose a device or emulator running API 26 or higher, then click the **Run** button.

## Scripts

Use these Gradle commands for common development tasks:

- **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Run Unit Tests**:
  ```bash
  ./gradlew test
  ```
- **Run Instrumented Tests**:
  ```bash
  ./gradlew connectedAndroidTest
  ```

## API Setup (Gemini AI)

Some features (like AI integration) require API keys. These must be configured in your `local.properties` file or through the App Settings menu to avoid committing sensitive information.

### Configuration via `local.properties`
To use the AI features (e.g., in the `:core:network` module), you need a Gemini API key:
1. Go to the [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Create or copy your API key.
3. Open your `local.properties` file in the project root.
4. Add the following line:
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```

### Configuration via App Settings
Alternatively, you can provide the API key directly within the app:
1. Open the **Aivance** app on your device.
2. Navigate to **Profile > Settings**.
3. Enter your Gemini API key in the designated field.
4. The key will be securely stored in your local DataStore.

> [!IMPORTANT]
> Never commit `local.properties` to version control. It is already included in the `.gitignore` by default.

## License

AiVance is **proprietary, all rights reserved**. It is a commercial product
distributed through the Google Play Store, and the source in this repository is
provided for the project's own development, operations and maintenance.

The full terms — including the no-redistribution and no-reverse-engineering
restrictions — are in the [LICENSE](LICENSE) file. Please do not open a public
issue for a security problem; see [SECURITY.md](SECURITY.md) for the private
reporting path.

The third-party libraries AiVance depends on remain under their own licenses,
and the in-app About screen links to each of them.
