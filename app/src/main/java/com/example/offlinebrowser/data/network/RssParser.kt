package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLConnection

class RssParser(
    private val logCallback: ((String) -> Unit)? = null
) {
    suspend fun fetchFeed(feed: Feed): List<Article> {
        return withContext(Dispatchers.IO) {
            try {
                logCallback?.invoke("Fetching feed: ${feed.url}")
                val urlConnection = URL(feed.url).openConnection()
                urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                urlConnection.connectTimeout = 30000
                urlConnection.readTimeout = 30000

                val input = SyndFeedInput()
                val syndFeed = input.build(XmlReader(urlConnection))

                val articles = syndFeed.entries.map { entry ->
                    Article(
                        feedId = feed.id,
                        title = entry.title ?: "",
                        url = entry.link ?: "",
                        content = entry.description?.value ?: "",
                        publishedDate = entry.publishedDate?.time ?: System.currentTimeMillis()
                    )
                }
                logCallback?.invoke("Successfully fetched feed: ${feed.url}. Found ${articles.size} entries.")
                articles
            } catch (e: Exception) {
                logCallback?.invoke("Failed to fetch feed ${feed.url}: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
