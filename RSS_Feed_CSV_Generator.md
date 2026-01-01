# RSS Feed CSV Generator 📡

A robust tool designed to generate, rank, and clean RSS feed datasets based on specific categories and languages. This tool automatically validates feed content, detects article types (headlines vs. full text), and ensures strictly formatted CSV output.

## 🚀 Key Features

* **Automated Content Detection:** Distinguishes between "Headlines" and "Full Articles" by analyzing feed tags and word counts.
* **Smart Sanitization:** Strips HTML, escapes characters, and removes line breaks to prevent CSV breakage.
* **Error Resilience:** Automatically logs and skips dead or unreachable feeds without crashing the process.
* **Standardized Output:** Organizes files into a strict ISO-language/Category directory structure.

---

## ⚙️ Configuration Variables

| Variable | Default | Description |
| :--- | :--- | :--- |
| **`{category}`** | *None* | **Required.** The specific industry or topic (e.g., `Tech`, `Politics`). If missing, the system prompts the user. |
| **`{lang_code}`** | `en` | The 2-letter ISO 639-1 code (e.g., `en`, `es`). Full names (e.g., "French") are auto-converted to codes (`fr`). |
| **`{list}`** | `top_100` | The identifier for the source ranking list or search depth. |

---

## 📂 Output Structure

The system automatically creates the required directory tree if it does not exist:

```text
rss_feeds/
└── {lang_code}/
    └── {category}/
        └── {lang_code}_{list}_{category}.csv
