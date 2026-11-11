# Offline Browser

An offline-first Android application designed for resilient, zero-connectivity content consumption, featuring offline web browsing, RSS and Mastodon feed aggregation, multi-location weather forecasting, Kiwix and "The Bindery" ZIM archive integration, custom DOM scraper plugin extraction, local HTML importing, and screen-aware on-device AI article summarization.

---

## 1. Project Overview

Offline Browser is built to ensure seamless digital access in environments with unstable, intermittent, or non-existent network connectivity. Rather than functioning as a transient cache, Offline Browser operates as an autonomous offline content repository, aggregating feeds, scraping articles into clean standalone offline documents, storing multi-day weather predictions, and serving complete offline knowledge modules (ZIM files).

### Core Capabilities

- **Offline Web & Feed Browsing**: Aggregates RSS, ATOM, and Mastodon user feeds (`https://mastodon.social/@user.rss`), persisting full articles, enclosure images, and metadata locally in Room database storage.
- **Offline Weather Viewer**: Multi-location meteorological forecasting powered by Open-Meteo, featuring current conditions, hourly metrics, 7-day forecasts, and local 10-day Room database retention.
- **Offline Knowledge Archives (ZIM Files)**:
  - **The Bindery Integration**: Direct discovery and high-speed local TLS downloading of `.zim` archive modules from local Bindery nodes via QR code scanning and SHA-256 certificate fingerprint pinning.
  - **Kiwix OPDS Catalog Search**: Search and acquire offline Wikipedia and specialized knowledge archives directly through the Kiwix OPDS catalog interface.
  - **Built-in ZIM Reader**: Integrated viewer powered by Zimdroid (`libzim`) for navigating offline `.zim` compressed knowledge archives without third-party external viewers.
- **On-Device AI & Screen-Aware AI Skills**:
  - **Local Gemma SLM Execution**: Offline, zero-connectivity article summarization running quantized Gemma GGUF models directly on device via `LocalGemmaRunner`.
  - **AI Skill Management**: Configurable multi-step AI execution pipelines defined in `ai_skills.json`, editable via `AiSettingsActivity` or synchronized remotely from GitHub repositories.
  - **Gemini Skill Optimizer**: Developer sandbox and prompt refinement tool powered by `gemini-2.5-flash` for tuning small on-device SLM prompts against article text.
- **Scraper Plugins & In-App Plugin Creator**:
  - **DOM Scraping Plugins**: Custom DOM extraction recipes (`.json`) and FiveFilters site configs (`.txt`) that strip paywalls, intrusive scripts, ads, and interactive widgets.
  - **Automated Plugin Discovery**: Domain matching against the `sprillex/BackLine` and `fivefilters/ftr-site-config` GitHub repositories.
  - **Interactive Plugin Creator**: On-device visual DOM selector generator allowing users to visually inspect article containers and generate exportable scraping recipes.
- **Local Document Ingestion**: Recursive importing of offline HTML directories and bundled assets into local database feeds via Android's Storage Access Framework.
- **Policy-Based Network Sync**: Granular background synchronization schedules, WiFi-only constraints, specific WiFi SSID whitelisting, and per-feed article retention limits.

---

## 2. Prerequisites

### Host Development & Build System Requirements

- **Supported Operating Systems**:
  - Linux distributions: Ubuntu (22.04 LTS / 24.04 LTS), Debian (11 / 12), Linux Mint (21 / 22), DietPi (Debian-based x86_64 or aarch64).
  - macOS (Ventura 13+, Sonoma 14+) with Xcode Command Line Tools.
  - Windows 10/11 with WSL2 (Ubuntu environment recommended).
- **Hardware Specifications**:
  - **CPU**: x86_64 or ARM64 multi-core processor (4 cores minimum, 8 cores recommended).
  - **Memory (RAM)**: 8 GB minimum (16 GB recommended for Gradle daemon, Android SDK compilation, and emulator execution).
  - **Disk Space**: 10 GB free disk space for Android SDK, build tools, Gradle caches, and generated APK artifacts.

