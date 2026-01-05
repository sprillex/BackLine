package com.example.offlinebrowser.data.model

enum class ExtractionStrategy {
    EXTRACT_FROM_JS_VAR,
    CSS_SELECTOR,
    // READABILITY
}

data class ScraperRecipe(
    val domainPattern: String,
    val strategy: ExtractionStrategy,
    val targetIdentifier: String, // e.g., the variable name "pgStoryZeroJSON"
    val contentPath: String,      // e.g., "articles[0].body"
    val titlePath: String? = null // e.g., "articles[0].title"
)
