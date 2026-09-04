# Offline Browser App

An Android application designed for offline content consumption, featuring full offline web browsing, RSS and Mastodon feed aggregation, multi-location weather forecasting, Kiwix and "The Bindery" ZIM archive integration, custom DOM scraper plugin extraction, local HTML importing, and screen-aware on-device AI article summarization.

## Features

- **Offline Web & Feed Browsing**: Download and view offline HTML web pages, RSS feeds, and Mastodon user feeds (`https://mastodon.social/@user.rss`) with offline asset caching.
- **Offline Weather Viewer**: Multi-location weather forecasts powered by Open-Meteo, with current conditions, hourly trends, 7-day predictions, and 10-day local Room database caching.
- **Offline Knowledge Archives (ZIM Files)**:
  - **The Bindery Integration**: Discover and download `.zim` archive modules from local or remote "The Bindery" instances using QR code scanning and SHA-256 certificate fingerprint verification.
  - **Kiwix OPDS Catalog Search**: Search and acquire offline Wikipedia and knowledge archives directly via the Kiwix OPDS catalog interface.
  - **ZIM Reader**: Built-in viewer for offline `.zim` archive files.
- **On-Device AI & Screen-Aware AI Skills**:
  - **Gemma SLM Integration**: Local execution of Gemma GGUF quantized models via `LocalGemmaRunner` for zero-connectivity article summarization.
  - **AI Skill Management**: Configurable multi-step AI execution routines defined in `ai_skills.json`, editable via `AiSettingsActivity` or updated remotely from GitHub.
  - **Gemini Skill Optimizer**: Interactive developer sandbox and remote prompt optimization powered by `gemini-2.5-flash`.
- **Scraper Plugins & In-App Plugin Creator**:
  - **Domain Scraping**: Custom DOM extraction plugins (`.json`) and FiveFilters site configs (`.txt`) to clean articles and eliminate ads, paywalls, and scripts.
  - **Automatic Discovery**: Matches website domains against the `sprillex/BackLine` and `fivefilters/ftr-site-config` GitHub repositories.
  - **Plugin Creator Activity**: Visual DOM inspector and interactive selector generator for crafting custom scraping recipes.
- **Local Document Import**: Import local HTML folders and offline documents recursively into the local database using Android's Document Access Framework.
- **Customizable Sync & Network Management**: Configure background sync intervals, WiFi-only restriction filters, specific network SSID whitelist enforcement, and per-feed auto-download limits.

---

## Tech Stack & Architecture

- **Platform & Language**: Android SDK 34 (min SDK 24), Java 17, Kotlin 1.9+, Coroutines & Flow.
- **UI & Layouts**: Android XML Layouts, Material Design Components, Data Binding, ViewBinding, Bottom Navigation, RecyclerView, Support WebViews.
- **Local Persistence & Storage**:
  - **Room Database (`OfflineDatabase`)**: Schema version 10 storing `articles`, `feeds`, `weather`, `trusted_servers`, and `suggested_feeds`.
  - **Preferences Repository**: SharedPreference storage managing model URLs, WiFi SSIDs, sync frequencies, and AI skill overrides.
- **Networking & API**:
  - **Retrofit 2 & OkHttp 4**: Type-safe HTTP clients with custom certificate validation and `User-Agent` headers.
  - **Jsoup**: HTML parsing, DOM selection, and article extraction.
  - **Gson**: JSON serialization and deserialization.
- **Background Execution**:
  - **WorkManager**: Periodic feed synchronization (`SyncWorker`), ZIM module downloading (`ZimDownloadWorker`), and batch file imports (`FileImportWorker`).
- **AI Infrastructure**:
  - **Local Gemma Runner**: Local SLM execution pipeline for GGUF model structures.
  - **Gemini REST API**: Remote prompt optimizer (`gemini-2.5-flash`).

### Architectural Overview