### Runtime Dependencies

- **Java Development Kit (JDK)**: **OpenJDK 17** is strictly required. Android Gradle Plugin 8.3+ with Android SDK Build Tools 34 and `jlink` require Java 17; builds will encounter toolchain incompatibilities on Java 21+.
- **Android SDK & Build Tools**:
  - `compileSdk`: 34 (Android 14)
  - `minSdk`: 24 (Android 7.0 Nougat)
  - `targetSdk`: 34 (Android 14)
  - `buildToolsVersion`: 34.0.0
  - Android SDK Command-line Tools (`cmdline-tools;latest`)
  - Android SDK Platform `platforms;android-34`
  - Android SDK Platform-Tools (`adb`)

### Target Device Requirements

- Physical Android device or Android Virtual Device (AVD) running Android 7.0 (API level 24) or higher.
- Storage: Minimum 500 MB free space (additional storage required for downloading large Kiwix `.zim` archives or Gemma `.gguf` model weights).

### Required Android Permissions

The application declares the following permissions in `app/src/main/AndroidManifest.xml`:

| Permission | Purpose | Runtime vs Install-time |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Retrieve feeds, weather forecasts, ZIM files, and plugins over network. | Install-time |
| `android.permission.ACCESS_NETWORK_STATE` | Monitor active network interfaces (cellular, WiFi, offline). | Install-time |
| `android.permission.ACCESS_WIFI_STATE` | Query WiFi connection status and connected network information. | Install-time |
| `android.permission.ACCESS_FINE_LOCATION` | Required on Android 8.1+ (API 27+) to retrieve the current WiFi SSID for network whitelisting; also used for local weather geolocation. | Runtime |
| `android.permission.ACCESS_COARSE_LOCATION` | Approximate location detection for Open-Meteo weather forecasts. | Runtime |
| `android.permission.FOREGROUND_SERVICE` | Run persistent background data synchronization and file downloading. | Install-time |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ requirement for WorkManager background data synchronization jobs. | Install-time |
| `android.permission.POST_NOTIFICATIONS` | Deliver sync completion and download progress alerts (Android 13+). | Runtime |

---

## 3. Installation & Setup

Execute the following commands in sequence on a standard Linux environment (Ubuntu/Debian/Mint/DietPi):

### Step 1: Install System Dependencies & OpenJDK 17

```bash
# Update package repositories and install OpenJDK 17 and base utilities non-interactively
sudo apt-get update && sudo apt-get install -y \
    openjdk-17-jdk \
    git \
    curl \
    unzip \
    libpulse0 \
    libgl1

# Set JAVA_HOME and verify Java version
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

### Step 2: Install Android Command-Line Tools (If not already present)

```bash
export ANDROID_SDK_ROOT=/opt/android-sdk
sudo mkdir -p $ANDROID_SDK_ROOT/cmdline-tools

# Download and extract Android Command-Line Tools
curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
sudo unzip -q /tmp/cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools
sudo mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest
rm -f /tmp/cmdline-tools.zip

# Export Android SDK environment variables
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH

# Accept licenses and install Android 34 SDK platform and build tools
yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Step 3: Clone the Repository & Initialize Gradle

```bash
git clone https://github.com/sprillex/BackLine.git
cd BackLine

# Grant execution rights to Gradle wrapper
chmod +x gradlew

# Verify Gradle wrapper tasks with OpenJDK 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew tasks
```

---

## 4. Configuration

Offline Browser provides granular runtime configuration via Android `SharedPreferences` (managed by `PreferencesRepository`), internal asset configs, and device-local JSON files.

### Configuration Overview & File Locations

