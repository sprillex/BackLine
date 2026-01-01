package com.example.offlinebrowser.data.repository

import android.content.Context
import com.example.offlinebrowser.data.local.SuggestedFeedDao
import com.example.offlinebrowser.data.model.SuggestedFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class SuggestedFeedRepository(
    private val suggestedFeedDao: SuggestedFeedDao,
    private val context: Context
) {

    val suggestedFeeds: Flow<List<SuggestedFeed>> = suggestedFeedDao.getAll()

    suspend fun initializeData() {
        withContext(Dispatchers.IO) {
            if (suggestedFeedDao.count() == 0) {
                val feeds = parseCsv()
                suggestedFeedDao.insertAll(feeds)
            }
        }
    }

    private fun parseCsv(): List<SuggestedFeed> {
        val feeds = mutableListOf<SuggestedFeed>()
        try {
            val inputStream = context.assets.open("top_feeds.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))
            // Skip header
            reader.readLine()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Expected Format: rank,Name,language,summary,url,contentType
                // Use Regex to split by comma but ignoring commas inside quotes
                val tokens = line!!.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                if (tokens.size >= 6) {
                    val rank = tokens[0].trim().toIntOrNull() ?: 0
                    val name = tokens[1].trim()
                    val language = tokens[2].trim()
                    val summary = tokens[3].trim().removeSurrounding("\"")
                    val url = tokens[4].trim()
                    val contentType = tokens[5].trim()

                    val feed = SuggestedFeed(
                        name = name,
                        category = "General", // Default since category column was removed
                        language = language,
                        summary = summary,
                        rank = rank,
                        url = url,
                        contentType = contentType
                    )
                    feeds.add(feed)
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return feeds
    }
}
