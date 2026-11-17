package com.example.offlinebrowser.data.network

import android.content.Context
import android.webkit.CookieManager
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class HtmlDownloadResult(
    val html: String?,
    val finalUrl: String,
    val scrapeResult: ScrapeResult? = null,
    val httpStatusCode: Int? = null
)

data class PrimaryFetchResponse(
    val statusCode: Int,
    val html: String?,
    val finalUrl: String
)

fun interface PrimaryHttpFetcher {
    suspend fun fetch(url: String): PrimaryFetchResponse
}

class SessionCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val key = url.host
        val existing = cookieStore.getOrPut(key) { mutableListOf() }
        synchronized(existing) {
            val now = System.currentTimeMillis()
            existing.removeAll { it.expiresAt <= now }
            for (incoming in cookies) {
                existing.removeAll {
                    it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
                }
                if (incoming.expiresAt > now) {
                    existing.add(incoming)
                }
            }
        }
        try {
            val webCookieManager = CookieManager.getInstance()
            val urlString = url.toString()
            for (cookie in cookies) {
                webCookieManager.setCookie(urlString, cookie.toString())
            }
        } catch (_: Throwable) {
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val matched = mutableListOf<Cookie>()
        val seenKeys = HashSet<String>()

        for ((_, list) in cookieStore) {
            synchronized(list) {
                list.removeAll { it.expiresAt <= now }
                for (cookie in list) {
                    if (cookie.matches(url) && seenKeys.add("${cookie.name}:${cookie.domain}:${cookie.path}")) {
                        matched.add(cookie)
                    }
                }
            }
        }

        try {
            val rawCookieHeader = CookieManager.getInstance().getCookie(url.toString())
            if (!rawCookieHeader.isNullOrBlank()) {
                rawCookieHeader.split(";").forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty()) {
                        val parsed = Cookie.parse(url, trimmed)
                        if (parsed != null && seenKeys.add("${parsed.name}:${parsed.domain}:${parsed.path}")) {
                            matched.add(parsed)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        return matched
    }
}

class HtmlDownloader internal constructor(
    context: Context?,
    private val primaryFetcher: PrimaryHttpFetcher,
    private val webViewFetcher: WebViewFallbackProvider?,
    private val logger: ((String) -> Unit)? = null
) {
    private val appContext: Context? = context?.applicationContext ?: context
    val scraperEngine = ScraperEngine()

    constructor(logger: ((String) -> Unit)? = null) : this(
        context = null,
        primaryFetcher = defaultPrimaryFetcher(defaultHttpClient()),
        webViewFetcher = null,
        logger = logger
    )

    constructor(
        context: Context?,
        logger: ((String) -> Unit)? = null
    ) : this(
        context = context,
        primaryFetcher = defaultPrimaryFetcher(defaultHttpClient()),
        webViewFetcher = null,
        logger = logger
    )

    internal constructor(
        context: Context?,
        httpClient: OkHttpClient,
        webViewFetcher: WebViewFallbackProvider?,
        logger: ((String) -> Unit)? = null
    ) : this(
        context = context,
        primaryFetcher = defaultPrimaryFetcher(httpClient),
        webViewFetcher = webViewFetcher,
        logger = logger
    )

    suspend fun downloadHtml(url: String, rssImageUrl: String? = null): String? {
        return downloadHtmlWithMetadata(url, rssImageUrl)?.html
    }

    suspend fun downloadHtmlWithMetadata(
        url: String,
        rssImageUrl: String? = null,
        enforceFilters: Boolean = true
    ): HtmlDownloadResult? {
        val sanitizedUrl = sanitizeUrl(url)
        if (sanitizedUrl.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                logger?.invoke("Scraping URL: $sanitizedUrl")
                val response = primaryFetcher.fetch(sanitizedUrl)
                val statusCode = response.statusCode

                if (statusCode in 200..299) {
                    logger?.invoke("Successfully scraped URL: $sanitizedUrl (HTTP $statusCode)")
                    val finalUrl = response.finalUrl.ifBlank { sanitizedUrl }
                    return@withContext processFetchedHtml(
                        url = finalUrl,
                        rawHtml = response.html,
                        rssImageUrl = rssImageUrl,
                        enforceFilters = enforceFilters,
                        httpStatusCode = statusCode
                    )
                }

                if (statusCode == 403 || statusCode == 503) {
                    logger?.invoke("HTTP $statusCode while scraping URL $sanitizedUrl; trying WebView fallback")
                    return@withContext attemptWebViewFallback(
                        url = sanitizedUrl,
                        rssImageUrl = rssImageUrl,
                        enforceFilters = enforceFilters,
                        originalStatusCode = statusCode
                    )
                }

                logger?.invoke("HTTP $statusCode while scraping URL $sanitizedUrl")
                HtmlDownloadResult(
                    html = null,
                    finalUrl = response.finalUrl.ifBlank { sanitizedUrl },
                    scrapeResult = null,
                    httpStatusCode = statusCode
                )
            } catch (e: HttpStatusException) {
                logger?.invoke("HTTP ${e.statusCode} while scraping URL $sanitizedUrl: ${e.message}")
                if (e.statusCode == 403 || e.statusCode == 503) {
                    return@withContext attemptWebViewFallback(
                        url = sanitizedUrl,
                        rssImageUrl = rssImageUrl,
                        enforceFilters = enforceFilters,
                        originalStatusCode = e.statusCode
                    )
                }
                HtmlDownloadResult(
                    html = null,
                    finalUrl = sanitizedUrl,
                    scrapeResult = null,
                    httpStatusCode = e.statusCode
                )
            } catch (e: Exception) {
                e.printStackTrace()
                logger?.invoke("Failed to scrape URL $sanitizedUrl: ${e.message}")
                null
            }
        }
    }

    private suspend fun attemptWebViewFallback(
        url: String,
        rssImageUrl: String?,
        enforceFilters: Boolean,
        originalStatusCode: Int
    ): HtmlDownloadResult {
        val fetcher = webViewFetcher ?: appContext?.let { WebViewFetcher(it, logger) }
        if (fetcher == null) {
            logger?.invoke("No WebView fallback provider available for $url (HTTP $originalStatusCode)")
            return HtmlDownloadResult(
                html = null,
                finalUrl = url,
                scrapeResult = null,
                httpStatusCode = originalStatusCode
            )
        }

        val webViewResult = try {
            fetcher.fetchHtml(url)
        } catch (e: Exception) {
            logger?.invoke("WebView fallback threw exception for $url: ${e.message}")
            null
        }

        val webViewHtml = webViewResult?.html
        val finalUrl = webViewResult?.finalUrl?.ifBlank { url } ?: url
        if (webViewHtml.isNullOrBlank() || isBotBlockOrEmptyHtml(webViewHtml)) {
            logger?.invoke("WebView fallback returned empty or bot-blocked content for $url")
            return HtmlDownloadResult(
                html = null,
                finalUrl = finalUrl,
                scrapeResult = null,
                httpStatusCode = originalStatusCode
            )
        }

        val processed = processFetchedHtml(
            url = finalUrl,
            rawHtml = webViewHtml,
            rssImageUrl = rssImageUrl,
            enforceFilters = enforceFilters,
            httpStatusCode = 200
        )
        return if (processed.html != null) {
            processed
        } else {
            processed.copy(httpStatusCode = originalStatusCode)
        }
    }

    private fun processFetchedHtml(
        url: String,
        rawHtml: String?,
        rssImageUrl: String?,
        enforceFilters: Boolean,
        httpStatusCode: Int
    ): HtmlDownloadResult {
        if (rawHtml.isNullOrBlank()) {
            return HtmlDownloadResult(
                html = null,
                finalUrl = url,
                scrapeResult = null,
                httpStatusCode = httpStatusCode
            )
        }

        val effectiveRssImageUrl = rssImageUrl ?: scraperEngine.extractImage(rawHtml)
        val scrapeResult = scraperEngine.processWithMetadata(
            url = url,
            html = rawHtml,
            rssImageUrl = effectiveRssImageUrl,
            enforceFilters = enforceFilters
        )

        val isPaywalledOrFiltered = scrapeResult != null && (
            scrapeResult.shouldHide ||
                scrapeResult.tags.any { it.equals("paywalled", ignoreCase = true) }
            )

        val resolvedHtml = when {
            isPaywalledOrFiltered -> null
            scrapeResult?.html != null -> {
                logger?.invoke("ScraperEngine successfully processed URL: $url")
                scrapeResult.html
            }
            isBotBlockOrEmptyHtml(rawHtml) -> null
            else -> rawHtml
        }

        return HtmlDownloadResult(
            html = resolvedHtml,
            finalUrl = url,
            scrapeResult = scrapeResult,
            httpStatusCode = httpStatusCode
        )
    }

    companion object {
        const val CHROME_ANDROID_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private const val ACCEPT_HEADER =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"

        private const val ACCEPT_LANGUAGE_HEADER = "en-US,en;q=0.9"

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .cookieJar(SessionCookieJar())
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        fun defaultPrimaryFetcher(httpClient: OkHttpClient): PrimaryHttpFetcher {
            return PrimaryHttpFetcher { url ->
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", CHROME_ANDROID_USER_AGENT)
                    .header("Accept", ACCEPT_HEADER)
                    .header("Accept-Language", ACCEPT_LANGUAGE_HEADER)
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-Fetch-Dest", "document")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    PrimaryFetchResponse(
                        statusCode = response.code,
                        html = response.body?.string(),
                        finalUrl = response.request.url.toString().ifBlank { url }
                    )
                }
            }
        }

        /**
         * Sanitizes raw URLs before passing them to Jsoup/OkHttp/URI so unencoded characters
         * in path, query, or fragment (such as `|`, spaces, `[`, `]`) do not throw
         * [java.net.URISyntaxException]. Preserves IPv6 host brackets and existing `%XX` escapes.
         */
        fun sanitizeUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return ""

            val schemeIdx = trimmed.indexOf("://")
            val afterScheme = if (schemeIdx >= 0) schemeIdx + 3 else 0
            var authorityEnd = trimmed.length
            for (i in afterScheme until trimmed.length) {
                val c = trimmed[i]
                if (c == '/' || c == '?' || c == '#') {
                    authorityEnd = i
                    break
                }
            }

            val prefix = trimmed.substring(0, authorityEnd).replace(" ", "%20")
            val suffix = trimmed.substring(authorityEnd)
            if (suffix.isEmpty()) return prefix

            val out = StringBuilder(trimmed.length + 16)
            out.append(prefix)
            for (i in suffix.indices) {
                when (val ch = suffix[i]) {
                    ' ' -> out.append("%20")
                    '|' -> out.append("%7C")
                    '[' -> out.append("%5B")
                    ']' -> out.append("%5D")
                    '{' -> out.append("%7B")
                    '}' -> out.append("%7D")
                    '^' -> out.append("%5E")
                    '`' -> out.append("%60")
                    '\\' -> out.append("%5C")
                    '<' -> out.append("%3C")
                    '>' -> out.append("%3E")
                    '"' -> out.append("%22")
                    '%' -> {
                        val hasValidHexEscape = i + 2 < suffix.length &&
                            isHexDigit(suffix[i + 1]) &&
                            isHexDigit(suffix[i + 2])
                        if (hasValidHexEscape) {
                            out.append('%')
                        } else {
                            out.append("%25")
                        }
                    }
                    else -> out.append(ch)
                }
            }
            return out.toString()
        }

        private fun isHexDigit(c: Char): Boolean {
            return (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
        }

        /**
         * Unescapes the JSON-encoded string returned by [android.webkit.WebView.evaluateJavascript].
         */
        fun unescapeJsString(rawJsResult: String?): String? {
            if (rawJsResult.isNullOrBlank() || rawJsResult == "null") return null
            return try {
                val parsed = JsonParser.parseString(rawJsResult)
                when {
                    parsed.isJsonNull -> null
                    parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString -> parsed.asString
                    else -> rawJsResult
                }
            } catch (_: Exception) {
                rawJsResult
            }
        }

        fun isBotBlockOrEmptyHtml(html: String?): Boolean {
            if (html.isNullOrBlank()) return true
            val doc = Jsoup.parse(html)
            val plainText = doc.text().trim()
            if (plainText.isEmpty() && doc.select("script").isEmpty()) {
                return true
            }
            if (plainText.length < 1500) {
                val titleLower = doc.title().lowercase()
                val textLower = plainText.lowercase()
                val htmlLower = html.lowercase()
                if (titleLower.contains("just a moment") ||
                    titleLower.contains("attention required") ||
                    titleLower.contains("access denied") ||
                    titleLower.contains("403 forbidden") ||
                    htmlLower.contains("cf-browser-verification") ||
                    htmlLower.contains("cf-challenge-running") ||
                    textLower.contains("enable javascript and cookies to continue") ||
                    textLower.contains("checking if the site connection is secure")
                ) {
                    return true
                }
            }
            return false
        }
    }
}
