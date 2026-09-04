# Offline Browser API & Interface Reference

This document provides a comprehensive reference for all external REST APIs, service interfaces, network protocols, and data exchange schemas utilized by the Offline Browser Android Application.

---

## 1. Overview

The Offline Browser application interacts with several external REST services, public content distribution networks (CDNs), and local network server instances to retrieve weather data, downloadable offline knowledge archives (ZIM files), RSS feed subscriptions, scraper plugins, AI skills, and remote LLM optimization capabilities.

### Base URLs & Service Interfaces

| Service Name | Base URL / Host | Protocol | Primary Purpose |
| :--- | :--- | :--- | :--- |
| **Open-Meteo API** | `https://api.open-meteo.com/` | HTTPS | Historical and multi-day weather forecasts |
| **Kiwix Library API** | `https://library.kiwix.org/` | HTTPS | Searching and fetching OPDS catalog entries for ZIM archives |
| **The Bindery (Local)** | `https://<ip>:<port>/` | HTTPS / Local TLS | Local node content discovery and direct ZIM downloading |
| **GitHub REST API** | `https://api.github.com/` | HTTPS | Plugin directory tree discovery and catalog browsing |
| **GitHub Raw CDN** | `https://raw.githubusercontent.com/` | HTTPS | Fetching raw JSON scraper plugins and AI skill registries |
| **Google Favicon Service** | `https://www.google.com/` | HTTPS | Fetching feed domain favicons |
| **Gemini AI REST API** | `https://generativelanguage.googleapis.com/` | HTTPS | Remote prompt optimization and AI skill refinement |

### Versioning Conventions

- **Open-Meteo API**: Explicit path versioning (`/v1/forecast`).
- **Kiwix OPDS API**: Path-based versioning (`/catalog/v2/entries`).
- **Gemini REST API**: Path versioning (`/v1beta/models/...`).
- **GitHub REST API**: Default API versioning via headers (`Accept: application/vnd.github.v3+json`).
- **Internal Configurations**: Schema versioning embedded within JSON fields (`"version": 1`).

---

## 2. Authentication & Security

### Key-Based Authentication

- **Gemini API**: Authenticated via query parameter `?key={GEMINI_API_KEY}` passed to the REST endpoint. The key is managed user-side via `PreferencesRepository` or input in `AiSettingsActivity`.

### TLS Certificate Pinning & Fingerprint Verification

- **The Bindery**: Local server connections utilize self-signed certificates. Trust is established by scanning a QR code containing the server IP, port, and SHA-256 certificate fingerprint. The app stores trusted hosts in `TrustedServerDao` and enforces custom `X509TrustManager` validation via `SecurityModule`.

### Request Headers

- **User-Agent**: External repository requests (e.g., GitHub API and FiveFilters site configs) send custom `User-Agent` headers (`OfflineBrowserApp/1.0`) to avoid rate limiting or generic request rejection.
- **Accept**: XML or JSON content types (`application/xml`, `application/json`, `application/atom+xml`).

---

## 3. Standard Envelopes & Error Handling

### HTTP Status Codes

- `200 OK`: Request succeeded. Response body contains payload (JSON, XML, or binary stream).
- `201 Created`: Resource successfully created or uploaded.
- `400 Bad Request`: Invalid parameter formats, missing required parameters, or malformed JSON payloads.
- `401 Unauthorized`: Missing or invalid API key (e.g., invalid Gemini API Key).
- `404 Not Found`: Requested resource, repository path, or catalog entry does not exist.
- `422 Unprocessable Entity`: Request syntax is valid, but internal validation failed (e.g., JSON schema validation error).
- `500 Internal Server Error`: Remote server error during generation or query execution.

### Generic JSON Error Format

```json
{
  "error": {
    "code": 400,
    "message": "Invalid location parameters: latitude must be between -90 and 90.",
    "status": "INVALID_ARGUMENT"
  }
}
```

---

## 4. Endpoints by Resource

### 4.1 Weather Forecast (Open-Meteo API)

#### `GET /v1/forecast`

Retrieves multi-day and hourly weather forecasts for a specified geographic coordinate pair.

##### Request Parameters

