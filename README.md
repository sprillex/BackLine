# Offline Browser (BackLine)

An offline-first Android application (`com.example.offlinebrowser`) for zero-connectivity content consumption: RSS/Atom/Mastodon/HTML feed aggregation, two-stage article classification & filtering, custom DOM/JS-var/XPath scraper plugins, Open-Meteo offline weather, Kiwix OPDS & local TLS Bindery ZIM archives, and on-device Gemma AI skill summarization.

- **Architecture Spec (AI Coding Context):** [ARCHITECTURE.md](./ARCHITECTURE.md)
- **API, Schemas & Module Reference:** [API.md](./API.md)
- **SBOM & Security Manifest:** [LIBRARIES.md](./LIBRARIES.md)

---

## 1. Overview & Primary Features

Offline Browser operates across four decoupled planes:

1. **Feed Ingestion & Scraping Plane:** Aggregates `RSS`, `MASTODON`, and `HTML` feeds (`RssParser`, `FeedRepository`), runs two-stage classification/filtering (`ArticleClassifier`), and extracts full article bodies via `ScraperEngine` (`CSS_SELECTOR`, `EXTRACT_FROM_JS_VAR`, `XPATH`) with OkHttp + headless `WebViewFetcher` fallback on HTTP `403`/`503`.
2. **Offline Knowledge Archive Plane:** Searches Kiwix OPDS catalogs (`KiwixRepository`), discovers and downloads `.zim` archives over SHA-256-pinned local TLS from The Bindery (`BinderyActivity`, `SafeClientFactory`, `ZimDownloadWorker`), imports/links local `.zim` files via Storage Access Framework (`LibraryActivity`, `FileImportWorker`), and opens them with Zimdroid (`ZimViewerActivity`).
3. **On-Device AI & Skills Plane:** Sanitizes article HTML (`ArticleSummarizationPipeline`), executes multi-step `AiSkill` workflows (`AiSkillManager`, `GemmaManager`) via `LocalGemmaRunner` (or `DefaultLocalGemmaRunner` fallback), and syncs/edits skills (`AiSettingsActivity`).
4. **Weather & Sync Policy Plane:** Persists 1–16 day Open-Meteo forecasts (`WeatherRepository`, `WeatherActivity`) and schedules periodic `WorkManager` sync (`SyncWorker`) gated by WiFi-only and allowed-SSID constraints (`PreferencesRepository`).

---

## 2. Prerequisites

### Host Build Environment
- **Supported OS:** Debian 11/12, Ubuntu 22.04/24.04 LTS, Linux Mint 21/22, DietPi (Debian x86_64/aarch64), macOS 13+, or Windows 10/11 WSL2.
- **Hardware:** 4+ CPU cores (8 recommended), 8 GB RAM min (16 GB recommended), 10 GB free disk for Android SDK + Gradle caches.
- **Required Binaries & Toolchains:**
  - **OpenJDK 17** (`JAVA_HOME` must point to JDK 17; `build.gradle.kts` / `app/build.gradle.kts` target Java 17 and AGP `8.2.0`).
  - **Android SDK:** `compileSdk = 34`, `minSdk = 24`, `targetSdk = 34`, `build-tools;34.0.0`, `platforms;android-34`, `platform-tools` (`adb`), `cmdline-tools;latest`.
  - **Utilities:** `git`, `curl`, `unzip`.

### Target Device & Permissions
- Android 7.0+ (`minSdk 24`), 500 MB+ free storage (extra for `.zim` archives and `.gguf` model weights).
- Declared in [app/src/main/AndroidManifest.xml](./app/src/main/AndroidManifest.xml):

| Permission | Purpose | Grant Type |
| :--- | :--- | :--- |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Feed, weather, Kiwix, Bindery, and GitHub sync + connectivity checks | Install-time |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | WiFi SSID retrieval (`WifiManager` on Android 8.1+) & weather geolocation | Runtime |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | WorkManager foreground ZIM downloads/imports (`ZimDownloadWorker`, `FileImportWorker`) | Install-time |
| `POST_NOTIFICATIONS` | Download/import progress & completion notifications (Android 13+) | Runtime |

---

## 3. Installation & Setup

Non-interactive setup on Debian/Ubuntu/Linux Mint/DietPi:

