package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom2.Element
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class ParsedFeedItem(
    val article: Article,
    val categories: List<String> = emptyList(),
    val author: String? = null,
    val rawContentHtml: String = "",
    val mediaCount: Int = 0,
    val encodedContentHtml: String = ""
)

class RssParser(private val logger: ((String) -> Unit)? = null) {

    suspend fun fetchFeed(feed: Feed): List<Article> {
        return fetchFeedItems(feed).map { it.article }
    }

    suspend fun fetchFeedItems(feed: Feed): List<ParsedFeedItem> {
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

                val items = parseStream(feed, urlConnection.inputStream)
                logger?.invoke("Successfully fetched feed: ${feed.url}. Found ${items.size} entries.")
                items
            } catch (e: Exception) {
                e.printStackTrace()
                logger?.invoke("Failed to fetch feed ${feed.url}: ${e.message}")
                emptyList()
            }
        }
    }

    fun parseStream(feed: Feed, inputStream: InputStream): List<ParsedFeedItem> {
        val input = SyndFeedInput()
        val syndFeed: SyndFeed = input.build(XmlReader(inputStream))
        return parseSyndFeed(feed, syndFeed)
    }

    fun parseSyndFeed(feed: Feed, syndFeed: SyndFeed): List<ParsedFeedItem> {
        return syndFeed.entries.map { entry ->
            val descriptionHtml = entry.description?.value ?: ""
            val contentParts = entry.contents?.mapNotNull { it.value?.takeIf { v -> v.isNotBlank() } } ?: emptyList()
            val encodedContentHtml = contentParts.joinToString("\n")
            val rawContentHtml = (listOf(descriptionHtml) + contentParts)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
            val primaryContent = encodedContentHtml.ifBlank { descriptionHtml }

            val categories = entry.categories
                ?.mapNotNull { it.name?.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            val author = entry.author?.trim()?.takeIf { it.isNotEmpty() }
                ?: entry.authors?.firstOrNull()?.name?.trim()?.takeIf { it.isNotEmpty() }

            val article = Article(
                feedId = feed.id,
                title = entry.title ?: "",
                url = entry.link ?: "",
                content = primaryContent,
                publishedDate = entry.publishedDate?.time ?: System.currentTimeMillis(),
                imageUrl = extractImageUrl(entry)
            )

            ParsedFeedItem(
                article = article,
                categories = categories,
                author = author,
                rawContentHtml = rawContentHtml,
                mediaCount = countMedia(entry),
                encodedContentHtml = encodedContentHtml
            )
        }
    }

    internal fun countMedia(entry: SyndEntry): Int {
        val urls = mutableSetOf<String>()
        var anonymousCount = 0

        entry.enclosures?.forEach { enclosure ->
            val encUrl = enclosure.url?.trim()
            if (!encUrl.isNullOrEmpty()) {
                urls.add(encUrl)
            } else {
                anonymousCount++
            }
        }

        entry.foreignMarkup?.forEach { element ->
            collectMediaUrls(element, urls) { anonymousCount++ }
        }

        return urls.size + anonymousCount
    }

    private fun collectMediaUrls(
        element: Element,
        urls: MutableSet<String>,
        onAnonymous: () -> Unit
    ) {
        val isMediaNs = element.namespacePrefix.equals("media", ignoreCase = true) ||
            element.namespaceURI?.contains("search.yahoo.com/mrss", ignoreCase = true) == true
        if (isMediaNs) {
            when (element.name.lowercase()) {
                "content", "thumbnail" -> {
                    val mediaUrl = element.getAttributeValue("url")?.trim()
                    if (!mediaUrl.isNullOrEmpty()) {
                        urls.add(mediaUrl)
                    } else {
                        onAnonymous()
                    }
                    return
                }
                "group" -> {
                    element.children?.forEach { child ->
                        collectMediaUrls(child, urls, onAnonymous)
                    }
                    return
                }
            }
        }
        element.children?.forEach { child ->
            collectMediaUrls(child, urls, onAnonymous)
        }
    }

    private fun extractImageUrl(entry: SyndEntry): String? {
        // 1. Check enclosures (e.g. <enclosure type="image/..." /> or parsed <media:content>)
        val enclosure = entry.enclosures.firstOrNull { it.type?.startsWith("image/") == true }
        if (enclosure != null) return enclosure.url

        // 2. Check foreignMarkup media:content / media:thumbnail
        val mediaUrls = mutableSetOf<String>()
        entry.foreignMarkup?.forEach { collectMediaUrls(it, mediaUrls) {} }
        if (mediaUrls.isNotEmpty()) return mediaUrls.first()

        // 3. Fallback: Parse description for <img> tag
        val description = entry.description?.value
        if (description != null) {
            val match = Regex("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]").find(description)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // 4. Fallback: Check contents list (often used for full content in Atom/RSS)
        if (entry.contents.isNotEmpty()) {
            for (content in entry.contents) {
                val value = content.value
                if (value != null) {
                    val match = Regex("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]").find(value)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        }

        return null
    }
}