| Parameter | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `latitude` | `Double` | **Yes** | — | Latitude coordinate (-90.0 to 90.0). |
| `longitude` | `Double` | **Yes** | — | Longitude coordinate (-180.0 to 180.0). |
| `current_weather` | `Boolean` | No | `true` | Includes current weather snapshot. |
| `daily` | `String` | No | `temperature_2m_max,temperature_2m_min,weathercode` | Comma-separated daily metrics. |
| `hourly` | `String` | No | `temperature_2m,weathercode` | Comma-separated hourly metrics. |
| `forecast_days` | `Integer` | No | `7` | Number of forecast days (1 to 16). |
| `timezone` | `String` | No | `"auto"` | Target timezone string or auto-detect. |

##### Success Response (`200 OK`)

```json
{
  "latitude": 37.7749,
  "longitude": -122.4194,
  "timezone": "America/Los_Angeles",
  "current_weather": {
    "temperature": 18.5,
    "windspeed": 12.3,
    "winddirection": 240,
    "weathercode": 1,
    "time": "2025-02-15T12:00"
  },
  "daily": {
    "time": ["2025-02-15", "2025-02-16", "2025-02-17"],
    "temperature_2m_max": [20.1, 19.4, 18.0],
    "temperature_2m_min": [11.2, 10.8, 10.1],
    "weathercode": [1, 2, 3]
  },
  "hourly": {
    "time": ["2025-02-15T00:00", "2025-02-15T01:00"],
    "temperature_2m": [13.1, 12.8],
    "weathercode": [1, 1]
  }
}
```

---

### 4.2 Kiwix Catalog Search (Kiwix Service)

#### `GET /catalog/v2/entries`

Searches the central Kiwix library catalog for downloadable ZIM archives using OPDS (Open Publication Distribution System) XML feed format.

##### Request Parameters

| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `q` | `String` | **Yes** | Search term (e.g., `"wikipedia"`, `"ubuntu"`). |

##### Success Response (`200 OK`)

Returns OPDS Atom XML content containing entry links for acquisition:

```xml
<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
  <title>Kiwix Search Results</title>
  <entry>
    <id>urn:uuid:a1b2c3d4-e5f6-7890-1234-56789abcdef0</id>
    <title>Wikipedia in English</title>
    <summary>Simple English Wikipedia ZIM bundle</summary>
    <link rel="http://opds-spec.org/acquisition" href="https://download.kiwix.org/zim/wikipedia/wikipedia_en_simple_all_nopic_2024-01.zim" type="application/x-zim" length="1500000000"/>
  </entry>
</feed>
```

---

### 4.3 The Bindery Local Node Interface

#### `GET /`

Connects to a local Bindery instance to retrieve an HTML catalog of available ZIM modules.

##### Success Response (`200 OK`)

Returns HTML content parsed with Jsoup to extract `<a href="...">` download links and module labels.

#### `GET /{zim_filename}`

Streams binary `.zim` file content directly from the local Bindery node.

##### Success Response (`200 OK`)

Headers: `Content-Type: application/x-zim` or `application/octet-stream`. Returns binary file stream saved to internal storage by `ZimDownloadWorker`.

---

### 4.4 Repository File & Tree Discovery (GitHub API)

#### `GET /repos/{owner}/{repo}/git/trees/{branch}`

Discovers recursive directory structures to automatically find scraper plugins (`.json`) or site configurations (`.txt`).

##### Path Parameters

- `owner`: Repository owner (e.g., `sprillex`, `fivefilters`).
- `repo`: Repository name (e.g., `BackLine`, `ftr-site-config`).
- `branch`: Target git branch (e.g., `main`, `master`).

##### Query Parameters

| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `recursive` | `Integer` / `String` | No | Set to `1` or `true` for full tree recursion. |

##### Success Response (`200 OK`)

```json
{
  "sha": "9b12a831e5f8f...",
  "tree": [
    {
      "path": "plugins/en/Tech/wired.json",
      "mode": "100644",
      "type": "blob",
      "sha": "c1f2e3...",
      "size": 412,
      "url": "https://api.github.com/repos/sprillex/BackLine/git/blobs/c1f2e3..."
    }
  ],
  "truncated": false
}
```

#### `GET /repos/{owner}/{repo}/contents/{path}`

Lists directory contents or retrieves metadata for a specified path.

##### Success Response (`200 OK`)