| Config Item / File | Path / Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **Gemma Model URL** | `pref_gemma_model_url` | `https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf` | Direct CDN URL for downloading quantized Gemma GGUF model weights. |
| **Gemma Model File** | `pref_gemma_model_path` | `""` (Unset) | Absolute filesystem path to the downloaded `.gguf` model file on device. |
| **Gemini API Key** | `pref_gemini_api_key` | `""` (Empty string) | Google Generative AI API key used in `GeminiSkillOptimizer` within the AI Sandbox. |
| **WiFi Only Sync** | `pref_sync_wifi_only` | `true` | When true, WorkManager background synchronization runs only when connected to unmetered WiFi. |
| **Whitelisted SSID** | `pref_allowed_wifi_ssid` | `""` (Any WiFi) | Enforces synchronization only when connected to a specific named WiFi SSID. |
| **Sync Interval** | `pref_sync_interval_hours` | `6` | Periodic sync frequency in hours (e.g., 1, 6, 12, 24) scheduled via `WorkManager`. |
| **Auto Download Limit** | `auto_download_limit` | `5` | Maximum number of new articles downloaded and scraped per feed per sync cycle. |
| **AI Skills Registry** | `context.filesDir/ai_skills.json` (Fallback: `assets/ai_skills.json`) | Bundled Registry | Multi-turn prompt templates and inference parameters for on-device AI skills. |
| **Summary Pipeline Config**| `context.filesDir/summary_pipeline_config.json` (Fallback: `assets/summary_pipeline_config.json`) | Bundled Sanitizer Config | HTML sanitization rules, strip selectors, and input character limits for AI summarization. |
| **Room Database** | `/data/data/com.example.offlinebrowser/databases/offline_browser.db` | Schema Version 10 | SQLite database containing articles, feeds, weather data, and trusted Bindery servers. |

### Environment Variables (Development & CI)

| Variable | Description | Example |
| :--- | :--- | :--- |
| `JAVA_HOME` | Points to OpenJDK 17 installation root. | `/usr/lib/jvm/java-17-openjdk-amd64` |
| `ANDROID_HOME` or `ANDROID_SDK_ROOT` | Points to Android SDK directory containing `platforms` and `build-tools`. | `/opt/android-sdk` or `$HOME/Android/Sdk` |
| `PATH` | System PATH containing `$JAVA_HOME/bin` and `$ANDROID_SDK_ROOT/platform-tools`. | `$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH` |

---

## 5. Usage Guide

All compilation, testing, deployment, and operational workflows are managed via the Gradle wrapper and Android Debug Bridge (`adb`).

### 1. Compile & Assemble APKs

Compile the project and build the debug APK non-interactively:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug --no-daemon
```

*Expected output log:*
```text
BUILD SUCCESSFUL in 42s
54 actionable tasks: 54 executed
```
The compiled output is located at: `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Execute Unit Tests