```bash
# 1. Install OpenJDK 17 and CLI utilities
sudo apt-get update && sudo apt-get install -y \
  openjdk-17-jdk git curl unzip

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# 2. Install Android SDK Command-Line Tools (skip if /opt/android-sdk exists)
export ANDROID_SDK_ROOT=/opt/android-sdk
sudo mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
curl -fsSL -o /tmp/cmdline-tools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
sudo unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
sudo mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
rm -f /tmp/cmdline-tools.zip

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 3. Verify Gradle wrapper (Gradle 8.7)
chmod +x gradlew
./gradlew tasks --no-daemon
```

> **Note:** No Python virtualenv (`venv`) or `.env` file is required by this Android/Gradle codebase. Optional host SDK discovery can alternatively use a gitignored `local.properties` file containing `sdk.dir=/opt/android-sdk`.

---

## 4. Configuration

### Build & Host Environment Variables

| Variable / File | Path / Value | Description |
| :--- | :--- | :--- |
| `JAVA_HOME` | `/usr/lib/jvm/java-17-openjdk-amd64` | OpenJDK 17 root required by AGP `8.2.0` and Kotlin `1.9.23` |
| `ANDROID_SDK_ROOT` / `ANDROID_HOME` | `/opt/android-sdk` | Android SDK location (`platforms;android-34`, `build-tools;34.0.0`) |
| `gradle.properties` | `org.gradle.jvmargs=-Xmx2048m` | Gradle JVM heap, UTF-8 encoding, AndroidX & Jetifier flags |
| `local.properties` (optional, gitignored) | `sdk.dir=/opt/android-sdk` | Explicit SDK override for Android Studio / Gradle |

### Runtime Preferences (`SharedPreferences`: `offline_browser_prefs`)

Managed by [PreferencesRepository.kt](./app/src/main/java/com/example/offlinebrowser/data/repository/PreferencesRepository.kt):

| Key | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `wifi_only` | `Boolean` | `false` | Require `TRANSPORT_WIFI` / `NetworkType.UNMETERED` for sync & downloads |
| `allowed_wifi_ssids` | `Set<String>` | `emptySet()` | Whitelisted WiFi SSIDs checked by `SyncWorker` (requires location permission) |
| `refresh_interval_minutes` | `Long` | `60` | Periodic `SyncWorker` interval in minutes |
| `feed_limit_count` | `Int` | `50` | Max non-favorite articles retained per feed |
| `feed_limit_days` | `Int` | `30` | Max age in days for non-favorite articles |
| `weather_units` | `String` | `"metric"` | `"metric"` (`°C`) or `"imperial"` (`°F`) |
| `weather_refresh_interval_minutes` | `Long` | `60` | Weather refresh interval (clamped `>= 15`) |
| `weather_forecast_days` | `Int` | `7` | Open-Meteo forecast days (clamped `1..16`) |
| `detailed_debugging_enabled` | `Boolean` | `false` | Enables `FileLogger` output & `"Debug"` pill in `HomeActivity` |
| `show_article_thumbnails` | `Boolean` | `true` | Toggle article thumbnail rendering in `ArticleAdapter` |
| `show_images_in_article_view` | `Boolean` | `true` | Toggle `loadsImagesAutomatically` in `ArticleViewerActivity` |
| `show_hidden_articles` | `Boolean` | `false` | Expose `"Hidden"` filter pill in `HomeActivity` |
| `enforce_plugin_filters` | `Boolean` | `true` | Enforce `dropIfCategoryIn` / `dropIfTagIn` classification rules |
| `gemma_model_path` | `String?` | `null` | Local path/URI to downloaded Gemma `.gguf` / `gemma.bin` |
| `gemma_model_url` | `String` | `https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf` | Model download URL |
| `enabled_skill_ids` | `Set<String>?` | `null` | Enabled AI skill IDs (`null` = all enabled) |

### On-Device Storage Paths & Backup Seed Format

- **Room SQLite DB:** `/data/data/com.example.offlinebrowser/databases/offline_browser_database` (Schema v11).
- **Linked ZIM Prefs:** `SharedPreferences("linked_files_prefs")` key `linked_files_list`.
- **Internal Files (`context.filesDir`):** `plugins/*.json`, `images/<md5>.jpg`, `models/gemma.bin`, `ai_skills.json`, `summary_pipeline_config.json`, `debug_log.txt`, `debug_log.1.txt`.
- **External App Files (`getExternalFilesDir(null)`):** Downloaded/imported `.zim` files (`<title>.zim`), `DownloadManager` `Downloads/gemma.bin`.
- **Exported `offline_browser_config.json` / ZIP `config.json` format** (importable in `SettingsActivity`):