```json
[
  {
    "name": "article_summarizer.json",
    "path": "aiskills/article_summarizer.json",
    "type": "file",
    "download_url": "https://raw.githubusercontent.com/sprillex/BackLine/main/aiskills/article_summarizer.json"
  }
]
```

---

### 4.5 Gemini Skill Optimization (Google Generative AI)

#### `POST /v1beta/models/gemini-2.5-flash:generateContent?key={GEMINI_API_KEY}`

Sends article context, execution outputs, and user critique to refine AI Skill prompt templates for small on-device models.

##### Headers

- `Content-Type: application/json`

##### Request Body Schema

```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "Optimize the prompt template for an AI skill named 'article_summarizer'..."
        }
      ]
    }
  ],
  "generationConfig": {
    "temperature": 0.2,
    "response_mime_type": "application/json"
  }
}
```

##### Success Response (`200 OK`)

```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "{\"id\":\"article_summarizer\",\"displayName\":\"Executive Summary\",\"summary\":\"Refined summary skill\",\"targetScreens\":[\"ArticleViewerActivity\"],\"version\":2,\"steps\":[{\"stepId\":\"extract_concrete_facts\",\"promptTemplate\":\"<start_of_turn>user\\nList 3 specific facts from:\\n{{INPUT}}<end_of_turn>\\n<start_of_turn>model\\n\",\"temperature\":0.15,\"maxTokens\":180,\"repeatPenalty\":1.15,\"stopSequences\":[\"<end_of_turn>\"]}]}"
          }
        ]
      }
    }
  ]
}
```

---

## 5. Data Exchange Schemas & Formats

### 5.1 Scraper Recipe Plugin Schema (`.json`)

Scraper plugins define DOM parsing rules used by `ScraperEngine` to extract full-text content and title elements from article webpages.

```json
{
  "domainPattern": "wired.com",
  "strategy": "CSS_SELECTOR",
  "targetIdentifier": "",
  "contentPath": [
    "div.body__container",
    "article",
    "main"
  ],
  "titlePath": [
    "h1[data-testid='ContentHeaderHed']",
    "h1"
  ],
  "injectRssImage": true,
  "removeSelectors": [
    "iframe",
    "script",
    "div.ad-wrapper",
    "div.social-share",
    ".newsletter-signup",
    ".paywall-element"
  ],
  "sourceName": "Wired"
}
```

#### Field Specifications

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `domainPattern` | `String` | **Yes** | Target domain name or regex pattern (e.g., `"techcrunch.com"`). |
| `strategy` | `Enum` | **Yes** | Extraction strategy: `CSS_SELECTOR`, `EXTRACT_FROM_JS_VAR`, or `XPATH`. |
| `targetIdentifier` | `String` | **Yes** | JS variable name if strategy is `EXTRACT_FROM_JS_VAR`, otherwise empty string. |
| `contentPath` | `List<String>` | **Yes** | Ordered list of fallback DOM selection paths for article content body. |
| `titlePath` | `List<String>` | No | Optional ordered list of fallback DOM selection paths for article title. |
| `injectRssImage` | `Boolean` | No | Default `false`. If `true`, injects RSS enclosure image into extracted body HTML. |
| `removeSelectors` | `List<String>` | No | CSS selectors to strip from HTML before rendering (ads, scripts, share buttons). |
| `sourceName` | `String` | No | Display name header inserted above extracted article content. |

---

### 5.2 AI Skill Registry Schema (`ai_skills.json`)

AI Skills define multi-step execution routines for local language models (such as Gemma GGUF via `LocalGemmaRunner`).

```json
{
  "version": 1,
  "skills": [
    {
      "id": "article_summarizer",
      "displayName": "Executive Summary",
      "summary": "Extracts core entity actions and produces an executive 2-bullet summary.",
      "targetScreens": [
        "ArticleViewerActivity"
      ],
      "version": 1,
      "steps": [
        {
          "stepId": "extract_concrete_facts",
          "promptTemplate": "<start_of_turn>user\nList 2 or 3 specific actions or decisions mentioned in the article below:\n\nArticle:\n\"\"\"\n{{INPUT}}\n\"\"\"<end_of_turn>\n<start_of_turn>model\n",
          "temperature": 0.15,
          "maxTokens": 180,
          "repeatPenalty": 1.15,
          "stopSequences": [
            "<end_of_turn>"
          ]
        },
        {
          "stepId": "synthesize_summary",
          "promptTemplate": "<start_of_turn>user\nRewrite these notes into 2 concise summary bullet points:\n\nNotes:\n{{INPUT}}<end_of_turn>\n<start_of_turn>model\n",
          "temperature": 0.2,
          "maxTokens": 120,
          "repeatPenalty": 1.15,
          "stopSequences": [
            "<end_of_turn>"
          ]
        }
      ]
    }
  ]
}
```

