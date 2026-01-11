package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.CopyOnWriteArrayList
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ScraperEngine {
    // Store pairs of Recipe and its compiled Regex
    private val recipes = CopyOnWriteArrayList<Pair<ScraperRecipe, Regex>>()
    private val gson = Gson()

    fun loadRecipes(newRecipes: List<ScraperRecipe>) {
        recipes.clear()
        // Compile regexes upfront
        newRecipes.forEach { recipe ->
            addRecipe(recipe)
        }
    }

    fun addRecipe(recipe: ScraperRecipe) {
        try {
            val regex = Regex(recipe.domainPattern)
            recipes.add(recipe to regex)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun process(url: String, html: String, rssImageUrl: String? = null): String? {
        // Iterate through the cache of compiled regexes
        val match = recipes.find { (_, regex) -> regex.containsMatchIn(url) }
            ?: return null

        val recipe = match.first

        return try {
            when (recipe.strategy) {
                ExtractionStrategy.EXTRACT_FROM_JS_VAR -> extractFromJsVar(html, recipe, rssImageUrl)
                ExtractionStrategy.CSS_SELECTOR -> extractFromCssSelector(html, recipe, rssImageUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractFromCssSelector(html: String, recipe: ScraperRecipe, rssImageUrl: String? = null): String? {
        val doc = Jsoup.parse(html)

        if (!recipe.removeSelectors.isNullOrEmpty()) {
            removeElementsAndEmptyParents(doc, recipe.removeSelectors)
        }

        val title = if (recipe.titlePath != null) {
            doc.select(recipe.titlePath).first()?.text() ?: "No Title"
        } else {
            "No Title"
        }

        val bodyElement = doc.select(recipe.contentPath).first() ?: return null
        val body = bodyElement.html()

        return buildSimpleHtml(title, body, if (recipe.injectRssImage) rssImageUrl else null, recipe.sourceName)
    }

    fun extractImage(html: String): String? {
        val doc = Jsoup.parse(html)
        // 1. Check OpenGraph image
        val ogImage = doc.select("meta[property=og:image]").attr("content")
        if (ogImage.isNotEmpty()) return ogImage

        // 2. Check Twitter card image
        val twitterImage = doc.select("meta[name=twitter:image]").attr("content")
        if (twitterImage.isNotEmpty()) return twitterImage

        // 3. Find first significant image in body
        // We look for img tags with src, filtering out small icons/pixels if possible
        // This is a heuristic.
        val images = doc.select("img[src]")
        for (img in images) {
            val src = img.attr("src")
            // Simple heuristic: skip very small or common icon names if possible, but for now just take the first valid one.
            // Maybe check width/height attributes if available.
            val width = img.attr("width").toIntOrNull()
            val height = img.attr("height").toIntOrNull()
            if ((width == null || width > 50) && (height == null || height > 50)) {
                if (src.isNotEmpty()) return src
            }
        }
        return null
    }

    private fun extractFromJsVar(html: String, recipe: ScraperRecipe, rssImageUrl: String? = null): String? {
        // More robust finding of the variable assignment:
        // 1. Find the identifier
        val idIndex = html.indexOf(recipe.targetIdentifier)
        if (idIndex == -1) return null

        // 2. Find the '=' assignment operator after the identifier
        val equalsIndex = html.indexOf("=", idIndex + recipe.targetIdentifier.length)
        if (equalsIndex == -1) return null

        // 3. Find the start of the JSON object '{' after the '='
        val jsonStartIndex = html.indexOf("{", equalsIndex)
        if (jsonStartIndex == -1) return null

        // We can try to count braces to find the end.
        var braceCount = 0
        var jsonEndIndex = -1
        var inString = false
        var escape = false

        for (i in jsonStartIndex until html.length) {
            val char = html[i]
            if (escape) {
                escape = false
                continue
            }
            if (char == '\\') {
                escape = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (char == '{') braceCount++
                if (char == '}') {
                    braceCount--
                    if (braceCount == 0) {
                        jsonEndIndex = i + 1
                        break
                    }
                }
            }
        }

        if (jsonEndIndex == -1) return null

        val jsonString = html.substring(jsonStartIndex, jsonEndIndex)

        val jsonElement = JsonParser.parseString(jsonString)
        val title = traversePath(jsonElement, recipe.titlePath) ?: "No Title"
        var body = traversePath(jsonElement, recipe.contentPath) ?: return null

        if (!recipe.removeSelectors.isNullOrEmpty()) {
            val bodyDoc = Jsoup.parseBodyFragment(body)
            removeElementsAndEmptyParents(bodyDoc, recipe.removeSelectors)
            body = bodyDoc.body().html()
        }

        return buildSimpleHtml(title, body, if (recipe.injectRssImage) rssImageUrl else null, recipe.sourceName)
    }

    private fun removeElementsAndEmptyParents(root: Element, selectors: List<String>) {
        selectors.forEach { selector ->
            val elements = root.select(selector)
            for (element in elements) {
                var parent = element.parent()
                element.remove()

                // Recursively remove empty parents
                // We check if the parent has no text (whitespace is ignored) and no significant children elements.
                while (parent != null && parent != root && isEffectivelyEmpty(parent)) {
                    val nextParent = parent.parent()
                    parent.remove()
                    parent = nextParent
                }
            }
        }
    }

    private fun isEffectivelyEmpty(element: Element): Boolean {
        if (element.hasText()) return false
        // Check children: if any child is NOT a <br> and NOT empty itself, then the element is not empty
        for (child in element.children()) {
            if (child.tagName().equals("br", ignoreCase = true)) continue
            if (!isEffectivelyEmpty(child)) return false
        }
        return true
    }

    private fun traversePath(element: JsonElement, path: String?): String? {
        if (path == null) return null
        val parts = path.split(".")
        var current: JsonElement = element

        for (part in parts) {
            if (!current.isJsonObject && !current.isJsonArray) return null

            if (part.contains("[")) {
                // Handle array index e.g. "articles[0]"
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

    private fun buildSimpleHtml(title: String, body: String, imageUrl: String? = null, sourceName: String? = null): String {
        val safeImageUrl = imageUrl?.replace("\"", "&quot;")
        val imageHtml = if (safeImageUrl != null) "<img src=\"$safeImageUrl\" alt=\"Article Image\" style=\"width:100%; height:auto; margin-bottom:16px;\" /><br/>" else ""
        val sourceHtml = if (sourceName != null) "<p style=\"color: #666; font-size: 0.9em; margin-bottom: 8px;\">$sourceName</p>" else ""
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: sans-serif; line-height: 1.6; padding: 16px; color: #333; background-color: #fff; }
                    h1 { font-size: 24px; margin-bottom: 16px; }
                    img { max-width: 100%; height: auto; }
                    @media (prefers-color-scheme: dark) {
                        body { color: #eee; background-color: #121212; }
                        a { color: #8ab4f8; }
                    }
                </style>
            </head>
            <body>
                $sourceHtml
                <h1>$title</h1>
                $imageHtml
                <div class="content">$body</div>
            </body>
            </html>
        """.trimIndent()
    }
}
