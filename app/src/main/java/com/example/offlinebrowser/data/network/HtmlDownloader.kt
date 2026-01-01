package com.example.offlinebrowser.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class HtmlDownloader(
    private val logCallback: ((String) -> Unit)? = null
) {
    suspend fun downloadHtml(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                logCallback?.invoke("Scraping URL: $url")
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .timeout(30000)
                    .followRedirects(true)
                    .get()

                logCallback?.invoke("Successfully scraped URL: $url")
                doc.outerHtml()
            } catch (e: Exception) {
                logCallback?.invoke("Failed to scrape URL $url: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
}