Run all unit tests across the test suites:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew test --no-daemon
```

*Expected output log:*
```text
> Task :app:testDebugUnitTest
> Task :app:testReleaseUnitTest
> Task :app:test
BUILD SUCCESSFUL in 18s
```

### 3. Deploy to Connected Hardware or Emulator

Install the debug package onto an active device or running emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Launch Application via ADB

Start the primary launcher activity (`HomeActivity`):

```bash
adb shell am start -n com.example.offlinebrowser/.HomeActivity
```

*Expected output:*
```text
Starting: Intent { cmp=com.example.offlinebrowser/.HomeActivity }
```

### 5. Inspect Runtime Logs & Telemetry

Monitor live application output, WorkManager sync jobs, and scraper logs:

```bash
adb logcat -s OfflineBrowser:V ScraperEngine:D GemmaManager:D SyncWorker:D
```

*Sample execution log:*
```text
D/SyncWorker: Starting scheduled sync cycle for all enabled feeds
D/ScraperEngine: Scraping article url: https://techcrunch.com/article-slug/
D/ScraperEngine: Matched domain plugin: plugins/en/Tech/techcrunch.json
D/ScraperEngine: Extracted article title: 'Open Source Advances' with selector 'h1'
D/ScraperEngine: Extracted content body (4820 bytes), injected RSS enclosure image
I/SyncWorker: Sync completed: 5 articles updated, 0 errors
```

---

## 6. Architecture & Subsystems

Offline Browser is built using modern Android Jetpack architecture following the Model-View-ViewModel (MVVM) and Unidirectional Data Flow (UDF) patterns.

```
┌───────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer                              │
│                                                                           │
│  HomeActivity            ArticleViewerActivity       WeatherActivity      │
│  PluginsActivity         AiSettingsActivity          BinderyActivity      │
│  PluginCreatorActivity   KiwixSearchActivity         ZimViewerActivity    │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │ Observers (StateFlow / LiveData)
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                            ViewModel Layer                                │
│                                                                           │
│                            MainViewModel                                  │
│         Manages UI State, Filters, Search, Sync Requests, Feed Lists       │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │ Coroutines / Dispatchers.IO
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                           Repository Layer                                │
│                                                                           │
│   ArticleRepository      FeedRepository          WeatherRepository        │
│   KiwixRepository        PreferencesRepository   ScraperPluginRepository  │
└──────────────────┬─────────────────────────────────────┬──────────────────┘
                   │                                     │
                   ▼                                     ▼
┌──────────────────────────────────────┐  ┌─────────────────────────────────┐
│          Local Data Layer            │  │      Remote Network Layer       │
│                                      │  │                                 │
│  OfflineDatabase (Room SQLite v10)   │  │  Retrofit 2 & OkHttp 4 Clients  │
│    - ArticleDao                      │  │    - Open-Meteo REST Service    │
│    - FeedDao                         │  │    - Kiwix OPDS XML Service     │
│    - WeatherDao                      │  │    - GitHub Content & Tree API  │
│    - TrustedServerDao                │  │    - Google Generative AI REST  │
│    - SuggestedFeedDao                │  │    - The Bindery Local Node TLS │
│  SharedPreferences                   │  │  Jsoup Article Scraper Engine   │
│  Local Filesystem (ZIM, GGUF)        │  │  ROME RSS/Atom Feed Parsers     │
└──────────────────────────────────────┘  └─────────────────────────────────┘
                   ▲                                     ▲
                   │                                     │
