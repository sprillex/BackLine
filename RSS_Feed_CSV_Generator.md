# RSS Feed CSV Generator 📡

A robust tool designed to generate, rank, and clean RSS feed datasets based on specific categories and languages. This tool automatically validates feed content, detects article types (headlines vs. full text), and ensures strictly formatted CSV output.

## 🚀 Key Features

* **Automated Content Detection:** Distinguishes between "Headlines" and "Full Articles" by analyzing feed tags and word counts.
* **Smart Sanitization:** Strips HTML, escapes characters, and removes line breaks to prevent CSV breakage.
* **Error Resilience:** Automatically logs and skips dead or unreachable feeds without crashing the process.
* **https First** When given an http feed https is checked first. If https is available the secure url is stored instead of http.
* **Standardized Output:** Organizes files into a strict ISO-language/Category directory structure.

---

## ⚙️ Configuration Variables

| Variable | Default | Description |
| :--- | :--- | :--- |
| **`{category}`** | *None* | **Required.** The specific industry or topic (e.g., `Tech`, `Politics`). If missing, the system prompts the user. |
| **`{lang_code}`** | `en` | The 2-letter ISO 639-1 code (e.g., `en`, `es`). Full names (e.g., "French") are auto-converted to codes (`fr`). |
| **`{list}`** | `top_100` | The identifier for the source ranking list or search depth. |

---

## 📡 Data Acquisition Strategy

To populate the list, the system executes the following search logic based on the inputs:

1.  **Search Query:** Performs a web search for keywords: *"Top [rank] [category] RSS feeds"* (e.g., "Top 100 Tech RSS feeds").
2.  **Extraction:** Scrapes resulting pages to identify valid RSS feed links (`.xml`, `.rss`, or `/feed`).
3.  **Validation:** Tests each link. Only active (HTTP 200 OK) feeds are processed.
4.  **Ranking:** Feeds are ranked (1–100) based on the order they are discovered/verified.

---

## 📂 Output Structure

The system automatically creates the required directory tree if it does not exist:

```text
rss_feeds/
└── {lang_code}/
    └── {category}/
        └── {lang_code}_{list}_{category}.csv
