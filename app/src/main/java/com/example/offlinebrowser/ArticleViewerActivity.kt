package com.example.offlinebrowser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.network.HtmlDownloader
import com.example.offlinebrowser.data.network.ImageDownloader
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.data.repository.ScraperPluginRepository
import com.example.offlinebrowser.util.NetworkMonitor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ArticleViewerActivity : AppCompatActivity() {

    private var currentArticleUrl: String? = null
    private var currentArticleId: Int = -1
    private var isRetryingDownload = false

    private lateinit var webView: WebView
    private lateinit var partialBanner: View
    private lateinit var btnOpenInBrowser: Button
    private lateinit var btnRetryDownload: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_viewer)

        webView = findViewById(R.id.webView)
        partialBanner = findViewById(R.id.partial_article_banner)
        btnOpenInBrowser = findViewById(R.id.btn_open_in_browser)
        btnRetryDownload = findViewById(R.id.btn_retry_download)
        val fabDarkMode = findViewById<FloatingActionButton>(R.id.fab_dark_mode)

        // Do not block network images globally; we will filter them in WebViewClient to allow local/cached content
        webView.settings.blockNetworkImage = false
        // Allow file access to load locally cached images
        webView.settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                if (url.scheme == "app" && url.host == RETRY_DOWNLOAD_HOST) {
                    retryArticleDownload()
                    return true
                }
                if (url.scheme == "http" || url.scheme == "https") {
                    openInBrowser(url.toString())
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.scheme == "http" || url.scheme == "https") {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        // Handle user preference for showing images in article view
        // Since we are blocking network images, this mostly controls local/injected images
        val preferencesRepository = PreferencesRepository(this)
        webView.settings.loadsImagesAutomatically = preferencesRepository.showImagesInArticleView

        btnOpenInBrowser.setOnClickListener {
            openInBrowser(currentArticleUrl)
        }

        btnRetryDownload.setOnClickListener {
            retryArticleDownload()
        }

        val articleId = intent.getIntExtra("ARTICLE_ID", -1)
        if (articleId != -1) {
            currentArticleId = articleId
            loadArticleContent(webView, articleId)
        }

        fabDarkMode.setOnClickListener {
            showPluginMenu()
        }

        setupBottomNav()
    }

    private fun openInBrowser(rawUrl: String?) {
        val targetUrl = rawUrl?.takeIf { it.isNotBlank() } ?: return
        val sanitizedUrl = HtmlDownloader.sanitizeUrl(targetUrl)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sanitizedUrl))
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser app available", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open URL: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadArticleContent(webView: WebView, articleId: Int) {
        val database = OfflineDatabase.getDatabase(this)
        lifecycleScope.launch {
            val article = withContext(Dispatchers.IO) {
                database.articleDao().getArticleById(articleId)
            }

            if (article != null) {
                currentArticleUrl = article.url
                val showPartialBanner = shouldShowPartialBanner(article)
                partialBanner.visibility = if (showPartialBanner) View.VISIBLE else View.GONE

                var content = article.content

                if (!article.summary.isNullOrEmpty()) {
                    content = injectSummaryHtml(content, article.summary)
                }

                if (article.localImagePath != null) {
                    try {
                        val doc = Jsoup.parse(content)
                        var changed = false

                        if (article.imageUrl != null) {
                            val images = doc.select("img[src]")
                            for (img in images) {
                                if (img.attr("src") == article.imageUrl) {
                                    img.attr("src", "file://${article.localImagePath}")
                                    changed = true
                                }
                            }
                        }

                        if (!changed) {
                            val injectedImage = doc.select("img[alt='Article Image']").first()
                            if (injectedImage != null) {
                                injectedImage.attr("src", "file://${article.localImagePath}")
                                changed = true
                            }
                        }

                        if (changed) {
                            content = doc.outerHtml()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (article.imageUrl != null) {
                            content = content.replace(article.imageUrl, "file://${article.localImagePath}")
                        }
                    }
                }

                if (showPartialBanner) {
                    content = injectPartialFooterHtml(content, article.url)
                }

                webView.loadDataWithBaseURL("file:///", content, "text/html", "UTF-8", null)
            }
        }
    }

    private fun retryArticleDownload() {
        if (currentArticleId == -1 || isRetryingDownload) return

        val prefs = PreferencesRepository(this)
        val networkMonitor = NetworkMonitor(this)
        if (prefs.wifiOnly && !networkMonitor.isWifiConnected()) {
            Toast.makeText(this, "WiFi is required to re-download", Toast.LENGTH_SHORT).show()
            return
        }

        isRetryingDownload = true
        btnRetryDownload.isEnabled = false
        btnRetryDownload.text = "Downloading..."
        Toast.makeText(this, "Retrying full article download...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = OfflineDatabase.getDatabase(this@ArticleViewerActivity)
                val article = database.articleDao().getArticleById(currentArticleId)
                if (article == null) {
                    withContext(Dispatchers.Main) {
                        resetRetryButton()
                    }
                    return@launch
                }

                val scraperPluginRepository = ScraperPluginRepository(this@ArticleViewerActivity)
                val htmlDownloader = HtmlDownloader(context = applicationContext)
                scraperPluginRepository.ensureDefaultPlugins()
                htmlDownloader.scraperEngine.loadRecipes(scraperPluginRepository.loadAllRecipes())

                var localImagePath = article.localImagePath
                var imageUrl = article.imageUrl
                if (imageUrl != null && localImagePath == null) {
                    localImagePath = ImageDownloader(this@ArticleViewerActivity).downloadImage(imageUrl)
                }
                val imagePathForInjection = if (localImagePath != null) "file://$localImagePath" else imageUrl

                val downloadResult = htmlDownloader.downloadHtmlWithMetadata(
                    url = article.url,
                    rssImageUrl = imagePathForInjection,
                    enforceFilters = prefs.enforcePluginFilters
                )

                val newHtml = downloadResult?.html
                if (newHtml != null) {
                    if (imageUrl == null) {
                        imageUrl = htmlDownloader.scraperEngine.extractImage(newHtml)
                        if (imageUrl != null && localImagePath == null) {
                            localImagePath = ImageDownloader(this@ArticleViewerActivity).downloadImage(imageUrl)
                        }
                    }

                    val updatedCategory = if (article.isManuallyOverridden) {
                        article.category
                    } else {
                        downloadResult.scrapeResult?.category ?: article.category
                    }
                    val updatedArticle = article.copy(
                        content = newHtml,
                        isCached = true,
                        imageUrl = imageUrl,
                        localImagePath = localImagePath,
                        category = updatedCategory,
                        subcategory = downloadResult.scrapeResult?.subcategory ?: article.subcategory
                    )
                    database.articleDao().updateArticle(updatedArticle)

                    withContext(Dispatchers.Main) {
                        resetRetryButton()
                        loadArticleContent(webView, article.id)
                        Toast.makeText(this@ArticleViewerActivity, "Full article downloaded", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        resetRetryButton()
                        Toast.makeText(
                            this@ArticleViewerActivity,
                            "Could not download full article. Open in browser to read.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    resetRetryButton()
                    Toast.makeText(this@ArticleViewerActivity, "Failed to download article", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resetRetryButton() {
        isRetryingDownload = false
        btnRetryDownload.isEnabled = true
        btnRetryDownload.setText(R.string.retry_download)
    }

    private fun injectSummaryHtml(html: String, summary: String, modelName: String? = null): String {
        val modelLabel = if (!modelName.isNullOrEmpty()) " ✨ Gemma Summary ($modelName)" else "✨ Gemma Summary"
        val summaryCardHtml = """
            <div style="background-color: #262626; color: #E0E0E0; border-left: 4px solid #00E5FF; padding: 14px; margin: 15px 0; border-radius: 6px; font-family: sans-serif;">
                <div style="font-weight: bold; font-size: 16px; margin-bottom: 8px; color: #00E5FF;">$modelLabel</div>
                <div style="font-size: 14px; line-height: 1.5; white-space: pre-wrap;">${Jsoup.clean(summary, org.jsoup.safety.Safelist.basic())}</div>
            </div>
        """.trimIndent()

        return try {
            val doc = Jsoup.parse(html)
            val body = doc.body()
            val firstH1 = body.select("h1").first()
            if (firstH1 != null) {
                firstH1.after(summaryCardHtml)
            } else {
                body.prepend(summaryCardHtml)
            }
            doc.outerHtml()
        } catch (e: Exception) {
            summaryCardHtml + html
        }
    }

    private fun triggerGemmaSummarization() {
        if (currentArticleId == -1) return

        val gemmaManager = com.example.offlinebrowser.util.GemmaManager(this)

        if (!gemmaManager.isModelAvailable()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Gemma Model Option")
                .setMessage("No external Gemma model file is currently loaded. Would you like to download one now, or generate a summary using the built-in summary engine?")
                .setPositiveButton("Download Model") { _, _ ->
                    gemmaManager.downloadModel()
                    Toast.makeText(this, "Model download started in background. Proceeding with summary...", Toast.LENGTH_SHORT).show()
                    generateAndDisplaySummary()
                }
                .setNegativeButton("Use Internal Engine") { _, _ ->
                    generateAndDisplaySummary()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            generateAndDisplaySummary()
        }
    }

    private fun generateAndDisplaySummary() {
        Toast.makeText(this, "Generating summary with Gemma...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val database = OfflineDatabase.getDatabase(this@ArticleViewerActivity)
            val article = database.articleDao().getArticleById(currentArticleId)

            if (article != null) {
                if (article.summary.isNullOrEmpty()) {
                    val pluginRepo = ScraperPluginRepository(this@ArticleViewerActivity)
                    val matchingRecipe = pluginRepo.loadAllRecipes().firstOrNull { recipe ->
                        try {
                            Regex(recipe.domainPattern).containsMatchIn(article.url)
                        } catch (e: Exception) {
                            false
                        }
                    }
                    val gemmaManager = com.example.offlinebrowser.util.GemmaManager(this@ArticleViewerActivity)
                    val (generatedSummary, modelName) = gemmaManager.generateSummary(article.content, matchingRecipe)
                    val formattedSummary = "Generated by $modelName:\n$generatedSummary"
                    database.articleDao().updateArticleSummary(article.id, formattedSummary)
                }

                withContext(Dispatchers.Main) {
                    loadArticleContent(webView, article.id)
                    Toast.makeText(this@ArticleViewerActivity, "Summary updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val importPluginLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val json = inputStream?.bufferedReader().use { reader -> reader?.readText() }

                    if (json != null) {
                        val gson = com.google.gson.Gson()
                        gson.fromJson(json, com.example.offlinebrowser.data.model.ScraperRecipe::class.java)

                        val fileName = "plugin_${System.currentTimeMillis()}.json"
                        val file = java.io.File(filesDir, "plugins/$fileName")
                        file.parentFile?.mkdirs()
                        file.writeText(json)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ArticleViewerActivity, "Plugin imported successfully", Toast.LENGTH_SHORT).show()
                            applyPlugin()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ArticleViewerActivity, "Failed to import plugin: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showPluginMenu() {
        val url = currentArticleUrl ?: return
        val options = arrayOf("Create Plugin", "Import Plugin", "Apply Plugin", "Edit Plugin", "Search for Plugin automatically")

        android.app.AlertDialog.Builder(this)
            .setTitle("Plugin Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, com.example.offlinebrowser.ui.plugincreator.PluginCreatorActivity::class.java)
                        intent.putExtra("ARTICLE_URL", url)
                        startActivity(intent)
                    }
                    1 -> {
                        importPluginLauncher.launch("application/json")
                    }
                    2 -> {
                        applyPlugin()
                    }
                    3 -> {
                        // Edit plugin
                        lifecycleScope.launch(Dispatchers.IO) {
                            val repo = ScraperPluginRepository(this@ArticleViewerActivity)
                            val plugins = repo.loadAllRecipes()
                            val match = plugins.find { Regex(it.domainPattern).containsMatchIn(url) }

                            withContext(Dispatchers.Main) {
                                if (match != null) {
                                    val intent = Intent(this@ArticleViewerActivity, com.example.offlinebrowser.ui.plugincreator.PluginCreatorActivity::class.java)
                                    intent.putExtra("ARTICLE_URL", url)
                                    intent.putExtra("EXISTING_RECIPE_JSON", com.google.gson.Gson().toJson(match))
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this@ArticleViewerActivity, "No plugin found for this domain", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    4 -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val installed = com.example.offlinebrowser.util.PluginSearchUtil.searchAndInstallPlugin(this@ArticleViewerActivity, url)
                            if (installed) {
                                withContext(Dispatchers.Main) {
                                    applyPlugin()
                                }
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun applyPlugin() {
        if (currentArticleId == -1) return

        // Check WiFi settings before downloading
        val prefs = PreferencesRepository(this)
        val networkMonitor = NetworkMonitor(this)
        if (prefs.wifiOnly && !networkMonitor.isWifiConnected()) {
            Toast.makeText(this, "WiFi is required to re-download", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Applying plugin...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val database = OfflineDatabase.getDatabase(this@ArticleViewerActivity)
            val article = database.articleDao().getArticleById(currentArticleId)

            if (article != null) {
                val scraperPluginRepository = ScraperPluginRepository(this@ArticleViewerActivity)
                val htmlDownloader = HtmlDownloader(context = applicationContext)

                // Ensure latest plugins are loaded
                scraperPluginRepository.ensureDefaultPlugins()
                val recipes = scraperPluginRepository.loadAllRecipes()
                htmlDownloader.scraperEngine.loadRecipes(recipes)

                val newHtml = htmlDownloader.downloadHtml(article.url, article.imageUrl)

                if (newHtml != null) {
                    val updatedArticle = article.copy(content = newHtml, isCached = true)
                    database.articleDao().updateArticle(updatedArticle)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ArticleViewerActivity, "Plugin applied successfully", Toast.LENGTH_SHORT).show()
                        loadArticleContent(webView, article.id)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ArticleViewerActivity, "Failed to download article", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.nav_gemma).setOnClickListener {
            triggerGemmaSummarization()
        }
        findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, FeedSettingsActivity::class.java))
        }
    }

    companion object {
        const val RETRY_DOWNLOAD_HOST = "retry-download"
        const val RETRY_DOWNLOAD_URI = "app://retry-download"

        /**
         * Determines whether the partial/uncached banner and browser fallback card
         * should be shown for the given [article].
         */
        fun shouldShowPartialBanner(article: Article): Boolean {
            return shouldShowPartialBanner(article.isCached, article.content)
        }

        fun shouldShowPartialBanner(isCached: Boolean, content: String): Boolean {
            if (!isCached) return true
            if (content.isBlank()) return true
            return Jsoup.parse(content).text().trim().isEmpty()
        }

        fun buildPartialFooterHtml(articleUrl: String): String {
            val sanitized = HtmlDownloader.sanitizeUrl(articleUrl)
            val escapedUrl = sanitized
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

            return """
                <div id="offline-partial-banner" style="background-color: #1E1E1E; color: #E0E0E0; border: 1px solid #333333; border-left: 4px solid #4DA6FF; padding: 14px; margin: 20px 0; border-radius: 8px; font-family: sans-serif;">
                    <div style="font-weight: bold; font-size: 15px; margin-bottom: 6px; color: #4DA6FF;">Partial Preview Cached</div>
                    <div style="font-size: 13px; line-height: 1.4; color: #B0B0B0; margin-bottom: 12px;">Only a partial preview is cached offline. Open in your browser to read the full article or retry downloading.</div>
                    <div style="display: flex; gap: 14px; flex-wrap: wrap;">
                        <a href="$escapedUrl" style="color: #4DA6FF; font-weight: bold; text-decoration: none; font-size: 14px;">Open full article in browser</a>
                        <a href="$RETRY_DOWNLOAD_URI" style="color: #00E676; font-weight: bold; text-decoration: none; font-size: 14px;">Retry Download</a>
                    </div>
                </div>
            """.trimIndent()
        }

        fun injectPartialFooterHtml(html: String, articleUrl: String): String {
            val footerHtml = buildPartialFooterHtml(articleUrl)
            if (html.isBlank()) {
                return """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: sans-serif; line-height: 1.6; padding: 16px; color: #E0E0E0; background-color: #121212; }
                        </style>
                    </head>
                    <body>
                        $footerHtml
                    </body>
                    </html>
                """.trimIndent()
            }

            return try {
                val doc = Jsoup.parse(html)
                doc.body().append(footerHtml)
                doc.outerHtml()
            } catch (_: Exception) {
                html + footerHtml
            }
        }
    }
}
