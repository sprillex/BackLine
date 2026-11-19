# Offline Browser API & Module Reference

Compact reference for external network endpoints, data exchange schemas, Android Activity/WorkManager contracts, and internal Kotlin APIs. See [ARCHITECTURE.md](./ARCHITECTURE.md) for invariants and [README.md](./README.md) for setup.

---

## 1. External Network & REST Endpoints

No authentication tokens are stored or sent in the current codebase; local Bindery nodes use SHA-256 leaf-certificate pinning via `SafeClientFactory` (`FingerprintTrustManager` + `trusted_servers`).

| Service | Method & URL | Request / Headers | Response Schema | Error / Fallback |
| :--- | :--- | :--- | :--- | :--- |
| **Open-Meteo** | `GET https://api.open-meteo.com/v1/forecast` | Query: `latitude`, `longitude`, `current_weather=true`, `daily`, `hourly`, `forecast_days=7` (`1..16`), `timezone=auto` | `WeatherResponse` JSON stored verbatim in `weather.dataJson` | Caught in `WeatherRepository.updateWeather`; preserves cached row |
| **Kiwix OPDS** | `GET https://library.kiwix.org/catalog/v2/entries` | Query: `q` | Atom/OPDS XML (`<entry>` with acquisition `<link type="application/x-zim">`) | `Result.failure(e)` from `KiwixRepository.search` |
| **The Bindery (Local TLS)** | `GET https://<ip>:<port>/` & `GET /{zim_filename}` | Custom `SafeClientFactory` (`OkHttpClient`) pinned against `trusted_servers` | HTML with `div.module` (`h3`, `p`, `a` containing `"Download ZIM"`); binary `.zim` stream | `CertificateException` if untrusted/mismatch; `ZimDownloadWorker` returns `Result.failure` |
| **GitHub Tree (BackLine)** | `GET https://api.github.com/repos/sprillex/BackLine/git/trees/main?recursive=1` | Default OkHttp headers | `{ sha, tree: [{ path, type, sha, size, url }], truncated }` | Returns `false` in `PluginSearchUtil` |
| **GitHub Tree (FiveFilters)** | `GET https://api.github.com/repos/fivefilters/ftr-site-config/git/trees/master?recursive=1` | Default OkHttp headers | `{ sha, tree: [{ path, type, ... }] }` | Returns `false` in `PluginSearchUtil` |
| **GitHub Contents API** | `GET https://api.github.com/repos/{owner}/{repo}/contents/{path}` | `SuggestedFeedRepository` (`rss_feeds` or `fivefilters/ftr-site-config`); `AiSkillManager` (`User-Agent: OfflineBrowserApp/1.0`) | `GitHubContent[]` or single file metadata `{ download_url }` | `IOException` on non-2xx; `AiSkillManager` tries fallback URLs |
| **GitHub Raw CDN** | `GET https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}` | `User-Agent: OfflineBrowserApp/1.0` (for AI skills) | `ScraperRecipe` JSON, FiveFilters `.txt`, CSV feed list, or `AiSkillRegistry` JSON | Falls back to bundled assets / defaults |
| **Google Favicons** | `GET https://www.google.com/s2/favicons?domain={host}&sz=64` | Loaded by Glide (`ArticleAdapter`, `FeedAdapter`) | PNG icon | Falls back to `R.mipmap.ic_launcher` |
| **HuggingFace Model CDN** | `GET {gemma_model_url}` | `DownloadManager` or `OkHttpClient` (`GemmaManager`); default `google_gemma-3-1b-it-Q4_K_M.gguf` | Binary `.gguf` / `gemma.bin` | `downloadModelDirect` writes local stub file on offline/HTTP failure |
| **RSS / Article Pages** | `GET {sanitizedUrl}` | Chrome Android `User-Agent`, `Accept`, `Sec-Fetch-*` headers; `SessionCookieJar` | RSS/Atom XML (`RssParser`) or HTML (`HtmlDownloader`) | `403`/`503` triggers `WebViewFetcher` headless fallback (`15s` timeout) |

### Internal WebView URI Scheme (`ArticleViewerActivity`)
- `app://retry-download` (`ArticleViewerActivity.RETRY_DOWNLOAD_URI`): Intercepted in `shouldOverrideUrlLoading`; triggers `retryArticleDownload()`.
- `http://*` / `https://*`: Blocked in `shouldInterceptRequest` (returns empty `text/plain` `200`); link clicks open external browser via `Intent.ACTION_VIEW` after `HtmlDownloader.sanitizeUrl()`.

---

## 2. Data Exchange & Configuration Schemas

