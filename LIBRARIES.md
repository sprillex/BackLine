# Software Bill of Materials & Security Manifest

Dependency inventory, native/runtime requirements, and security boundaries parsed directly from `build.gradle.kts`, `app/build.gradle.kts`, `app/libs/zimdroid.aar`, and `app/src/main/AndroidManifest.xml`.

---

## 1. Machine-Readable Dependency Manifest (OSV-Compatible)

<!-- BEGIN_DEPENDENCY_MANIFEST -->
```json
[
  {
    "name": "com.android.application",
    "version": "8.2.0",
    "scope": "build",
    "ecosystem": "Gradle",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.android.library",
    "version": "8.2.0",
    "scope": "build",
    "ecosystem": "Gradle",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "org.jetbrains.kotlin.android",
    "version": "1.9.23",
    "scope": "build",
    "ecosystem": "Gradle",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.google.devtools.ksp",
    "version": "1.9.23-1.0.19",
    "scope": "build",
    "ecosystem": "Gradle",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.core:core-ktx",
    "version": "1.12.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.appcompat:appcompat",
    "version": "1.6.1",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.activity:activity-ktx",
    "version": "1.8.2",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.google.android.material:material",
    "version": "1.11.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.constraintlayout:constraintlayout",
    "version": "2.1.4",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.work:work-runtime-ktx",
    "version": "2.9.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.webkit:webkit",
    "version": "1.10.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.room:room-runtime",
    "version": "2.6.1",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.room:room-ktx",
    "version": "2.6.1",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.room:room-compiler",
    "version": "2.6.1",
    "scope": "build",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.squareup.retrofit2:retrofit",
    "version": "2.9.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.squareup.retrofit2:converter-gson",
    "version": "2.9.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.squareup.retrofit2:converter-scalars",
    "version": "2.9.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.squareup.okhttp3:okhttp",
    "version": "4.12.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.google.code.gson:gson",
    "version": "2.10.1",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "org.jsoup:jsoup",
    "version": "1.17.2",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "MIT",
    "direct": true
  },
  {
    "name": "com.rometools:rome",
    "version": "2.1.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "org.jetbrains.kotlinx:kotlinx-coroutines-android",
    "version": "1.7.3",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "com.google.android.gms:play-services-location",
    "version": "21.0.1",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Android-SDK",
    "direct": true
  },
  {
    "name": "com.google.android.gms:play-services-code-scanner",
    "version": "16.1.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Android-SDK",
    "direct": true
  },
  {
    "name": "com.dmitrybrant.zimdroid:zimdroid",
    "version": "0.0.14",
    "scope": "runtime",
    "ecosystem": "LocalAAR",
    "license": "Unknown",
    "direct": true
  },
  {
    "name": "com.android.tools:desugar_jdk_libs",
    "version": "2.0.4",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "GPL-2.0-with-classpath-exception",
    "direct": true
  },
  {
    "name": "com.github.bumptech.glide:glide",
    "version": "4.16.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "BSD-2-Clause",
    "direct": true
  },
  {
    "name": "androidx.swiperefreshlayout:swiperefreshlayout",
    "version": "1.1.0",
    "scope": "runtime",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "junit:junit",
    "version": "4.13.2",
    "scope": "test",
    "ecosystem": "Maven",
    "license": "EPL-1.0",
    "direct": true
  },
  {
    "name": "androidx.test.ext:junit",
    "version": "1.1.5",
    "scope": "test",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  },
  {
    "name": "androidx.test.espresso:espresso-core",
    "version": "3.5.1",
    "scope": "test",
    "ecosystem": "Maven",
    "license": "Apache-2.0",
    "direct": true
  }
]
```
<!-- END_DEPENDENCY_MANIFEST -->

---

## 2. Dependency Inventory

