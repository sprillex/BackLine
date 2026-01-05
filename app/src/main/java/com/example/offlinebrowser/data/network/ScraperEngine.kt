package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.CopyOnWriteArrayList

class ScraperEngine {
    private val recipes = CopyOnWriteArrayList<ScraperRecipe>()
    private val gson = Gson()

    fun loadRecipes(newRecipes: List<ScraperRecipe>) {
        recipes.clear()
        recipes.addAll(newRecipes)
    }

    fun addRecipe(recipe: ScraperRecipe) {
        recipes.add(recipe)
    }

    fun process(url: String, html: String): String? {
        // Use the pre-compiled regex from the recipe
        val recipe = recipes.find { it.regex.containsMatchIn(url) }
            ?: return null

        return try {
            when (recipe.strategy) {
                ExtractionStrategy.EXTRACT_FROM_JS_VAR -> extractFromJsVar(html, recipe)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractFromJsVar(html: String, recipe: ScraperRecipe): String? {
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
        val body = traversePath(jsonElement, recipe.contentPath) ?: return null

        return buildSimpleHtml(title, body)
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

    private fun buildSimpleHtml(title: String, body: String): String {
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
                <h1>$title</h1>
                <div class="content">$body</div>
            </body>
            </html>
        """.trimIndent()
    }
}
