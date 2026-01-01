package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class RssParser(private val logger: ((String) -> Unit)? = null) {
    suspend fun fetchFeed(feed: Feed): List<Article> {
        return withContext(Dispatchers.IO) {
            try {
                logger?.invoke("Fetching feed: ${feed.url}")
                val url = URL(feed.url)
                val input = SyndFeedInput()
                val syndFeed = input.build(XmlReader(url))

                logger?.invoke("Successfully fetched feed: ${feed.url}. Found ${syndFeed.entries.size} entries.")

                syndFeed.entries.map { entry ->
                    Article(
                        feedId = feed.id,
                        title = entry.title ?: "",
                        url = entry.link ?: "",
                        content = entry.description?.value ?: "",
                        publishedDate = entry.publishedDate?.time ?: System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logger?.invoke("Failed to fetch feed ${feed.url}: ${e.message}")
                emptyList()
            }
        }
    }
}