| Package | Version | Ecosystem | Scope | License | Consumed By |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `com.android.application` / `library` | `8.2.0` | Gradle | build | Apache-2.0 | `build.gradle.kts`, `app/build.gradle.kts` |
| `org.jetbrains.kotlin.android` | `1.9.23` | Gradle | build | Apache-2.0 | `build.gradle.kts`, `app/build.gradle.kts` |
| `com.google.devtools.ksp` | `1.9.23-1.0.19` | Gradle | build | Apache-2.0 | Room annotation processing (`ksp(...)`) |
| `androidx.core:core-ktx` | `1.12.0` | Maven | runtime | Apache-2.0 | `FileProvider`, `NotificationCompat`, `ContextCompat` |
| `androidx.appcompat:appcompat` | `1.6.1` | Maven | runtime | Apache-2.0 | All `AppCompatActivity` screens, `AlertDialog`, `SearchView` |
| `androidx.activity:activity-ktx` | `1.8.2` | Maven | runtime | Apache-2.0 | `viewModels()`, `ActivityResultContracts` |
| `com.google.android.material:material` | `1.11.0` | Maven | runtime | Apache-2.0 | `MaterialToolbar`, `FloatingActionButton`, `TextInputEditText` |
| `androidx.constraintlayout:constraintlayout` | `2.1.4` | Maven | runtime | Apache-2.0 | Activity & item XML layouts |
| `androidx.work:work-runtime-ktx` | `2.9.0` | Maven | runtime | Apache-2.0 | `OfflineBrowserApp`, `SyncWorker`, `ZimDownloadWorker`, `FileImportWorker` |
| `androidx.webkit:webkit` | `1.10.0` | Maven | runtime | Apache-2.0 | WebView compatibility support |
| `androidx.room:room-runtime` / `room-ktx` | `2.6.1` | Maven | runtime | Apache-2.0 | `OfflineDatabase`, DAOs, Kotlin `Flow` queries |
| `androidx.room:room-compiler` | `2.6.1` | Maven | build | Apache-2.0 | KSP Room DAO/database codegen |
| `com.squareup.retrofit2:retrofit` (+ `converter-gson`, `converter-scalars`) | `2.9.0` | Maven | runtime | Apache-2.0 | `WeatherRepository` (`WeatherService`), `KiwixRepository` (`KiwixService`) |
| `com.squareup.okhttp3:okhttp` | `4.12.0` | Maven | runtime | Apache-2.0 | `HtmlDownloader`, `SecurityModule`, `ZimDownloadWorker`, `SuggestedFeedRepository`, `AiSkillManager`, `PluginSearchUtil`, `GemmaManager`, `BinderyActivity` |
| `com.google.code.gson:gson` | `2.10.1` | Maven | runtime | Apache-2.0 | `ScraperPluginRepository`, `ConfigRepository`, `LinkedFileRepository`, `AiSkillManager`, `PipelineConfigLoader`, `ArticleClassifier`, `WeatherHomeAdapter` |
| `org.jsoup:jsoup` | `1.17.2` | Maven | runtime | MIT | `ScraperEngine`, `HtmlDownloader`, `ArticleClassifier`, `ArticleSummarizationPipeline`, `ArticleViewerActivity`, `BinderyActivity`, `PluginCreatorActivity`, `FeedRepository` |
| `com.rometools:rome` | `2.1.0` | Maven | runtime | Apache-2.0 | `RssParser`, `KiwixRepository` (OPDS XML parsing) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.7.3` | Maven | runtime | Apache-2.0 | `MainViewModel`, `WebViewFetcher`, repositories, workers |
| `com.google.android.gms:play-services-location` | `21.0.1` | Maven | runtime | Android-SDK | `WeatherActivity` (`FusedLocationProviderClient`) |
| `com.google.android.gms:play-services-code-scanner` | `16.1.0` | Maven | runtime | Android-SDK | `BinderyActivity` (`GmsBarcodeScanning`) |
| `com.dmitrybrant.zimdroid:zimdroid` (`app/libs/zimdroid.aar`) | `0.0.14` | LocalAAR | runtime | Unknown | `ZimViewerActivity` (`ZimFile`, `ZimReader`) |
| `com.android.tools:desugar_jdk_libs` | `2.0.4` | Maven | runtime | GPL-2.0-with-classpath-exception | `java.time` (`LocalDate`, `LocalDateTime`, `ZoneOffset`) on API 24–25 |
| `com.github.bumptech.glide:glide` | `4.16.0` | Maven | runtime | BSD-2-Clause | `ArticleAdapter`, `FeedAdapter` |
| `androidx.swiperefreshlayout:swiperefreshlayout` | `1.1.0` | Maven | runtime | Apache-2.0 | `HomeActivity` |
| `junit:junit` | `4.13.2` | Maven | test | EPL-1.0 | All unit tests under `app/src/test/java/` |
| `androidx.test.ext:junit` / `espresso-core` | `1.1.5` / `3.5.1` | Maven | test | Apache-2.0 | Instrumented test runner (`androidTestImplementation`) |

**Key Transitive Dependencies:** `org.jdom2:jdom2` (pulled by `com.rometools:rome`, also imported directly in `RssParser.kt` for `media:*` `foreignMarkup` parsing); `com.squareup.okio:okio` (pulled by `okhttp`); `androidx.sqlite:sqlite` & `androidx.room:room-common` (pulled by `room-runtime`); `androidx.lifecycle:*` (pulled by `activity-ktx`/`appcompat`).

---

## 3. System & Native Runtime Dependencies

- **Android Platform APIs:** `android.webkit.WebView` & `CookieManager` (`WebViewFetcher`, `ArticleViewerActivity`, `SessionCookieJar`), `android.app.DownloadManager` (`GemmaManager`), `ConnectivityManager` / `WifiManager` (`SyncWorker`, `HomeActivity`, `NetworkMonitor`), Android Storage Access Framework (`ActivityResultContracts.OpenDocument` / `CreateDocument` / `GetContent`).
- **Native Shared Libraries (`.so`):** None bundled in the repository (`app/libs/zimdroid.aar` contains `classes.jar` only and an empty `jni/` directory). No JNI/NDK C/C++ sources exist in `app/src/`.
- **Build Toolchain & Host Binaries (Non-App Runtime):** OpenJDK 17 (`JAVA_HOME`), Gradle 8.7 (`./gradlew`), Android SDK Platform `android-34`, Android SDK Build-Tools `34.0.0`, `adb` (`platform-tools`). The Android application itself executes **zero** host `subprocess` calls.

---

## 4. Network & Privilege Boundaries

- **Declared Android Permissions (`app/src/main/AndroidManifest.xml`):**
  - Install-time: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`.
  - Runtime: `ACCESS_FINE_LOCATION` (WiFi SSID check on Android 8.1+ & geolocation), `ACCESS_COARSE_LOCATION` (weather geolocation), `POST_NOTIFICATIONS` (WorkManager foreground download/import notifications on Android 13+).
