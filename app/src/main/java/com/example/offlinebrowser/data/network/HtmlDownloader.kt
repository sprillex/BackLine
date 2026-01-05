package com.example.offlinebrowser.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class HtmlDownloader(private val logger: ((String) -> Unit)? = null) {
    val scraperEngine = ScraperEngine()

    suspend fun downloadHtml(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                logger?.invoke("Scraping URL: $url")
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                    .get()
                logger?.invoke("Successfully scraped URL: $url")

                val html = doc.outerHtml()
                // Try to process via ScraperEngine for specific sites like Toledo Blade
                val scrapedContent = scraperEngine.process(url, html)

                if (scrapedContent != null) {
                    logger?.invoke("ScraperEngine successfully processed URL: $url")
                    scrapedContent
                } else {
                    html
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logger?.invoke("Failed to scrape URL $url: ${e.message}")
                null
            }
        }
    }
}