┌──────────────────┴─────────────────────────────────────┴──────────────────┐
│                         Background & AI Subsystems                        │
│                                                                           │
│   WorkManager Execution Engine:                                           │
│     - SyncWorker (Periodic Feed & Article Polling)                        │
│     - ZimDownloadWorker (Large Binary ZIM Acquisition)                    │
│     - FileImportWorker (Asynchronous Local Folder Ingestion)              │
│                                                                           │
│   AI & Inference Engine:                                                  │
│     - GemmaManager (Model Lifecycle, Downloads, Prompt Dispatching)       │
│     - LocalGemmaRunner & DefaultLocalGemmaRunner (On-Device Inference)    │
│     - AiSkillManager (Multi-step Skill Execution, Schema Normalization)   │
│     - GeminiSkillOptimizer (Cloud Prompt Sandbox via gemini-2.5-flash)    │
│     - ArticleSummarizationPipeline (HTML Pre-sanitization & Fact Filter)  │
└───────────────────────────────────────────────────────────────────────────┘
```

### Subsystems Breakdown

1. **Local Data Persistence (`OfflineDatabase`)**:
   - Built on Room 2.6.1 with schema migration history through version 10.
   - Migration 9 to 10 adds an optional `summary TEXT` column to the `articles` table for caching generated AI summaries.
   - Includes full relational entities for `articles`, `feeds`, `weather`, `suggested_feeds`, and `trusted_servers`.
2. **Scraper Engine (`ScraperEngine`)**:
   - Executes domain-specific extraction rules defined in JSON plugins.
   - Evaluates strategies (`CSS_SELECTOR`, `EXTRACT_FROM_JS_VAR`, `XPATH`) with fallback selector chains.
   - Cleans DOM nodes by stripping ads, scripts, tracking pixels, and interactive widgets.
3. **Background Sync Engine (`WorkManager`)**:
   - `SyncWorker`: Scheduled background job that enforces network conditions (metered vs unmetered, WiFi SSID matching) before fetching feed entries and running `ScraperEngine`.
   - `ZimDownloadWorker`: Manages multi-gigabyte ZIM file streaming and checksum verification.
   - `FileImportWorker`: Ingests offline HTML file structures in the background using `Dispatchers.IO`.
4. **On-Device AI Engine (`GemmaManager` & `AiSkillManager`)**:
   - Abstracted through `LocalGemmaRunner`. If no native binary is present, `DefaultLocalGemmaRunner` provides resilient fallback extraction of context-aware facts.
   - `ArticleSummarizationPipeline` strips unwanted HTML (`nav`, `header`, `footer`, `aside`, `.poll`, `quiz`) before tokenization.
   - `AiSkillSandboxBottomSheetDialogFragment` allows live side-by-side prompt tuning and field diffing against `GeminiSkillOptimizer`.
5. **Security & Custom Trust Management (`SecurityModule`)**:
   - Manages self-signed certificate validation for local Bindery nodes. Scanned QR codes provide SHA-256 fingerprints checked by an implementation of `X509TrustManager`.

---

## 7. Troubleshooting

| Symptom / Error | Root Cause | Actionable Recovery Step |
| :--- | :--- | :--- |
| **`Unsupported class file major version 65` or `jlink` build failure** | Gradle execution was initiated with Java 21+ instead of Java 17. | Install OpenJDK 17 and set `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` before invoking `./gradlew`. |
| **`Could not find or load main class` during Gradle invocation** | Missing or corrupted Gradle wrapper jar (`gradle/wrapper/gradle-wrapper.jar`). | Regenerate wrapper via `gradle wrapper` or ensure git tracked assets are intact. |
| **`SDK location not found`** | `ANDROID_HOME` or `ANDROID_SDK_ROOT` is unset, or `local.properties` is missing. | Export SDK path: `export ANDROID_HOME=/opt/android-sdk` or create `local.properties` with `sdk.dir=/opt/android-sdk`. |
| **`ZimDownloadWorker` failed: SSL Handshake Exception** | Connecting to a local Bindery instance without trusting its self-signed certificate. | Scan the Bindery server's QR code from `BinderyActivity` to register its SHA-256 fingerprint into `trusted_servers`. |
| **Feed sync skipped: `Constraints not met`** | Device is on cellular data or an untrusted WiFi network when WiFi-only / SSID whitelisting is active. | Open `SettingsActivity` and either disable "WiFi Only Sync" or add the active network to the allowed SSID whitelist. |
| **`LocalGemmaRunner: Model file not found`** | Summarization was invoked, but the `.gguf` model file has not been downloaded or selected. | Navigate to `AiSettingsActivity`, select "Download Model" (or manually place a GGUF model in storage), and verify `pref_gemma_model_path`. |
| **Gemini Skill Optimizer returns `401 Unauthorized`** | Missing or invalid Gemini API key in `PreferencesRepository`. | Provide a valid Google AI Studio API key in `AiSettingsActivity` under "Gemini API Key". |
| **Article scraper extracts empty body content** | Target website changed its DOM structure, rendering the plugin selectors obsolete. | Launch `PluginCreatorActivity` on the article URL, select the new container element, and export an updated plugin JSON to `plugins/`. |

---

## 8. External Interfaces & API Reference

Offline Browser interfaces with external REST endpoints, local Bindery nodes, Kiwix catalog servers, and Google Generative AI services. Complete request parameters, response payloads, error representations, and data exchange schemas are documented in [API.md](./API.md).