```typescript
// Scraper Plugin (plugins/**/*.json, filesDir/plugins/*.json)
interface ScraperRecipe {
  domainPattern: string;                  // Regex matched against article URL
  strategy: "EXTRACT_FROM_JS_VAR" | "CSS_SELECTOR" | "XPATH";
  targetIdentifier?: string;              // JS global var name for EXTRACT_FROM_JS_VAR (default "")
  contentPath: string[];                  // Fallback CSS/XPath selectors or dot/bracket JSON paths
  titlePath?: string[];                   // Fallback title paths
  injectRssImage?: boolean;               // Default false
  removeSelectors?: string[];             // CSS or XPath selectors stripped with empty parents
  sourceName?: string;                    // Optional display header
  version?: number;                       // Default 1; bundled v2 recipes auto-upgrade v1
  summaryTruncateAfter?: string;          // Truncate text before marker prior to AI/excerpt
  summaryStripRegexes?: string[];         // Multiline regexes stripped from summary text
  classification?: ClassificationConfig;
}

interface ClassificationConfig {
  defaultCategory?: string;               // Default "News"
  rssRules?: ClassificationRule[];        // Stage 1: URL_REGEX, RSS_CATEGORIES, RSS_FIELD_EQUALS, RSS_FIELD_REGEX, RSS_MEDIA_COUNT
  pageRules?: ClassificationRule[];       // Stage 2: JS_VAR_PATH, HTML_REGEX, TEXT_REGEX
  categoryMap?: Record<string, string>;   // Case-insensitive raw -> canonical category map
  dropIfCategoryIn?: string[];            // Marks article isHidden=true when enforcePluginFilters=true
  dropIfTagIn?: string[];
}

interface ClassificationRule {
  source?: "URL_REGEX" | "RSS_CATEGORIES" | "RSS_FIELD_EQUALS" | "RSS_FIELD_REGEX" | "RSS_MEDIA_COUNT" | "JS_VAR_PATH" | "HTML_REGEX" | "TEXT_REGEX";
  pattern?: string; flags?: string;       // Regex pattern + "i"|"m"|"s"
  categoryGroup?: number; subcategoryGroup?: number;
  field?: string; value?: string;         // field: url|link|title|author|creator|content_html|content|description|summary
  minCount?: number; assignTag?: string;
  categoryPath?: string; subcategoryPath?: string; // JSON dot/array path for JS_VAR_PATH
}

// AI Skill Registry (aiskills/ai_skills.json, filesDir/ai_skills.json)
interface AiSkillRegistry { version: number; skills: AiSkill[]; }
interface AiSkill {
  id: string; displayName: string; summary: string;
  targetScreens: string[]; version: number; steps: SkillStepConfig[];
}
interface SkillStepConfig {
  stepId: string; promptTemplate: string; // Placeholders: {{INPUT}}, {{RAW_INPUT}}, {{CLEANED_ARTICLE_TEXT}}, {{STEP_N_OUTPUT}}, {{STEPID}}
  temperature?: number;                   // Default 0.2
  maxTokens?: number;                     // Default 150
  repeatPenalty?: number;                 // Default 1.15
  stopSequences?: string[];               // Default ["<end_of_turn>"]
}

// Summary Pipeline Config (assets/summary_pipeline_config.json, filesDir/summary_pipeline_config.json)
interface PipelineConfig { version: number; steps: SkillStepConfig[]; }

// App Config Export/Import (offline_browser_config.json & config.json inside backup ZIP)
interface AppConfig {
  settings: {
    wifi_only: boolean; refresh_interval: number; feed_limit_count: number;
    feed_limit_days: number; weather_units: "metric" | "imperial";
    show_hidden_articles?: boolean; enforce_plugin_filters?: boolean;
  };
  feeds: { url: string; title: string; type: "RSS" | "MASTODON" | "HTML"; download_limit: number; category: string | null; }[];
  weather_locations: { location_name: string; latitude: number; longitude: number; }[];
}

// Open-Meteo Weather Payload (weather.dataJson)
interface WeatherResponse {
  current_weather?: { temperature: number; windspeed: number; winddirection: number; weathercode: number; time: string; };
  hourly?: { time: string[]; temperature_2m: number[]; weathercode: number[]; };
  daily?: { time: string[]; temperature_2m_max: number[]; temperature_2m_min: number[]; weathercode: number[]; };
}

// Bindery QR Payload (scanned in BinderyActivity)
interface BinderyQrPayload { ip: string; port: number; hash: string; } // SHA-256 cert fingerprint
```

### Suggested Feeds CSV Schema (`rss_feeds/<lang>/<Category>/*.csv`)
Header: `name,category,language,rank,url,contentType` (quoted commas supported by `SuggestedFeedRepository.parseRemoteCsv`).

---

## 3. Activity Intents & WorkManager Contracts