#### Field Specifications

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `version` | `Integer` | **Yes** | Registry schema version. |
| `skills` | `List<AiSkill>` | **Yes** | List of registered AI skill definitions. |
| `id` | `String` | **Yes** | Unique identifier string (e.g., `"article_summarizer"`). |
| `displayName` | `String` | **Yes** | Human-readable title displayed in application UI. |
| `summary` | `String` | **Yes** | Brief functional description of the skill. |
| `targetScreens` | `List<String>` | **Yes** | Screen Activity/Fragment class names where skill applies. |
| `steps` | `List<SkillStepConfig>` | **Yes** | Sequential execution steps for local or remote model inference. |

##### Step Configuration (`SkillStepConfig`)

| Field | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `stepId` | `String` | — | Unique identifier for step within the skill sequence. |
| `promptTemplate` | `String` | — | Prompt text template supporting variables like `{{INPUT}}` and `{{CLEANED_ARTICLE_TEXT}}`. |
| `temperature` | `Float` | `0.2` | Model sampling temperature (lower values yield deterministic output). |
| `maxTokens` | `Integer` | `150` | Maximum token generation limit for step output. |
| `repeatPenalty` | `Float` | `1.15` | Repetition penalty factor applied during generation. |
| `stopSequences` | `List<String>` | `["<end_of_turn>"]` | Stop token sequences indicating generation completion. |

---

### 5.3 Summary Pipeline Config Schema (`summary_pipeline_config.json`)

Defines sanitization rules and strip selectors applied during pre-processing of article HTML before passing to SLM models.

```json
{
  "version": 1,
  "sanitizeSelectors": [
    "nav",
    "header",
    "footer",
    "aside",
    "script",
    "style",
    ".poll",
    ".poll-container",
    "form",
    "[class*='poll']",
    "[id*='poll']",
    "[class*='quiz']"
  ],
  "ignoreLinesStartingWith": [
    "POLL",
    "VOTE",
    "WHICH KIND OF",
    "CLICK HERE",
    "SHARE THIS"
  ],
  "maxInputCharacters": 4000
}
```

---

### 5.4 Suggested Feeds CSV Schema (`.csv`)

Curated RSS/Atom feed lists stored in `rss_feeds/<lang>/<Category>/` utilize comma-separated values matching the header format below:

```csv
name,category,language,rank,url,contentType
TechCrunch,Tech,en,1,https://techcrunch.com/feed/,RSS
Wired Top Stories,Tech,en,2,https://www.wired.com/feed/rss,RSS
Ars Technica,Tech,en,3,https://feeds.arstechnica.com/arstechnica/index,RSS
```

| Header Field | Type | Description |
| :--- | :--- | :--- |
| `name` | `String` | Human-readable title of the publication or feed. |
| `category` | `String` | Target category string (e.g., `Tech`, `News`, `Science`). |
| `language` | `String` | Two-letter ISO language code (e.g., `en`). |
| `rank` | `Integer` | Numerical priority sequence (1, 2, 3...). |
| `url` | `String` | Valid RSS/Atom feed target URL. |
| `contentType` | `String` | Feed content classification (`RSS`, `ATOM`, or `HTML`). |

---

## 6. Pagination, Querying, & Limits

- **GitHub Git Trees API**: Query parameter `recursive=1` returns full directory trees in a single response, avoiding multiple HTTP roundtrips.
- **Background Article Sync**: Configurable per-feed download limit (`auto_download_limit`, default `5` articles per sync cycle) to manage device bandwidth and storage.
- **Weather Forecast Cache**: Offline weather data is retained in `OfflineDatabase` and cached for up to **10 days** before requiring fresh network re-synchronization.
