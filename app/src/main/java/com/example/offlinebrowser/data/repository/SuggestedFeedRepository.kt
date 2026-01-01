package com.example.offlinebrowser.data.repository

import android.content.Context
import com.example.offlinebrowser.data.local.SuggestedFeedDao
import com.example.offlinebrowser.data.model.GitHubContent
import com.example.offlinebrowser.data.model.SuggestedFeed
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SuggestedFeedRepository(
    private val suggestedFeedDao: SuggestedFeedDao
) {

    val suggestedFeeds: Flow<List<SuggestedFeed>> = suggestedFeedDao.getAll()
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun initializeData() {
        // No-op: Removed automatic initialization from local asset
    }

    suspend fun fetchRemoteDirectory(url: String): List<GitHubContent> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val json = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<GitHubContent>>() {}.type
                gson.fromJson(json, type)
            }
        }
    }

    suspend fun downloadAndImportFeeds(url: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val csvContent = response.body?.string() ?: ""
                val feeds = parseRemoteCsv(csvContent)
                if (feeds.isNotEmpty()) {
                    suggestedFeedDao.insertAll(feeds)
                }
            }
        }
    }

    private fun parseRemoteCsv(content: String): List<SuggestedFeed> {
        val feeds = mutableListOf<SuggestedFeed>()
        val lines = content.lines()
        if (lines.isEmpty()) return feeds

        // Remote Format: name,category,language,rank,url,contentType
        // Skip header
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue

            // Use Regex to split by comma but ignoring commas inside quotes
            val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            if (tokens.size >= 6) {
                val name = tokens[0].trim().removeSurrounding("\"")
                val category = tokens[1].trim().removeSurrounding("\"")
                val language = tokens[2].trim().removeSurrounding("\"")
                val rank = tokens[3].trim().toIntOrNull() ?: 0
                val url = tokens[4].trim().removeSurrounding("\"")
                val contentType = tokens[5].trim().removeSurrounding("\"")

                val feed = SuggestedFeed(
                    name = name,
                    category = category,
                    language = language,
                    rank = rank,
                    url = url,
                    contentType = contentType,
                    summary = "" // Not present in remote CSV
                )
                feeds.add(feed)
            }
        }
        return feeds
    }
}
