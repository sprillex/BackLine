package com.example.offlinebrowser.data.network

import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.ArticleListItem
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.repository.FeedRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.jsoup.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

class HtmlDownloaderTest {

    private class LocalTestHttpServer(
        private val handler: (requestPath: String, headers: Map<String, String>) -> TestHttpResponse
    ) : AutoCloseable {
        data class TestHttpResponse(
            val statusCode: Int,
            val statusText: String,
            val headers: Map<String, String> = emptyMap(),
            val body: String = ""
        )

        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort
        @Volatile
        private var running = true

        init {
            Thread {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        socket.use { s ->
                            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                            val requestLine = reader.readLine() ?: return@use
                            val parts = requestLine.split(" ")
                            val requestPath = if (parts.size >= 2) parts[1] else "/"
                            val reqHeaders = linkedMapOf<String, String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                val colon = line.indexOf(':')
                                if (colon > 0) {
                                    val name = line.substring(0, colon).trim().lowercase()
                                    val value = line.substring(colon + 1).trim()
                                    reqHeaders[name] = value
                                }
                            }
                            val resp = handler(requestPath, reqHeaders)
                            val bodyBytes = resp.body.toByteArray(Charsets.UTF_8)
                            val sb = StringBuilder()
                            sb.append("HTTP/1.1 ${resp.statusCode} ${resp.statusText}\r\n")
                            sb.append("Content-Length: ${bodyBytes.size}\r\n")
                            sb.append("Connection: close\r\n")
                            resp.headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
                            sb.append("\r\n")
                            val out = s.getOutputStream()
                            out.write(sb.toString().toByteArray(Charsets.UTF_8))
                            out.write(bodyBytes)
                            out.flush()
                        }
                    } catch (_: Exception) {
                        if (!running) break
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        override fun close() {
            running = false
            try {
                serverSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadPluginRecipe(relativePath: String): ScraperRecipe {
        val candidates = listOf(File(relativePath), File("../$relativePath"))
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Missing plugin file: $relativePath")
        return Gson().fromJson(file.readText(), ScraperRecipe::class.java)
    }

    private class InMemoryArticleDao : ArticleDao {
        private val articles = MutableStateFlow<List<Article>>(emptyList())

        override fun getArticlesForFeed(feedId: Int): Flow<List<ArticleListItem>> = articles.map { emptyList() }
        override fun getArticlesByCategory(category: String): Flow<List<ArticleListItem>> = articles.map { emptyList() }
        override fun getAllArticles(): Flow<List<ArticleListItem>> = articles.map { emptyList() }
        override fun searchArticles(query: String): Flow<List<ArticleListItem>> = articles.map { emptyList() }
        override fun getHiddenArticles(): Flow<List<ArticleListItem>> = articles.map { emptyList() }
        override fun getDistinctVisibleCategories(): Flow<List<String>> = articles.map { emptyList() }
        override suspend fun getArticleByUrl(feedId: Int, url: String): Article? =
            articles.value.firstOrNull { it.feedId == feedId && it.url == url }
        override suspend fun getArticleById(id: Int): Article? =
            articles.value.firstOrNull { it.id == id }
        override suspend fun insertArticle(article: Article) {
            val current = articles.value.toMutableList()
            val idx = current.indexOfFirst { it.id == article.id }
            if (idx >= 0) current[idx] = article else current.add(article)
            articles.value = current
        }
        override suspend fun updateArticle(article: Article) {
            insertArticle(article)
        }
        override suspend fun deleteArticle(article: Article) = Unit
        override suspend fun updateArticleFavoriteStatus(id: Int, isFavorite: Boolean) = Unit
        override suspend fun updateArticleReadStatus(id: Int, isRead: Boolean) = Unit
        override suspend fun updateArticleSummary(id: Int, summary: String) = Unit
        override suspend fun updateArticleHiddenStatus(id: Int, isHidden: Boolean, isManuallyOverridden: Boolean) = Unit
        override suspend fun updateArticleCategory(id: Int, category: String?, isManuallyOverridden: Boolean) = Unit
        override suspend fun deleteOldArticles(feedId: Int, timestamp: Long) = Unit
        override suspend fun deleteExcessArticles(feedId: Int, limit: Int) = Unit
        override suspend fun getTopUncachedArticles(feedId: Int, limit: Int): List<Article> = emptyList()
    }

    @Test
    fun testSanitizeUrlEncodesIllegalQueryCharactersWithoutDoubleEncoding() {
        val raw = "  https://www.mlive.com/news/2026/11/story.html?utm_campaign=RSS|NSNS&q=hello world&arr=[a,b]  "
        val sanitized = HtmlDownloader.sanitizeUrl(raw)

        assertEquals(
            "https://www.mlive.com/news/2026/11/story.html?utm_campaign=RSS%7CNSNS&q=hello%20world&arr=%5Ba,b%5D",
            sanitized
        )
        // Must parse cleanly as java.net.URI without throwing URISyntaxException
        val parsedUri = URI(sanitized)
        assertEquals("www.mlive.com", parsedUri.host)

        // Already-encoded %7C and %20 must not be double-encoded
        assertEquals(sanitized, HtmlDownloader.sanitizeUrl(sanitized))

        // IPv6 host brackets stay intact while query brackets are encoded
        val ipv6 = "http://[::1]:8080/article?tag=[local|news]"
        val sanitizedIpv6 = HtmlDownloader.sanitizeUrl(ipv6)
        assertEquals("http://[::1]:8080/article?tag=%5Blocal%7Cnews%5D", sanitizedIpv6)
        assertEquals("::1", URI(sanitizedIpv6).host.trim('[', ']'))
    }

    @Test
    fun testPrimaryHttpFetchSendsBrowserHeadersCookiesAndSanitizedQuery() = runBlocking {
        val capturedRequests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        LocalTestHttpServer { requestPath, headers ->
            capturedRequests.add(requestPath to headers)
            LocalTestHttpServer.TestHttpResponse(
                statusCode = 200,
                statusText = "OK",
                headers = mapOf(
                    "Set-Cookie" to "session_token=xyz789; Path=/",
                    "Content-Type" to "text/html; charset=utf-8"
                ),
                body = "<html><body><h1>Article</h1><p>Hello world</p></body></html>"
            )
        }.use { server ->
            val downloader = HtmlDownloader(
                context = null,
                httpClient = HtmlDownloader.defaultHttpClient(),
                webViewFetcher = null
            )

            val rawUrl = "http://127.0.0.1:${server.port}/article?utm_campaign=RSS|NSNS&q=michigan news"
            val firstResult = downloader.downloadHtmlWithMetadata(rawUrl)
            assertNotNull(firstResult?.html)
            assertEquals(200, firstResult?.httpStatusCode)

            val (firstUri, firstHeaders) = capturedRequests[0]
            assertEquals("/article?utm_campaign=RSS%7CNSNS&q=michigan%20news", firstUri)
            val ua = firstHeaders["user-agent"].orEmpty()
            assertTrue("Expected Chrome Android User-Agent, got: $ua", ua.contains("Android") && ua.contains("Chrome/"))
            assertTrue(firstHeaders["accept"].orEmpty().contains("text/html"))
            assertEquals("en-US,en;q=0.9", firstHeaders["accept-language"])
            assertEquals("none", firstHeaders["sec-fetch-site"])
            assertEquals("navigate", firstHeaders["sec-fetch-mode"])
            assertEquals("?1", firstHeaders["sec-fetch-user"])
            assertEquals("document", firstHeaders["sec-fetch-dest"])

            // Second request to same server should carry the session cookie
            downloader.downloadHtmlWithMetadata("http://127.0.0.1:${server.port}/article?page=2")
            val (_, secondHeaders) = capturedRequests[1]
            assertTrue(
                "Expected session cookie on second request, got: ${secondHeaders["cookie"]}",
                secondHeaders["cookie"].orEmpty().contains("session_token=xyz789")
            )
        }
    }

    @Test
    fun testHttp403DelegatesToWebViewFallbackAndFeedsHtmlToScraperEngine() = runBlocking {
        val webViewRequestedUrls = mutableListOf<String>()
        LocalTestHttpServer { _, _ ->
            LocalTestHttpServer.TestHttpResponse(
                statusCode = 403,
                statusText = "Forbidden",
                headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
                body = "<html><head><title>403 Forbidden</title></head><body>Access Denied</body></html>"
            )
        }.use { server ->
            val fakeWebViewProvider = WebViewFallbackProvider { url ->
                webViewRequestedUrls.add(url)
                WebViewFetchResult(
                    html = """
                        <html>
                        <head><title>MLive Story</title></head>
                        <body>
                            <h1 class="entry-title">Michigan Bridge Opens Early</h1>
                            <div class="article__body">
                                <p>Crews completed construction ahead of schedule across northern Michigan.</p>
                            </div>
                        </body>
                        </html>
                    """.trimIndent(),
                    finalUrl = "https://www.mlive.com/news/2026/11/bridge-opens.html"
                )
            }

            val downloader = HtmlDownloader(
                context = null,
                httpClient = HtmlDownloader.defaultHttpClient(),
                webViewFetcher = fakeWebViewProvider
            )
            downloader.scraperEngine.loadRecipes(listOf(loadPluginRecipe("plugins/en/News/mlive.json")))

            val result = downloader.downloadHtmlWithMetadata(
                "http://127.0.0.1:${server.port}/mlive/story?utm_campaign=RSS|NSNS"
            )

            assertEquals(1, webViewRequestedUrls.size)
            assertTrue(webViewRequestedUrls[0].contains("utm_campaign=RSS%7CNSNS"))
            assertNotNull(result)
            assertNotNull(result?.html)
            assertEquals(200, result?.httpStatusCode)
            assertEquals("https://www.mlive.com/news/2026/11/bridge-opens.html", result?.finalUrl)
            assertNotNull(result?.scrapeResult)
            assertEquals("Michigan Bridge Opens Early", result?.scrapeResult?.title)
            assertTrue(result?.html!!.contains("Crews completed construction ahead of schedule"))
            assertTrue(result.html!!.contains("Michigan Bridge Opens Early"))
        }
    }

    @Test
    fun testHttpStatusException403And503DelegateToWebViewFallback() = runBlocking {
        var fallbackCalls = 0
        val throwingFetcher = PrimaryHttpFetcher { url ->
            throw HttpStatusException("Forbidden", 403, url)
        }
        val fakeWebView = WebViewFallbackProvider { url ->
            fallbackCalls++
            WebViewFetchResult(
                html = "<html><body><h1>Recovered</h1><p>Rendered via WebView.</p></body></html>",
                finalUrl = url
            )
        }

        val downloader = HtmlDownloader(
            context = null,
            primaryFetcher = throwingFetcher,
            webViewFetcher = fakeWebView
        )
        val result = downloader.downloadHtmlWithMetadata("https://example.com/blocked?a=1|2")
        assertEquals(1, fallbackCalls)
        assertNotNull(result?.html)
        assertTrue(result?.html!!.contains("Rendered via WebView."))

        // Also test HTTP 503 status response
        var fallback503Calls = 0
        val downloader503 = HtmlDownloader(
            context = null,
            primaryFetcher = PrimaryHttpFetcher { url ->
                PrimaryFetchResponse(statusCode = 503, html = "Service Unavailable", finalUrl = url)
            },
            webViewFetcher = WebViewFallbackProvider { url ->
                fallback503Calls++
                WebViewFetchResult("<html><body><p>Passed 503 JS challenge</p></body></html>", url)
            }
        )
        val result503 = downloader503.downloadHtmlWithMetadata("https://example.com/challenge")
        assertEquals(1, fallback503Calls)
        assertTrue(result503?.html.orEmpty().contains("Passed 503 JS challenge"))
    }

    @Test
    fun testFailedOrPaywalledWebViewFallbackKeepsArticleUncachedUnlessSubstantiveRssPreserved() = runBlocking {
        val oscoda = loadPluginRecipe("plugins/en/News/oscodaherald.json")
        val dao = InMemoryArticleDao()
        val feed = Feed(id = 1, url = "https://www.oscodaherald.com/feed", title = "Oscoda", type = FeedType.RSS)

        val shortArticle = Article(
            id = 10,
            feedId = feed.id,
            title = "Paywalled Story",
            url = "https://www.oscodaherald.com/news/story-1",
            content = "<p>Short teaser preview.</p>",
            publishedDate = System.currentTimeMillis(),
            isCached = false
        )
        dao.insertArticle(shortArticle)

        // Case 1: WebView returns paywalled HTML -> HtmlDownloader returns null html and keeps isCached = false
        val paywallDownloader = HtmlDownloader(
            context = null,
            primaryFetcher = PrimaryHttpFetcher { url -> PrimaryFetchResponse(403, null, url) },
            webViewFetcher = WebViewFallbackProvider { url ->
                WebViewFetchResult(
                    html = """
                        <html><body>
                            <h1 class="entry-title">Paywalled Story</h1>
                            <div class="entry-content"><p>Short teaser preview.</p><p>Login to continue reading</p></div>
                            <div id="checkout_form_wrapper" class="cherryroad-media-paywall">Subscribe</div>
                        </body></html>
                    """.trimIndent(),
                    finalUrl = url
                )
            }
        )
        paywallDownloader.scraperEngine.loadRecipes(listOf(oscoda))

        val paywallResult = paywallDownloader.downloadHtmlWithMetadata(shortArticle.url)
        assertNull(paywallResult?.html)

        FeedRepository.applyAutoDownloadResult(
            article = shortArticle,
            downloadResult = paywallResult,
            initialImageUrl = null,
            initialLocalImagePath = null,
            articleDao = dao,
            scraperEngine = paywallDownloader.scraperEngine,
            enforceFilters = true
        )
        assertFalse(dao.getArticleById(shortArticle.id)!!.isCached)

        // Case 2: WebView returns empty/challenge HTML -> remains uncached for short teaser,
        // but substantive >= 500-char RSS <content:encoded> body is preserved as cached.
        val blockDownloader = HtmlDownloader(
            context = null,
            primaryFetcher = PrimaryHttpFetcher { url -> PrimaryFetchResponse(403, null, url) },
            webViewFetcher = WebViewFallbackProvider { url ->
                WebViewFetchResult("<html><head><title>Just a moment...</title></head><body></body></html>", url)
            }
        )
        val blockedResult = blockDownloader.downloadHtmlWithMetadata("https://example.com/blocked")
        assertNull(blockedResult?.html)

        val longRssBody = "<p>" + "Substantive RSS encoded journalism paragraph. ".repeat(15) + "</p>"
        val longArticle = Article(
            id = 11,
            feedId = feed.id,
            title = "Substantive RSS Article",
            url = "https://www.mlive.com/news/2026/11/long.html",
            content = longRssBody,
            publishedDate = System.currentTimeMillis(),
            isCached = false
        )
        dao.insertArticle(longArticle)
        FeedRepository.applyAutoDownloadResult(
            article = longArticle,
            downloadResult = blockedResult,
            initialImageUrl = null,
            initialLocalImagePath = null,
            articleDao = dao,
            scraperEngine = blockDownloader.scraperEngine,
            enforceFilters = true
        )
        assertTrue(dao.getArticleById(longArticle.id)!!.isCached)
    }

    @Test
    fun testUnescapeJsStringUnescapesEvaluateJavascriptResult() {
        val raw = "\"\\u003Chtml>\\u003Cbody>\\u003Ch1>Title \\\"Quoted\\\"\\u003C/h1>\\u003C/body>\\u003C/html>\""
        assertEquals(
            "<html><body><h1>Title \"Quoted\"</h1></body></html>",
            HtmlDownloader.unescapeJsString(raw)
        )
        assertNull(HtmlDownloader.unescapeJsString("null"))
        assertNull(HtmlDownloader.unescapeJsString(null))
    }
}
