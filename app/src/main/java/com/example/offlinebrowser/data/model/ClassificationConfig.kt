package com.example.offlinebrowser.data.model

enum class ClassificationSource {
    URL_REGEX,
    RSS_CATEGORIES,
    RSS_FIELD_EQUALS,
    RSS_FIELD_REGEX,
    RSS_MEDIA_COUNT,
    JS_VAR_PATH,
    HTML_REGEX,
    TEXT_REGEX
}

data class ClassificationConfig(
    val defaultCategory: String = "News",
    val rssRules: List<ClassificationRule>? = null,
    val pageRules: List<ClassificationRule>? = null,
    val categoryMap: Map<String, String>? = null,
    val dropIfCategoryIn: List<String>? = null,
    val dropIfTagIn: List<String>? = null
)

data class ClassificationRule(
    val source: ClassificationSource? = null,
    val pattern: String? = null,
    val categoryGroup: Int? = null,
    val subcategoryGroup: Int? = null,
    val field: String? = null,
    val value: String? = null,
    val flags: String? = null,
    val minCount: Int? = null,
    val assignTag: String? = null,
    val categoryPath: String? = null,
    val subcategoryPath: String? = null
)
