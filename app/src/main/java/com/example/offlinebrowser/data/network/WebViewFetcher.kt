package com.example.offlinebrowser.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class WebViewFetchResult(
    val html: String?,
    val finalUrl: String
)

fun interface WebViewFallbackProvider {
    suspend fun fetchHtml(url: String): WebViewFetchResult?
}

/**
 * Offscreen headless Android [WebView] helper for fetching pages blocked by HTTP 403 / 503
 * anti-bot or JS challenges.
 */
class WebViewFetcher(
    private val context: Context,
    private val logger: ((String) -> Unit)? = null,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val jsSettleDelayMs: Long = DEFAULT_JS_SETTLE_DELAY_MS
) : WebViewFallbackProvider {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun fetchHtml(url: String): WebViewFetchResult? {
        val sanitizedUrl = HtmlDownloader.sanitizeUrl(url)
        if (sanitizedUrl.isBlank()) return null

        logger?.invoke("Starting headless WebView fallback for: $sanitizedUrl")

        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                var webView: WebView? = null
                try {
                    val appContext = context.applicationContext ?: context
                    val wv = WebView(appContext)
                    webView = wv

                    wv.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        blockNetworkImage = true
                        userAgentString = HtmlDownloader.CHROME_ANDROID_USER_AGENT
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    try {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(wv, true)
                    } catch (_: Throwable) {
                    }

                    val isLoading = AtomicBoolean(true)
                    val mainFrameError = AtomicBoolean(false)
                    val finishedCount = AtomicInteger(0)
                    val pageFinishedChannel = Channel<Int>(Channel.CONFLATED)

                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: android.graphics.Bitmap?) {
                            if (startedUrl != "about:blank") {
                                isLoading.set(true)
                            }
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            if (finishedUrl == "about:blank") return
                            isLoading.set(false)
                            val count = finishedCount.incrementAndGet()
                            pageFinishedChannel.trySend(count)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                logger?.invoke("WebView main-frame error for $sanitizedUrl: ${error?.description}")
                                mainFrameError.set(true)
                                isLoading.set(false)
                                pageFinishedChannel.trySend(finishedCount.incrementAndGet())
                            }
                        }
                    }

                    wv.loadUrl(sanitizedUrl)

                    var seenCount = pageFinishedChannel.receive()
                    var settleAttempts = 0
                    while (settleAttempts < MAX_SETTLE_ATTEMPTS) {
                        if (mainFrameError.get()) {
                            return@withContext null
                        }
                        delay(jsSettleDelayMs)
                        if (mainFrameError.get()) {
                            return@withContext null
                        }
                        if (!isLoading.get() && finishedCount.get() == seenCount) {
                            break
                        }
                        if (isLoading.get()) {
                            seenCount = pageFinishedChannel.receive()
                        } else {
                            seenCount = finishedCount.get()
                        }
                        settleAttempts++
                    }

                    val rawJsResult = suspendCancellableCoroutine<String?> { cont ->
                        wv.evaluateJavascript(
                            "(function(){ return document.documentElement ? document.documentElement.outerHTML : ''; })();"
                        ) { value ->
                            if (cont.isActive) {
                                cont.resume(value)
                            }
                        }
                    }

                    val extractedHtml = HtmlDownloader.unescapeJsString(rawJsResult)
                    val resolvedUrl = wv.url
                        ?.takeIf { it.isNotBlank() && it != "about:blank" }
                        ?: sanitizedUrl

                    try {
                        CookieManager.getInstance().flush()
                    } catch (_: Throwable) {
                    }

                    logger?.invoke("Headless WebView extracted ${extractedHtml?.length ?: 0} chars for: $resolvedUrl")
                    WebViewFetchResult(html = extractedHtml, finalUrl = resolvedUrl)
                } finally {
                    webView?.let { wv ->
                        try {
                            wv.stopLoading()
                            wv.webViewClient = WebViewClient()
                            wv.loadUrl("about:blank")
                            wv.clearHistory()
                            wv.removeAllViews()
                            wv.destroy()
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }.also { result ->
            if (result == null) {
                logger?.invoke("Headless WebView fallback timed out or failed for: $sanitizedUrl")
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val DEFAULT_JS_SETTLE_DELAY_MS = 1_500L
        private const val MAX_SETTLE_ATTEMPTS = 3
    }
}
