package com.example.offlinebrowser.data.repository

import android.content.Context
import com.example.offlinebrowser.data.model.ClassificationConfig
import com.example.offlinebrowser.data.model.ClassificationRule
import com.example.offlinebrowser.data.model.ClassificationSource
import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScraperPluginRepository(
    private val pluginsDir: File,
    private val bundledPluginsProvider: () -> Map<String, String> = { defaultBundledPluginJsons() }
) {
    constructor(context: Context) : this(
        pluginsDir = File(context.filesDir, "plugins").apply { mkdirs() },
        bundledPluginsProvider = { loadBundledPluginJsonsFromAssets(context) }
    )

    private val gson = Gson()

    suspend fun loadAllRecipes(): List<ScraperRecipe> = withContext(Dispatchers.IO) {
        val recipes = mutableListOf<ScraperRecipe>()
        val files = pluginsDir.listFiles { _, name -> name.endsWith(".json") }

        files?.forEach { file ->
            try {
                val json = file.readText()
                val recipe = gson.fromJson(json, ScraperRecipe::class.java)
                if (recipe != null) {
                    recipes.add(recipe)
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log error but continue loading other plugins
            }
        }
        recipes
    }

    suspend fun deletePlugin(recipe: ScraperRecipe) = withContext(Dispatchers.IO) {
        val files = pluginsDir.listFiles { _, name -> name.endsWith(".json") }
        files?.forEach { file ->
            try {
                val json = file.readText()
                val loadedRecipe = gson.fromJson(json, ScraperRecipe::class.java)
                if (loadedRecipe == recipe) {
                    file.delete()
                    return@forEach
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun ensureDefaultPlugins() = withContext(Dispatchers.IO) {
        pluginsDir.mkdirs()

        // Cleanup legacy file with incorrect configuration
        val legacyFile = File(pluginsDir, "toledo_blade.json")
        if (legacyFile.exists()) {
            legacyFile.delete()
        }

        val bundledPlugins = bundledPluginsProvider()
        for ((fileName, bundledJson) in bundledPlugins) {
            val bundledRecipe = try {
                gson.fromJson(bundledJson, ScraperRecipe::class.java)
            } catch (e: Exception) {
                null
            } ?: continue

            val targetFile = File(pluginsDir, fileName)
            var hasNewerOrEqualAlternative = false

            // Upgrade or remove any stale file (e.g. plugin_<timestamp>.json) matching the same domainPattern
            val existingFiles = pluginsDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            for (existingFile in existingFiles) {
                if (existingFile.name == fileName) continue
                val existingRecipe = parseRecipeSafe(existingFile) ?: continue
                if (existingRecipe.domainPattern == bundledRecipe.domainPattern) {
                    if (existingRecipe.version < bundledRecipe.version) {
                        existingFile.delete()
                    } else {
                        hasNewerOrEqualAlternative = true
                    }
                }
            }

            if (hasNewerOrEqualAlternative) {
                continue
            }

            val shouldWrite = if (!targetFile.exists()) {
                true
            } else {
                val diskRecipe = parseRecipeSafe(targetFile)
                diskRecipe == null || diskRecipe.version < bundledRecipe.version
            }

            if (shouldWrite) {
                try {
                    targetFile.writeText(bundledJson)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun parseRecipeSafe(file: File): ScraperRecipe? {
        return try {
            gson.fromJson(file.readText(), ScraperRecipe::class.java)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val prettyGson = GsonBuilder().setPrettyPrinting().create()

        private fun loadBundledPluginJsonsFromAssets(context: Context): Map<String, String> {
            val merged = defaultBundledPluginJsons().toMutableMap()
            try {
                val assetFiles = context.assets.list("plugins").orEmpty()
                for (name in assetFiles) {
                    if (!name.endsWith(".json")) continue
                    try {
                        val json = context.assets.open("plugins/$name")
                            .bufferedReader()
                            .use { it.readText() }
                        if (json.isNotBlank()) {
                            merged[name] = json
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return merged
        }

        fun defaultBundledPluginJsons(): Map<String, String> {
            val toledoBlade = ScraperRecipe(
                domainPattern = ".*toledoblade\\.com",
                strategy = ExtractionStrategy.EXTRACT_FROM_JS_VAR,
                targetIdentifier = "pgStoryZeroJSON",
                contentPath = listOf("articles[0].body"),
                titlePath = listOf("articles[0].title"),
                injectRssImage = true,
                removeSelectors = listOf(
                    "iframe",
                    "script",
                    ".embed-container",
                    ".video-container",
                    ".iframe-wrapper",
                    "figure:has(iframe)",
                    "div.embed-youtube"
                ),
                sourceName = "The Toledo Blade",
                version = 2,
                classification = ClassificationConfig(
                    defaultCategory = "News",
                    rssRules = listOf(
                        ClassificationRule(
                            source = ClassificationSource.URL_REGEX,
                            pattern = "^https?://[^/]+/([^/]+)(?:/([^/]+))?/\\d{4}/",
                            categoryGroup = 1,
                            subcategoryGroup = 2
                        ),
                        ClassificationRule(
                            source = ClassificationSource.RSS_MEDIA_COUNT,
                            minCount = 6,
                            assignTag = "photo_gallery"
                        ),
                        ClassificationRule(
                            source = ClassificationSource.RSS_FIELD_REGEX,
                            field = "title",
                            pattern = "^photo gallery:",
                            flags = "i",
                            assignTag = "photo_gallery"
                        )
                    ),
                    pageRules = listOf(
                        ClassificationRule(
                            source = ClassificationSource.JS_VAR_PATH,
                            categoryPath = "articles[0].section",
                            subcategoryPath = "articles[0].subsection"
                        )
                    ),
                    categoryMap = mapOf(
                        "local" to "Local",
                        "news" to "News",
                        "business" to "Business",
                        "sports" to "Sports",
                        "opinion" to "Opinion",
                        "a-e" to "Entertainment"
                    ),
                    dropIfCategoryIn = listOf("Sports", "Opinion", "Entertainment"),
                    dropIfTagIn = listOf("photo_gallery")
                )
            )

            val mlive = ScraperRecipe(
                domainPattern = ".*mlive\\.com",
                strategy = ExtractionStrategy.CSS_SELECTOR,
                contentPath = listOf("article.entry-content", "div.article__body", "article", "main"),
                titlePath = listOf("h1.entry-title", "h1"),
                injectRssImage = true,
                removeSelectors = listOf(
                    "iframe",
                    "script",
                    "div.ad",
                    "div.widget",
                    "div.social-share",
                    ".share",
                    ".newsletter-signup"
                ),
                sourceName = "MLive",
                version = 2,
                classification = ClassificationConfig(
                    defaultCategory = "News",
                    rssRules = listOf(
                        ClassificationRule(
                            source = ClassificationSource.URL_REGEX,
                            pattern = "^https?://[^/]+/([^/]+)(?:/([^/]+))?/\\d{4}/\\d{2}/",
                            categoryGroup = 1,
                            subcategoryGroup = 2
                        ),
                        ClassificationRule(
                            source = ClassificationSource.RSS_FIELD_REGEX,
                            field = "content_html",
                            pattern = "(pc_aff_link=|skimresources\\.com|irmp=|catena|bettercollective)",
                            assignTag = "affiliate"
                        )
                    ),
                    categoryMap = mapOf(
                        "news" to "News",
                        "politics" to "News",
                        "public-interest" to "News",
                        "business" to "Business",
                        "weather" to "Weather",
                        "highschoolsports" to "Sports",
                        "sports" to "Sports",
                        "wolverines" to "Sports",
                        "spartans" to "Sports",
                        "lions" to "Sports",
                        "tigers" to "Sports",
                        "redwings" to "Sports",
                        "pistons" to "Sports",
                        "sports-betting" to "Gambling",
                        "online-casinos" to "Gambling",
                        "sweepstakes-casinos" to "Gambling",
                        "tv" to "Entertainment",
                        "a-e" to "Entertainment",
                        "shopping" to "Shopping",
                        "life" to "Lifestyle",
                        "opinion" to "Opinion",
                        "griffins" to "Sports"
                    ),
                    dropIfCategoryIn = listOf(
                        "Sports",
                        "Gambling",
                        "Entertainment",
                        "Shopping",
                        "Lifestyle",
                        "Opinion"
                    ),
                    dropIfTagIn = listOf("affiliate")
                )
            )

            val oscoda = ScraperRecipe(
                domainPattern = ".*oscodaherald\\.com",
                strategy = ExtractionStrategy.CSS_SELECTOR,
                contentPath = listOf("div.entry-content", "article .td-post-content", "article"),
                titlePath = listOf("h1.entry-title", "h1"),
                injectRssImage = true,
                removeSelectors = listOf(
                    "script",
                    "style",
                    "link",
                    "iframe",
                    "#checkout_form_wrapper",
                    ".jquery-modal",
                    ".wp-block-pdfemb-pdf-embedder-viewer"
                ),
                sourceName = "The Oscoda County Herald",
                version = 2,
                summaryTruncateAfter = "Login to continue reading",
                summaryStripRegexes = listOf(
                    "By\\s+[A-Za-z .'-]+?\\S+@\\S+\\.\\S+\\s*",
                    "^By\\s+[A-Z][a-z]+\\s+[A-Z][a-z]+\\s+"
                ),
                classification = ClassificationConfig(
                    defaultCategory = "Local",
                    rssRules = listOf(
                        ClassificationRule(source = ClassificationSource.RSS_CATEGORIES),
                        ClassificationRule(
                            source = ClassificationSource.RSS_FIELD_EQUALS,
                            field = "author",
                            value = "posteditor",
                            assignTag = "pdf_embed"
                        ),
                        ClassificationRule(
                            source = ClassificationSource.RSS_FIELD_REGEX,
                            field = "title",
                            pattern = "^Cartoon\\b",
                            assignTag = "cartoon"
                        )
                    ),
                    pageRules = listOf(
                        ClassificationRule(
                            source = ClassificationSource.HTML_REGEX,
                            pattern = "Login to continue reading|cherryroad-media-paywall",
                            flags = "i",
                            assignTag = "paywalled"
                        )
                    ),
                    categoryMap = mapOf(
                        "news" to "Local",
                        "classifieds" to "Classifieds"
                    ),
                    dropIfCategoryIn = listOf("Classifieds", "Sports", "Opinion"),
                    dropIfTagIn = listOf("pdf_embed", "cartoon", "paywalled")
                )
            )

            return mapOf(
                "toledoblade.json" to prettyGson.toJson(toledoBlade),
                "mlive.json" to prettyGson.toJson(mlive),
                "oscodaherald.json" to prettyGson.toJson(oscoda)
            )
        }
    }
}
