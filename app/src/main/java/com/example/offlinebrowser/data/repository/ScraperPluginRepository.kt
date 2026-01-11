package com.example.offlinebrowser.data.repository

import android.content.Context
import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScraperPluginRepository(private val context: Context) {
    private val gson = Gson()
    private val pluginsDir: File by lazy {
        File(context.filesDir, "plugins").apply { mkdirs() }
    }

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
        // Cleanup legacy file with incorrect configuration
        val legacyFile = File(pluginsDir, "toledo_blade.json")
        if (legacyFile.exists()) {
            legacyFile.delete()
        }

        val toledoBladeFile = File(pluginsDir, "toledoblade.json")
        if (!toledoBladeFile.exists()) {
            val defaultRecipe = ScraperRecipe(
                domainPattern = ".*toledoblade\\.com",
                strategy = ExtractionStrategy.EXTRACT_FROM_JS_VAR,
                targetIdentifier = "pgStoryZeroJSON",
                contentPath = "articles[0].body",
                titlePath = "articles[0].title",
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
                sourceName = "The Toledo Blade"
            )
            try {
                toledoBladeFile.writeText(gson.toJson(defaultRecipe))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
