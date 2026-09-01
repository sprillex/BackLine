package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ArticleViewerActivity : AppCompatActivity() {

    private var currentArticleUrl: String? = null
    private var currentArticleId: Int = -1


    private var isDarkMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_viewer)

        val webView = findViewById<WebView>(R.id.webView)
        val fabDarkMode = findViewById<FloatingActionButton>(R.id.fab_dark_mode)

        // Do not block network images globally; we will filter them in WebViewClient to allow local/cached content
        webView.settings.blockNetworkImage = false
        // Allow file access to load locally cached images
        webView.settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {
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

    private fun loadArticleContent(webView: WebView, articleId: Int) {
        val database = OfflineDatabase.getDatabase(this)
        lifecycleScope.launch {
            val feed = withContext(Dispatchers.IO) {
                database.articleDao().getArticleById(articleId)
            }

            if (feed != null) {
                currentArticleUrl = feed.url
                var content = feed.content

                if (!feed.summary.isNullOrEmpty()) {
                    content = injectSummaryHtml(content, feed.summary)
                }

                if (feed.localImagePath != null) {
                    try {
                        val doc = Jsoup.parse(content)
                        var changed = false

                        if (feed.imageUrl != null) {
                            val images = doc.select("img[src]")
                            for (img in images) {
                                if (img.attr("src") == feed.imageUrl) {
                                    img.attr("src", "file://${feed.localImagePath}")
                                    changed = true
                                }
                            }
                        }

                        if (!changed) {
                            val injectedImage = doc.select("img[alt='Article Image']").first()
                            if (injectedImage != null) {
                                injectedImage.attr("src", "file://${feed.localImagePath}")
                                changed = true
                            }
                        }

                        if (changed) {
                            content = doc.outerHtml()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (feed.imageUrl != null) {
                            content = content.replace(feed.imageUrl, "file://${feed.localImagePath}")
                        }
                    }
                }
                webView.loadDataWithBaseURL("file:///", content, "text/html", "UTF-8", null)
            }
        }
    }

    private fun injectSummaryHtml(html: String, summary: String): String {
        val summaryCardHtml = """
            <div style="background-color: #262626; color: #E0E0E0; border-left: 4px solid #00E5FF; padding: 14px; margin: 15px 0; border-radius: 6px; font-family: sans-serif;">
                <div style="font-weight: bold; font-size: 16px; margin-bottom: 8px; color: #00E5FF;">✨ Gemma Summary</div>
                <div style="font-size: 14px; line-height: 1.5;">${Jsoup.clean(summary, org.jsoup.safety.Safelist.basic())}</div>
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
                    val gemmaManager = com.example.offlinebrowser.util.GemmaManager(this@ArticleViewerActivity)
                    val generated = gemmaManager.generateSummary(article.content)
                    database.articleDao().updateArticleSummary(article.id, generated)
                }

                withContext(Dispatchers.Main) {
                    val webView = findViewById<WebView>(R.id.webView)
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
                        val recipe = gson.fromJson(json, com.example.offlinebrowser.data.model.ScraperRecipe::class.java)

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
                            val repo = com.example.offlinebrowser.data.repository.ScraperPluginRepository(this@ArticleViewerActivity)
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
        val networkMonitor = com.example.offlinebrowser.util.NetworkMonitor(this)
        if (prefs.wifiOnly && !networkMonitor.isWifiConnected()) {
            Toast.makeText(this, "WiFi is required to re-download", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Applying plugin...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val database = OfflineDatabase.getDatabase(this@ArticleViewerActivity)
            val article = database.articleDao().getArticleById(currentArticleId)

            if (article != null) {
                val scraperPluginRepository = com.example.offlinebrowser.data.repository.ScraperPluginRepository(this@ArticleViewerActivity)
                val htmlDownloader = com.example.offlinebrowser.data.network.HtmlDownloader()

                // Ensure latest plugins are loaded
                scraperPluginRepository.ensureDefaultPlugins()
                val recipes = scraperPluginRepository.loadAllRecipes()
                htmlDownloader.scraperEngine.loadRecipes(recipes)

                val newHtml = htmlDownloader.downloadHtml(article.url, article.imageUrl)

                if (newHtml != null) {
                    val updatedArticle = article.copy(content = newHtml)
                    database.articleDao().updateArticle(updatedArticle)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ArticleViewerActivity, "Plugin applied successfully", Toast.LENGTH_SHORT).show()
                        // Reload activity
                        recreate()
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
}