- **Cleartext & TLS Policy:**
  - `android:usesCleartextTraffic="true"` is enabled in `AndroidManifest.xml` to support HTTP RSS feeds and local network endpoints.
  - **The Bindery (`SafeClientFactory` / `FingerprintTrustManager`):** Fail-closed custom `X509ExtendedTrustManager`. Rejects any connection missing socket/engine IP/port or lacking a matching `(ip, port)` or `(ip, 0)` SHA-256 leaf-certificate fingerprint in `trusted_servers`. Because `hostnameVerifier { _, _ -> true }` is used for raw IP/self-signed local certs, trust relies strictly on leaf SHA-256 pinning.
- **Inbound Listening Sockets:** None in production app code (`LocalTestHttpServer` and `ServerSocket(0)` in `HtmlDownloaderTest` / `AiSkillManagerTest` bind `127.0.0.1` only during JVM unit tests).
- **Outbound Network Endpoints:**
  - `https://api.open-meteo.com/v1/forecast` (Weather forecasts)
  - `https://library.kiwix.org/catalog/v2/entries` (Kiwix OPDS search + ZIM CDN links)
  - `https://<ip>:<port>/` (Pinned local Bindery servers)
  - `https://api.github.com/repos/{sprillex/BackLine,fivefilters/ftr-site-config}/...` & `https://raw.githubusercontent.com/...` (Feed CSVs, scraper plugins, AI skills)
  - `https://www.google.com/s2/favicons` (Feed/article favicons)
  - `https://huggingface.co/...` (Configurable `gemma_model_url`)
  - Arbitrary user-subscribed RSS/Mastodon/HTML feed URLs (`RssParser`, `HtmlDownloader`, `WebViewFetcher`, `ImageDownloader`).
- **Storage & Inter-App Sharing Boundaries:**
  - Private app storage (`context.filesDir`): `plugins/*.json`, `images/*.jpg`, `models/gemma.bin`, `ai_skills.json`, `debug_log.txt` / `debug_log.1.txt`, Room DB `offline_browser_database`, `SharedPreferences` (`offline_browser_prefs`, `linked_files_prefs`).
  - App-specific external storage (`getExternalFilesDir(null)`): downloaded/imported `.zim` files; exposed only via non-exported `FileProvider` (`${applicationId}.fileprovider`, `@xml/file_paths` `external-files-path="."`, `grantUriPermissions="true"`).
  - Offline `ArticleViewerActivity` `WebView` blocks all `http`/`https` subresource requests via `shouldInterceptRequest` and opens external links solely via `Intent.ACTION_VIEW`.
