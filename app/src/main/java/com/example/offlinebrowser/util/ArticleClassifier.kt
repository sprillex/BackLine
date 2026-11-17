package com.example.offlinebrowser.util

import com.example.offlinebrowser.data.model.ClassificationConfig
import com.example.offlinebrowser.data.model.ClassificationSource
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.google.gson.JsonElement
import java.util.Locale

data class ClassificationResult(
    val category: String?,
    val subcategory: String?,
    val tags: List<String>,
    val shouldHide: Boolean
)

object ArticleClassifier {

    fun toTitleCase(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }
        }
    }

    fun normalizeCategory(raw: String?, categoryMap: Map<String, String>? = null): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!categoryMap.isNullOrEmpty()) {
            val mapped = categoryMap.entries
                .firstOrNull { it.key.trim().equals(trimmed, ignoreCase = true) }
                ?.value
            if (!mapped.isNullOrBlank()) {
                return mapped.trim()
            }
        }
        return toTitleCase(trimmed)
    }

    /**
     * Normalizes stored category strings for display/pill grouping while preserving
     * intentional mixed-case mappings (e.g., from categoryMap) and collapsing
     * all-uppercase or all-lowercase variants like "SPORTS" / "sports" -> "Sports".
     */
    fun normalizeDisplayCategory(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hasUpper = trimmed.any { it.isUpperCase() }
        val hasLower = trimmed.any { it.isLowerCase() }
        return if (!hasUpper || !hasLower) {
            toTitleCase(trimmed)
        } else {
            trimmed
        }
    }

    fun evaluateRss(
        url: String,
        title: String,
        author: String?,
        contentHtml: String,
        rssCategories: List<String>,
        mediaCount: Int,
        recipe: ScraperRecipe?,
        feedCategory: String?,
        enforceFilters: Boolean = true
    ): ClassificationResult {
        val config = recipe?.classification
        if (config == null) {
            return ClassificationResult(
                category = normalizeCategory(feedCategory),
                subcategory = null,
                tags = emptyList(),
                shouldHide = false
            )
        }

        var extractedCategory: String? = null
        var extractedSubcategory: String? = null
        val tags = linkedSetOf<String>()

        for (rule in config.rssRules.orEmpty()) {
            when (rule.source) {
                ClassificationSource.URL_REGEX -> {
                    val pattern = rule.pattern
                    if (!pattern.isNullOrEmpty()) {
                        val regex = compileRegex(pattern, rule.flags)
                        val match = regex?.find(url)
                        if (match != null) {
                            val catGroup = rule.categoryGroup
                            if (extractedCategory == null && catGroup != null && catGroup >= 0 && catGroup < match.groupValues.size) {
                                val g = match.groupValues[catGroup].trim()
                                if (g.isNotEmpty()) extractedCategory = g
                            }
                            val subGroup = rule.subcategoryGroup
                            if (extractedSubcategory == null && subGroup != null && subGroup >= 0 && subGroup < match.groupValues.size) {
                                val g = match.groupValues[subGroup].trim()
                                if (g.isNotEmpty()) extractedSubcategory = g
                            }
                        }
                    }
                }

                ClassificationSource.RSS_CATEGORIES -> {
                    if (extractedCategory == null && rssCategories.isNotEmpty()) {
                        extractedCategory = selectBestRssCategory(rssCategories, config)
                    }
                }

                ClassificationSource.RSS_FIELD_EQUALS -> {
                    val fieldValue = resolveRssField(rule.field, url, title, author, contentHtml)
                    val target = rule.value
                    if (fieldValue != null && target != null &&
                        fieldValue.trim().equals(target.trim(), ignoreCase = true)
                    ) {
                        val tag = rule.assignTag?.trim()
                        if (!tag.isNullOrEmpty()) tags.add(tag)
                    }
                }

                ClassificationSource.RSS_FIELD_REGEX -> {
                    val pattern = rule.pattern
                    val fieldValue = resolveRssField(rule.field, url, title, author, contentHtml)
                    if (!pattern.isNullOrEmpty() && fieldValue != null) {
                        val regex = compileRegex(pattern, rule.flags)
                        if (regex != null && regex.containsMatchIn(fieldValue)) {
                            val tag = rule.assignTag?.trim()
                            if (!tag.isNullOrEmpty()) tags.add(tag)
                        }
                    }
                }

                ClassificationSource.RSS_MEDIA_COUNT -> {
                    val minCount = rule.minCount
                    if (minCount != null && mediaCount >= minCount) {
                        val tag = rule.assignTag?.trim()
                        if (!tag.isNullOrEmpty()) tags.add(tag)
                    }
                }

                ClassificationSource.JS_VAR_PATH, null -> Unit
            }
        }

        val resolvedCategory = if (extractedCategory != null) {
            normalizeCategory(extractedCategory, config.categoryMap)
        } else {
            val defaultCat = (config.defaultCategory as String?)?.takeIf { it.isNotBlank() } ?: "News"
            normalizeCategory(defaultCat)
        }

        val tagList = tags.toList()
        val shouldHide = enforceFilters && shouldDrop(resolvedCategory, tagList, config)

        return ClassificationResult(
            category = resolvedCategory,
            subcategory = extractedSubcategory,
            tags = tagList,
            shouldHide = shouldHide
        )
    }

    fun evaluatePageJson(
        jsonElement: JsonElement,
        recipe: ScraperRecipe?,
        existingCategory: String? = null,
        existingSubcategory: String? = null,
        existingTags: List<String> = emptyList(),
        enforceFilters: Boolean = true
    ): ClassificationResult {
        val config = recipe?.classification
            ?: return ClassificationResult(existingCategory, existingSubcategory, existingTags, false)

        var extractedCategory: String? = null
        var extractedSubcategory: String? = null

        for (rule in config.pageRules.orEmpty()) {
            if (rule.source == ClassificationSource.JS_VAR_PATH) {
                if (extractedCategory == null && !rule.categoryPath.isNullOrBlank()) {
                    val rawCat = traverseJsonPath(jsonElement, rule.categoryPath)?.trim()
                    if (!rawCat.isNullOrEmpty()) {
                        extractedCategory = rawCat
                    }
                }
                if (extractedSubcategory == null && !rule.subcategoryPath.isNullOrBlank()) {
                    val rawSub = traverseJsonPath(jsonElement, rule.subcategoryPath)?.trim()
                    if (!rawSub.isNullOrEmpty()) {
                        extractedSubcategory = rawSub
                    }
                }
            }
        }

        val resolvedCategory = if (extractedCategory != null) {
            normalizeCategory(extractedCategory, config.categoryMap)
        } else {
            existingCategory ?: normalizeCategory(
                (config.defaultCategory as String?)?.takeIf { it.isNotBlank() } ?: "News"
            )
        }

        val resolvedSubcategory = extractedSubcategory ?: existingSubcategory
        val shouldHide = enforceFilters && shouldDrop(resolvedCategory, existingTags, config)

        return ClassificationResult(
            category = resolvedCategory,
            subcategory = resolvedSubcategory,
            tags = existingTags,
            shouldHide = shouldHide
        )
    }

    fun shouldDrop(
        category: String?,
        tags: List<String>,
        config: ClassificationConfig?
    ): Boolean {
        if (config == null) return false
        val categoryDropped = !category.isNullOrBlank() &&
            config.dropIfCategoryIn?.any { it.trim().equals(category.trim(), ignoreCase = true) } == true
        val tagDropped = tags.isNotEmpty() &&
            config.dropIfTagIn?.any { dropTag ->
                tags.any { it.trim().equals(dropTag.trim(), ignoreCase = true) }
            } == true
        return categoryDropped || tagDropped
    }

    fun applySummaryRules(text: String, recipe: ScraperRecipe?): String {
        if (recipe == null || text.isBlank()) return text
        var result = text

        val truncateMarker = recipe.summaryTruncateAfter
        if (!truncateMarker.isNullOrEmpty()) {
            val idx = result.indexOf(truncateMarker)
            if (idx >= 0) {
                result = result.substring(0, idx)
            }
        }

        for (pattern in recipe.summaryStripRegexes.orEmpty()) {
            if (pattern.isEmpty()) continue
            val regex = try {
                Regex(pattern, setOf(RegexOption.MULTILINE))
            } catch (e: Exception) {
                null
            }
            if (regex != null) {
                result = regex.replace(result, "")
            }
        }

        return result.trim()
    }

    fun applyStripRegexes(text: String, recipe: ScraperRecipe?): String {
        if (recipe == null || text.isBlank()) return text
        var result = text
        for (pattern in recipe.summaryStripRegexes.orEmpty()) {
            if (pattern.isEmpty()) continue
            val regex = try {
                Regex(pattern, setOf(RegexOption.MULTILINE))
            } catch (e: Exception) {
                null
            }
            if (regex != null) {
                result = regex.replace(result, "")
            }
        }
        return result.trim()
    }

    fun traverseJsonPath(element: JsonElement, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val parts = path.split(".")
        var current: JsonElement = element

        for (part in parts) {
            if (!current.isJsonObject && !current.isJsonArray) return null

            if (part.contains("[")) {
                val name = part.substringBefore("[")
                val index = part.substringAfter("[").substringBefore("]").toIntOrNull() ?: return null

                if (name.isNotEmpty()) {
                    if (!current.isJsonObject) return null
                    current = current.asJsonObject.get(name) ?: return null
                }

                if (current.isJsonArray && current.asJsonArray.size() > index) {
                    current = current.asJsonArray.get(index)
                } else {
                    return null
                }
            } else {
                if (!current.isJsonObject) return null
                current = current.asJsonObject.get(part) ?: return null
            }
        }

        return if (current.isJsonPrimitive) current.asString else null
    }

    private fun selectBestRssCategory(
        rssCategories: List<String>,
        config: ClassificationConfig
    ): String? {
        val clean = rssCategories.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null

        // If any category in the entry maps to a dropped category, pick it so drop rules catch it
        val droppedMatch = clean.firstOrNull { raw ->
            val normalized = normalizeCategory(raw, config.categoryMap)
            !normalized.isNullOrBlank() &&
                config.dropIfCategoryIn?.any { it.trim().equals(normalized, ignoreCase = true) } == true
        }
        if (droppedMatch != null) return droppedMatch

        // Otherwise prefer the first category explicitly mapped in categoryMap
        val mappedMatch = clean.firstOrNull { raw ->
            config.categoryMap?.keys?.any { it.trim().equals(raw, ignoreCase = true) } == true
        }
        return mappedMatch ?: clean.first()
    }

    private fun resolveRssField(
        field: String?,
        url: String,
        title: String,
        author: String?,
        contentHtml: String
    ): String? {
        return when (field?.lowercase(Locale.ROOT)?.trim()) {
            "url", "link" -> url
            "title" -> title
            "author", "creator" -> author
            "content_html", "content", "description", "summary" -> contentHtml
            else -> null
        }
    }

    private fun compileRegex(pattern: String, flags: String?): Regex? {
        return try {
            val options = mutableSetOf<RegexOption>()
            if (!flags.isNullOrEmpty()) {
                if (flags.contains('i', ignoreCase = true)) options.add(RegexOption.IGNORE_CASE)
                if (flags.contains('m', ignoreCase = true)) options.add(RegexOption.MULTILINE)
                if (flags.contains('s', ignoreCase = true)) options.add(RegexOption.DOT_MATCHES_ALL)
            }
            if (options.isEmpty()) Regex(pattern) else Regex(pattern, options)
        } catch (e: Exception) {
            null
        }
    }
}