| Component | Key / Extra | Type | Direction / Contract |
| :--- | :--- | :--- | :--- |
| `HomeActivity` | `EXTRA_CATEGORY`, `FEED_ID`, `OPEN_WEATHER`, `OPEN_CONTENT` | `String`, `Int`, `Boolean`, `Boolean` | Routes initial/re-delivered `ViewState` and `MainViewModel` filter |
| `ArticleViewerActivity` | `ARTICLE_ID` | `Int` | Loads `articles` row by PK; `-1` loads nothing |
| `EditFeedActivity` | `FEED_ID` | `Int` | Loads `feeds` row; calls `finish()` if `-1` or missing |
| `SettingsActivity` | `EXTRA_ADD_SSID` | `String` | Prompts user to append SSID to `allowed_wifi_ssids` |
| `PluginCreatorActivity` | `ARTICLE_URL`, `EXISTING_RECIPE_JSON` | `String`, `String` | Pre-populates domain regex and existing `ScraperRecipe` fields |
| `ZimViewerActivity` | `ZIM_FILE_PATH`, `ZIM_FILE_URI` | `String`, `String` | Opens local path or `/proc/self/fd/<fd>` via `ZimFile` + `ZimReader` |
| `ZimDownloadWorker` | Input: `url`, `title`; Progress: `progress`, `title`; Output: `error` | `String`, `Int` | Downloads `.zim` via `SafeClientFactory` to `getExternalFilesDir(null)/<title>.zim` |
| `FileImportWorker` | Input: `source_uri`, `file_name`; Progress: `progress`, `title`; Output: `error` | `String`, `Int` | Copies SAF URI stream to `getExternalFilesDir(null)/<file_name>` |
| `SyncWorker` | Unique periodic work `"SyncWorker"` | — | Enforces `wifiOnly` & `allowedWifiSsids`; syncs feeds & weather (`Result.retry()` if network unmet/exception) |

---

## 4. Public Kotlin Module Interfaces

### Network & Scraping (`com.example.offlinebrowser.data.network`)

| Symbol | Signature & Return | Contract / Exceptions |
| :--- | :--- | :--- |
| `HtmlDownloader.downloadHtml` | `suspend fun downloadHtml(url: String, rssImageUrl: String? = null): String?` | Delegates to `downloadHtmlWithMetadata(... )?.html`; returns `null` on failure/block/paywall |
| `HtmlDownloader.downloadHtmlWithMetadata` | `suspend fun downloadHtmlWithMetadata(url: String, rssImageUrl: String? = null, enforceFilters: Boolean = true): HtmlDownloadResult?` | Sanitizes URL; runs `PrimaryHttpFetcher` on `Dispatchers.IO`; falls back to `WebViewFetcher` on HTTP `403`/`503`; catches `HttpStatusException`/`Exception` |
| `HtmlDownloader.sanitizeUrl` | `fun sanitizeUrl(rawUrl: String): String` | Encodes unsafe URI chars (`|`, `[`, `]`, spaces, etc.) preserving valid `%XX` and IPv6 brackets |
| `HtmlDownloader.isBotBlockOrEmptyHtml` | `fun isBotBlockOrEmptyHtml(html: String?): Boolean` | Detects empty DOM or Cloudflare/anti-bot pages (`plainText.length < 1500` + challenge markers) |
| `ScraperEngine.loadRecipes` / `addRecipe` | `fun loadRecipes(newRecipes: List<ScraperRecipe>)` / `fun addRecipe(recipe: ScraperRecipe)` | Thread-safe `CopyOnWriteArrayList`; catches invalid `Regex` exceptions |
| `ScraperEngine.processWithMetadata` | `fun processWithMetadata(url: String, html: String, rssImageUrl: String? = null, enforceFilters: Boolean = true): ScrapeResult?` | Matches first `domainPattern`, executes `CSS_SELECTOR`/`XPATH`/`EXTRACT_FROM_JS_VAR`, runs Stage 2 `ArticleClassifier` |
| `WebViewFetcher.fetchHtml` | `suspend fun fetchHtml(url: String): WebViewFetchResult?` | Runs headless `WebView` on `Dispatchers.Main` (`15_000 ms` timeout, `1_500 ms` settle); always calls `destroy()` |
| `RssParser.fetchFeedItems` / `parseStream` | `suspend fun fetchFeedItems(feed: Feed): List<ParsedFeedItem>` / `fun parseStream(feed: Feed, inputStream: InputStream): List<ParsedFeedItem>` | Parses RSS/Atom via ROME (`SyndFeedInput`); `fetchFeedItems` catches exceptions and returns `emptyList()` |
| `FingerprintTrustManager` / `SafeClientFactory` | `fun create(context: Context): OkHttpClient` | Pins leaf cert SHA-256 hex against `trusted_servers`; throws `CertificateException` if untrusted/unknown host |

### Repositories (`com.example.offlinebrowser.data.repository`)

