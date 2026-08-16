package com.example.offlinebrowser.data.model

enum class ExtractionStrategy {
    EXTRACT_FROM_JS_VAR,
    CSS_SELECTOR,
    XPATH
}

data class ScraperRecipe(
    val domainPattern: String,
    val strategy: ExtractionStrategy,
    val targetIdentifier: String, // e.g., the variable name "pgStoryZeroJSON"
    val contentPath: List<String>, // e.g., ["articles[0].body", "article.body"]
    val titlePath: List<String>? = null, // e.g., ["articles[0].title", "article.title"]
    val injectRssImage: Boolean = false, // If true, injects the RSS image at the top of the body
    val removeSelectors: List<String>? = null, // Selectors to remove from the content
    val sourceName: String? = null // Optional source name to display at the top
)
