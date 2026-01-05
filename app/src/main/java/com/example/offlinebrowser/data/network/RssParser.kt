package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class RssParser(private val logger: ((String) -> Unit)? = null) {
    suspend fun fetchFeed(feed: Feed): List<Article> {
        return withContext(Dispatchers.IO) {
            try {
                logger?.invoke("Fetching feed: ${feed.url}")
                val urlConnection = URL(feed.url).openConnection() as HttpURLConnection
                urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                urlConnection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                urlConnection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                urlConnection.setRequestProperty("Connection", "keep-alive")
                urlConnection.setRequestProperty("Upgrade-Insecure-Requests", "1")
                urlConnection.setRequestProperty("Sec-Fetch-Site", "none")
                urlConnection.setRequestProperty("Sec-Fetch-Mode", "navigate")
                urlConnection.setRequestProperty("Sec-Fetch-User", "?1")
                urlConnection.setRequestProperty("Sec-Fetch-Dest", "document")
                urlConnection.connectTimeout = 10000
                urlConnection.readTimeout = 10000

                var inputStream = urlConnection.inputStream
                val contentType = urlConnection.contentType ?: ""

                if (contentType.contains("html", ignoreCase = true)) {
                    val htmlContent = inputStream.bufferedReader().use { it.readText() }
                    val discoveredUrl = discoverFeedUrl(htmlContent, feed.url)

                    if (discoveredUrl != null) {
                        logger?.invoke("Discovered feed URL: $discoveredUrl from HTML content.")
                        // Recursively fetch the discovered URL? Or just start over connection
                        // For simplicity, let's just make a new connection here
                        val newUrl = URL(discoveredUrl)
                        val newConnection = newUrl.openConnection() as HttpURLConnection
                        newConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        newConnection.connectTimeout = 10000
                        newConnection.readTimeout = 10000

                        inputStream = newConnection.inputStream
                    } else {
                         logger?.invoke("Failed to discover RSS feed URL from HTML page: ${feed.url}")
                         // Fallback: try to guess standard RSS path if it's a known pattern or just fail gracefully?
                         // For now, fail gracefully but maybe logging it is enough.
                         // Attempting to parse HTML as XML will fail, but let it fail standard way.
                         // But we consumed the stream! So we can't reuse it.
                         // We must return empty list here.
                         return@withContext emptyList()
                    }
                }

                // Rome's XmlReader handles encoding detection
                val input = SyndFeedInput()
                val syndFeed = input.build(XmlReader(inputStream))

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

    private fun discoverFeedUrl(html: String, baseUrl: String): String? {
        // Find all <link> tags
        val linkTagPattern = "<link\\s+[^>]*>".toRegex(RegexOption.IGNORE_CASE)
        val typePattern = "type=[\"']application/(rss|atom)\\+xml[\"']".toRegex(RegexOption.IGNORE_CASE)
        val hrefPattern = "href=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)

        return linkTagPattern.findAll(html).map { matchResult ->
            matchResult.value
        }.firstOrNull { tag ->
            tag.contains(typePattern)
        }?.let { tag ->
            val hrefMatch = hrefPattern.find(tag)
            hrefMatch?.groups?.get(1)?.value?.let { href ->
                try {
                    val base = URL(baseUrl)
                    val resolved = URL(base, href)
                    resolved.toString()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