```json
{
  "settings": {
    "wifi_only": true,
    "refresh_interval": 60,
    "feed_limit_count": 50,
    "feed_limit_days": 30,
    "weather_units": "metric",
    "show_hidden_articles": false,
    "enforce_plugin_filters": true
  },
  "feeds": [
    {
      "url": "https://www.mlive.com/arc/outboundfeeds/rss/?outputType=xml",
      "title": "MLive",
      "type": "RSS",
      "download_limit": 5,
      "category": "News"
    }
  ],
  "weather_locations": [
    { "location_name": "Toledo", "latitude": 41.6528, "longitude": -83.5379 }
  ]
}
```

---

## 5. Usage Guide

### Build, Test & Deploy Commands

```bash
# Run pure-JVM unit tests (debug + release)
./gradlew test --no-daemon

# Build debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug --no-daemon

# Install and launch on connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.offlinebrowser/.HomeActivity
```

*Observed `./gradlew test --no-daemon` result:*
```text
> Task :app:testDebugUnitTest
> Task :app:testReleaseUnitTest
> Task :app:test
BUILD SUCCESSFUL in 4m 40s
54 actionable tasks: 54 executed
```

### UI Navigation & Operational Workflows
- **Home (`HomeActivity`):** Search articles via `sv_articles`, switch category/`Weather`/`Hidden`/`Debug` pills, long-press status dot to whitelist current WiFi SSID, long-press article cards for Favorite/Read/Hide/Change Category, or pull to refresh local DB views.
- **Reader (`ArticleViewerActivity`):** Displays cached HTML offline; shows `"Partial Preview Cached"` banner (`btn_open_in_browser` / `btn_retry_download`) if uncached/empty; bottom bar triggers Gemma summary (`nav_gemma`) and `Settings` (`nav_settings`); FAB opens Plugin Options (`Create`, `Import`, `Apply`, `Edit`, `Search for Plugin automatically`).
- **Feed & Content Hub (`FeedSettingsActivity` → `CustomFeedsActivity` / `SuggestedFeedsActivity`):** Subscribe to `RSS`/`MASTODON`/`HTML` feeds, browse curated CSVs in `sprillex/BackLine`, manage plugins (`PluginsActivity`), or open `BinderyActivity` (QR scan + ZIM download), `KiwixSearchActivity`, `LibraryActivity`, and `WeatherActivity`.
- **Settings & AI (`SettingsActivity` → `AiSettingsActivity`):** Configure sync policy, export/import `offline_browser_config.json` or full `.zip` backup (settings + feeds + weather + `plugins/*.json`), export `debug_log.txt`, download Gemma models, sync `ai_skills.json` from GitHub, and CRUD skills.

---

## 6. Troubleshooting

| Symptom / Error | Root Cause | Fix |
| :--- | :--- | :--- |
| `Unsupported class file major version 65` or `jlink` error | Gradle invoked under Java 21+ instead of JDK 17 | Set `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` and re-run `./gradlew` |
| `SDK location not found` | `ANDROID_HOME` / `ANDROID_SDK_ROOT` unset and no `local.properties` | Export `ANDROID_HOME=/opt/android-sdk` or add `sdk.dir=/opt/android-sdk` to `local.properties` |
| `Server <ip>:<port> is not trusted. Please scan QR code to add trust.` | Bindery server missing from `trusted_servers` | Scan the Bindery node QR code (`{"ip","port","hash"}`) in `BinderyActivity` |
| `Certificate pinning failure. Expected: ..., Found: ...` | Leaf TLS certificate SHA-256 on Bindery node changed | Re-scan updated Bindery QR code in `BinderyActivity` |
| `SyncWorker` returns `Result.retry()` / skips sync | `wifi_only=true` without WiFi or SSID not in `allowed_wifi_ssids` / missing location permission | Grant `ACCESS_FINE_LOCATION`, connect to whitelisted SSID, or disable `wifi_only` in `SettingsActivity` |
| `Could not download full article. Open in browser to read.` | Site blocked both OkHttp & `WebViewFetcher` (403/503/bot challenge) or matched `paywalled` filter | Tap `Open full article in browser` or toggle `enforce_plugin_filters` in `SettingsActivity` |
| `WiFi is required to re-download` | Manual retry/apply plugin invoked off WiFi while `wifi_only=true` | Connect to WiFi or uncheck WiFi-only in `SettingsActivity` |
| `Failed to update AI Skills: HTTP 404` / network error | GitHub raw/API unreachable or rate-limited | Retry later or paste custom skill JSON directly in `AiSettingsActivity` |