| Symbol | Signature & Return | Contract / Exceptions |
| :--- | :--- | :--- |
| `FeedRepository.addFeed` | `suspend fun addFeed(url: String, type: FeedType, downloadLimit: Int = 0, category: String? = null, allowDuplicate: Boolean = false): AddFeedResult` | Normalizes URL (`normalizeFeedUrl`); returns `Success(feedId)` or `Duplicate(existingFeed)` |
| `FeedRepository.syncFeed` | `suspend fun syncFeed(feed: Feed)` | Ensures bundled plugins loaded; ingests RSS/Mastodon items or HTML feed, applies limits, auto-downloads up to `feed.downloadLimit` |
| `ArticleRepository.downloadArticleContent` | `suspend fun downloadArticleContent(articleItem: ArticleListItem)` / `(article: Article)` | No-ops if `isCached`; downloads HTML and updates `content`, `isCached=true` |
| `ScraperPluginRepository.ensureDefaultPlugins` | `suspend fun ensureDefaultPlugins()` | Seeds/upgrades bundled recipes (`version` check) and removes legacy `toledo_blade.json` |
| `ScraperPluginRepository.loadAllRecipes` | `suspend fun loadAllRecipes(): List<ScraperRecipe>` | Parses all `*.json` in `pluginsDir`; skips malformed files |
| `ConfigRepository.exportConfig` / `importConfig` | `suspend fun exportConfig(outputStream: OutputStream)` / `importConfig(inputStream: InputStream)` | Serializes/restores `AppConfig` JSON without duplicating normalized feed URLs |
| `ConfigRepository.exportFullBackup` / `importFullBackup` | `suspend fun exportFullBackup(outputStream: OutputStream)` / `importFullBackup(inputStream: InputStream)` | Reads/writes ZIP containing `config.json` + `plugins/*.json` (basename-sanitized) |
| `KiwixRepository.search` | `suspend fun search(query: String): Result<List<KiwixBook>>` | Queries Kiwix OPDS XML and wraps errors in `Result.failure` |

### AI, Classification & Utilities (`com.example.offlinebrowser.util`)

| Symbol | Signature & Return | Contract / Exceptions |
| :--- | :--- | :--- |
| `ArticleClassifier.evaluateRss` | `fun evaluateRss(...): ClassificationResult` | Stage 1 evaluation (`URL_REGEX`, `RSS_CATEGORIES`, `RSS_FIELD_*`, `RSS_MEDIA_COUNT`, `categoryMap`, `dropIf*`) |
| `ArticleClassifier.evaluatePageJson` / `evaluatePageHtml` | `fun evaluatePageJson(...): ClassificationResult` / `fun evaluatePageHtml(...): ClassificationResult` | Stage 2 evaluation (`JS_VAR_PATH`, `HTML_REGEX`, `TEXT_REGEX`, `shouldDrop`) |
| `ArticleClassifier.applySummaryRules` | `fun applySummaryRules(text: String, recipe: ScraperRecipe?): String` | Truncates at `summaryTruncateAfter` and applies `summaryStripRegexes` |
| `AiSkillManager.executeSkill` | `suspend fun executeSkill(skill: AiSkill, initialInput: String, initialContext: Map<String, String> = emptyMap(), recipe: ScraperRecipe? = null): String` | Sanitizes HTML via `ArticleSummarizationPipeline`, executes multi-step prompts on `Dispatchers.Default`, cleans model turn tags |
| `AiSkillManager.updateSkillsFromGitHub` | `suspend fun updateSkillsFromGitHub(customUrl: String? = null): Result<Int>` | Fetches remote `ai_skills.json`, persists to `filesDir/ai_skills.json`, updates in-memory `registry` |
| `GemmaManager.generateSummary` | `suspend fun generateSummary(htmlOrTextContent: String, recipe: ScraperRecipe? = null): Pair<String, String>` | Runs `article_summarizer` skill if enabled, else `ArticleSummarizationPipeline`; returns `(summary, modelName)` |
| `ArticleSummarizationPipeline.sanitizeInput` | `fun sanitizeInput(rawText: String, recipe: ScraperRecipe? = null): String` | Strips `nav/header/footer/aside/script/style/poll/quiz` & short boilerplate lines (`>=40` chars, fallback `>=20`) |
| `FileLogger.log` / `readTail` / `exportTo` / `clear` | `fun log(message: String)` / `fun readTail(maxBytes: Int = 65_536, maxLines: Int = 500): String` | Thread-safe (`logLock`) rotating logger capped at `262_144` bytes per file (`debug_log.txt`, `debug_log.1.txt`) |
| `PluginSearchUtil.searchAndInstallPlugin` | `suspend fun searchAndInstallPlugin(context: Context, urlOrDomain: String): Boolean` | Searches `sprillex/BackLine` JSON plugins, then `fivefilters/ftr-site-config` `.txt` configs; saves to `filesDir/plugins/` |
