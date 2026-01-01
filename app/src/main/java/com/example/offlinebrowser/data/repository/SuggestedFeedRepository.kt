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
                val tokens = line!!.split(",")
                if (tokens.size >= 6) {
                    val feed = SuggestedFeed(
                        name = tokens[0],
                        category = tokens[1],
                        language = tokens[2],
                        rank = tokens[3].toIntOrNull() ?: 0,
                        url = tokens[4],
                        contentType = tokens[5]
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
