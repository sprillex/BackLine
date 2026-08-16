package com.example.offlinebrowser.util

import android.content.Context
import android.widget.Toast
import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.model.GitHubContent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL

object PluginSearchUtil {

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun searchAndInstallPlugin(context: Context, urlOrDomain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val domain = extractDomain(urlOrDomain)
            if (domain.isEmpty()) return@withContext false

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Searching plugin for $domain...", Toast.LENGTH_SHORT).show()
            }

            // 1. Search sprillex/BackLine repo
            val backLineInstalled = searchBackLineRepo(context, domain)
            if (backLineInstalled) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Plugin installed from BackLine repo", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            }

            // 2. Search fivefilters repo
            val fiveFiltersInstalled = searchFiveFiltersRepo(context, domain)
            if (fiveFiltersInstalled) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Plugin installed from FiveFilters repo", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No plugin found for $domain", Toast.LENGTH_SHORT).show()
            }
            return@withContext false

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error searching plugin: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@withContext false
        }
    }

    private fun extractDomain(urlOrDomain: String): String {
        return try {
            val url = if (!urlOrDomain.startsWith("http://") && !urlOrDomain.startsWith("https://")) {
                "http://$urlOrDomain"
            } else {
                urlOrDomain
            }
            val host = URL(url).host
            host.removePrefix("www.")
        } catch (e: Exception) {
            urlOrDomain.removePrefix("www.")
        }
    }

    private suspend fun searchBackLineRepo(context: Context, domain: String): Boolean {
        // The GitHub Tree API is used to list all files in the repo recursively
        val treeUrl = "https://api.github.com/repos/sprillex/BackLine/git/trees/main?recursive=1"

        val request = Request.Builder().url(treeUrl).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return false

        val json = response.body?.string() ?: return false

        // Parse the tree
        val treeResponse = gson.fromJson(json, Map::class.java) as Map<*, *>
        val tree = treeResponse["tree"] as? List<Map<String, Any>> ?: return false

        // Find matching json plugin
        val matchName = domain.substringBeforeLast(".") + ".json" // e.g., hackaday.com -> hackaday.json
        // Or handle simple domain matching

        for (item in tree) {
            val path = item["path"] as? String ?: continue
            val type = item["type"] as? String ?: continue

            if (type == "blob" && path.startsWith("plugins/") && path.endsWith(".json")) {
                val fileName = path.substringAfterLast("/")
                val fileNameWithoutExt = fileName.removeSuffix(".json")

                // Compare domain and file name
                if (domain.contains(fileNameWithoutExt, ignoreCase = true)) {
                    // Found a match
                    val downloadUrl = "https://raw.githubusercontent.com/sprillex/BackLine/main/$path"
                    return downloadAndSaveJsonPlugin(context, downloadUrl)
                }
            }
        }

        return false
    }

    private suspend fun downloadAndSaveJsonPlugin(context: Context, downloadUrl: String): Boolean {
        val request = Request.Builder().url(downloadUrl).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return false

        val json = response.body?.string() ?: return false

        try {
            // Validate JSON
            val recipe = gson.fromJson(json, ScraperRecipe::class.java) ?: return false

            val fileName = "plugin_${System.currentTimeMillis()}.json"
            val file = File(context.filesDir, "plugins/$fileName")
            file.parentFile?.mkdirs()
            file.writeText(json)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun searchFiveFiltersRepo(context: Context, domain: String): Boolean {
        val treeUrl = "https://api.github.com/repos/fivefilters/ftr-site-config/git/trees/master?recursive=1"

        val request = Request.Builder().url(treeUrl).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return false

        val json = response.body?.string() ?: return false

        val treeResponse = gson.fromJson(json, Map::class.java) as Map<*, *>
        val tree = treeResponse["tree"] as? List<Map<String, Any>> ?: return false

        for (item in tree) {
            val path = item["path"] as? String ?: continue
            val type = item["type"] as? String ?: continue

            if (type == "blob" && path.endsWith(".txt")) {
                val fileName = path.substringAfterLast("/")
                val fileNameWithoutExt = fileName.removeSuffix(".txt")

                if (domain.contains(fileNameWithoutExt as CharSequence, ignoreCase = true) || fileNameWithoutExt.contains(domain as CharSequence, ignoreCase = true)) {
                    val downloadUrl = "https://raw.githubusercontent.com/fivefilters/ftr-site-config/master/$path"
                    return downloadAndParseTxtPlugin(context, downloadUrl, fileName)
                }
            }
        }

        return false
    }

    private suspend fun downloadAndParseTxtPlugin(context: Context, downloadUrl: String, itemName: String): Boolean {
        val content = URL(downloadUrl).readText()
        val lines = content.split("\n")

        val bodyPaths = mutableListOf<String>()
        val titlePaths = mutableListOf<String>()
        val removeSelectors = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.isEmpty()) continue

            if (trimmed.startsWith("body:")) {
                val path = trimmed.substringAfter("body:").trim()
                if (path.isNotEmpty()) bodyPaths.add(path)
            } else if (trimmed.startsWith("title:")) {
                val path = trimmed.substringAfter("title:").trim()
                if (path.isNotEmpty()) titlePaths.add(path)
            } else if (trimmed.startsWith("strip:")) {
                val path = trimmed.substringAfter("strip:").trim()
                if (path.isNotEmpty()) removeSelectors.add(path)
            } else if (trimmed.startsWith("strip_id_or_class:")) {
                val idOrClass = trimmed.substringAfter("strip_id_or_class:").trim()
                if (idOrClass.isNotEmpty()) {
                    removeSelectors.add("//*[contains(@class, '$idOrClass') or @id='$idOrClass']")
                }
            }
        }

        if (bodyPaths.isEmpty()) return false

        val domainName = itemName.removeSuffix(".txt")
        val safeDomain = domainName.replace(".", "\\.")

        val recipe = ScraperRecipe(
            domainPattern = ".*$safeDomain.*",
            strategy = ExtractionStrategy.XPATH,
            targetIdentifier = "",
            contentPath = bodyPaths,
            titlePath = if (titlePaths.isNotEmpty()) titlePaths else null,
            injectRssImage = true,
            removeSelectors = if (removeSelectors.isNotEmpty()) removeSelectors else null,
            sourceName = domainName
        )

        val json = gson.toJson(recipe)
        val fileName = "plugin_${System.currentTimeMillis()}.json"
        val file = File(context.filesDir, "plugins/$fileName")
        file.parentFile?.mkdirs()
        file.writeText(json)

        return true
    }
}
