package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScraperPluginRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()

    @Test
    fun testEnsureDefaultPluginsUpgradesStaleV1ToledoBladeAndRemovesLegacyFile() = runBlocking {
        val pluginsDir = tempFolder.newFolder("plugins")

        // Create legacy file and stale v1 toledoblade.json (without classification rules or version >= 2)
        val legacyFile = File(pluginsDir, "toledo_blade.json").apply { writeText("{}") }
        val staleV1 = ScraperRecipe(
            domainPattern = ".*toledoblade\\.com",
            strategy = ExtractionStrategy.EXTRACT_FROM_JS_VAR,
            targetIdentifier = "pgStoryZeroJSON",
            contentPath = listOf("articles[0].body"),
            titlePath = listOf("articles[0].title"),
            injectRssImage = true,
            sourceName = "The Toledo Blade",
            version = 1,
            classification = null
        )
        val toledoFile = File(pluginsDir, "toledoblade.json").apply {
            writeText(gson.toJson(staleV1))
        }

        // Also place a stale v1 oscodaherald plugin saved under a timestamp filename
        val staleOscoda = """
            {
              "domainPattern": ".*oscodaherald\\.com",
              "strategy": "CSS_SELECTOR",
              "contentPath": ["div.entry-content"],
              "sourceName": "The Oscoda County Herald"
            }
        """.trimIndent()
        val staleTimestampFile = File(pluginsDir, "plugin_1700000000.json").apply {
            writeText(staleOscoda)
        }

        val repo = ScraperPluginRepository(pluginsDir)
        repo.ensureDefaultPlugins()

        assertFalse("Legacy toledo_blade.json should be deleted", legacyFile.exists())
        assertFalse("Stale timestamped oscoda file should be replaced", staleTimestampFile.exists())
        assertTrue(toledoFile.exists())

        val recipes = repo.loadAllRecipes()
        val upgradedToledo = recipes.firstOrNull { it.domainPattern == ".*toledoblade\\.com" }
        assertNotNull(upgradedToledo)
        assertTrue(upgradedToledo!!.version >= 2)
        assertNotNull(upgradedToledo.classification)
        assertTrue(upgradedToledo.classification!!.dropIfCategoryIn!!.contains("Sports"))

        val upgradedOscoda = recipes.firstOrNull { it.domainPattern == ".*oscodaherald\\.com" }
        assertNotNull(upgradedOscoda)
        assertTrue(upgradedOscoda!!.version >= 2)
        assertTrue(upgradedOscoda.classification?.dropIfTagIn?.contains("paywalled") == true)

        val mlive = recipes.firstOrNull { it.domainPattern == ".*mlive\\.com" }
        assertNotNull(mlive)
        assertTrue(mlive!!.version >= 2)
    }

    @Test
    fun testEnsureDefaultPluginsDoesNotOverwriteHigherUserVersion() = runBlocking {
        val pluginsDir = tempFolder.newFolder("plugins_custom")
        val customV3 = ScraperRecipe(
            domainPattern = ".*toledoblade\\.com",
            strategy = ExtractionStrategy.EXTRACT_FROM_JS_VAR,
            targetIdentifier = "customVar",
            contentPath = listOf("custom.body"),
            sourceName = "Custom Toledo v3",
            version = 3
        )
        val toledoFile = File(pluginsDir, "toledoblade.json").apply {
            writeText(gson.toJson(customV3))
        }

        val repo = ScraperPluginRepository(pluginsDir)
        repo.ensureDefaultPlugins()

        val loaded = gson.fromJson(toledoFile.readText(), ScraperRecipe::class.java)
        assertEquals(3, loaded.version)
        assertEquals("Custom Toledo v3", loaded.sourceName)
    }
}