```
┌──────────────────────────────────────────────────────────┐
│                   UI Layer (Activities)                  │
│ HomeActivity | ArticleViewerActivity | PluginsActivity    │
│ WeatherActivity | BinderyActivity | AiSettingsActivity   │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│              ViewModel & Utility Managers                │
│  MainViewModel | AiSkillManager | PluginSearchUtil        │
│  GemmaManager   | ArticleSummarizationPipeline           │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌────────────────────────────┴─────────────────────────────┐
│                    Repository Layer                      │
│ ArticleRepository | FeedRepository | WeatherRepository   │
│ KiwixRepository   | ScraperPluginRepository              │
└────────────────────────────┬─────────────────────────────┘
                             │
               ┌─────────────┴─────────────┐
               ▼                           ▼
┌───────────────────────────┐ ┌───────────────────────────┐
│     Local Data Layer      │ │    Remote Network Layer   │
│ Room DB (OfflineDatabase) │ │ Open-Meteo | Kiwix OPDS   │
│ PreferencesRepository     │ │ GitHub API | Gemini REST  │
└───────────────────────────┘ └───────────────────────────┘
```

---

## Repository Layout

```
├── aiskills/                  # Default AI Skill definitions and registries (ai_skills.json)
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/offlinebrowser/
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/        # Room Database, DAOs, and Converters
│   │   │   │   │   ├── model/        # Kotlin Data Models & Enums
│   │   │   │   │   ├── network/      # Retrofit Services, Parsers & ScraperEngine
│   │   │   │   │   └── repository/   # Repository classes abstraction
│   │   │   │   ├── ui/               # Adapters, Dialogs & UI components
│   │   │   │   ├── util/             # Gemma, AiSkillManager, Pipelines & Logger
│   │   │   │   ├── viewmodel/        # MainViewModel architecture
│   │   │   │   └── workers/          # WorkManager background workers
│   │   │   ├── res/                  # Layouts, Menus, Drawables, Values & XMLs
│   │   │   └── assets/               # Pipeline configs and default AI skills
│   │   └── test/                     # Unit test suites
├── gradle/                    # Gradle wrapper files and configuration
├── plugins/                   # Bundled scraper JSON plugins organized by lang/Category
├── rss_feeds/                 # Curated top RSS feed CSV lists organized by lang/Category
├── build.gradle.kts           # Root Gradle build configuration
├── settings.gradle.kts          # Project settings and module configurations
└── README.md                  # Project overview and developer instructions
```

---

## Prerequisites & Setup

### Prerequisites

1. **Java Development Kit**: OpenJDK 17 required.
   ```bash
   sudo apt-get update && sudo apt-get install -y openjdk-17-jdk
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
   ```
2. **Android SDK**: Android SDK 34 platform tools and build-tools installed.

### Initial Setup

Clone the repository and verify project setup:

```bash
git clone https://github.com/sprillex/BackLine.git
cd BackLine
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew tasks
```

---

## Configuration

- **AI Model Download URL**: Configurable in `PreferencesRepository` or via `AiSettingsActivity`. Defaults to `https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf`.
- **Gemini API Key**: Used for the AI Skill Sandbox prompt optimizer. Configurable in `AiSettingsActivity` or via environment/preferences.
- **WiFi Enforcement**: Configure restricted WiFi SSIDs and enable WiFi-only downloads in `SettingsActivity`.
- **Sync Schedule**: Background sync frequency (e.g., every 1 hour, 6 hours, 24 hours) configured in `SettingsActivity` via `WorkManager`.

---

## Running the Application

### Compile & Build APKs

To compile the project and assemble a Debug APK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug
```

The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Deploy to Connected Device / Emulator

Ensure an Android emulator or hardware device is attached via `adb`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew installDebug
```

---

## Testing

Run unit tests across test suites:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test
```

---

## API Reference

The Offline Browser application interacts with external REST endpoints, local Bindery services, Kiwix catalog APIs, and remote AI optimization services. Detailed specifications for all endpoints, parameters, JSON schemas, and standard request/response envelopes are documented in [API.md](./API.md).
