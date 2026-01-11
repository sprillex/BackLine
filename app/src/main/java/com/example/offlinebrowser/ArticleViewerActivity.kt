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
            // Fetch content from DB
            val database = OfflineDatabase.getDatabase(this)

            lifecycleScope.launch {
                val feed = withContext(Dispatchers.IO) {
                    // We need a method to get article by ID
                     database.articleDao().getArticleById(articleId)
                }

                if (feed != null) {
                    var content = feed.content
                    // If we have a local cached image, replace the remote URL in the content with the local path
                    if (feed.localImagePath != null) {
                        try {
                            val doc = Jsoup.parse(content)
                            var changed = false

                            // Strategy 1: Find by exact src match (handling HTML entities via Jsoup)
                            if (feed.imageUrl != null) {
                                val images = doc.select("img[src]")
                                for (img in images) {
                                    // Jsoup's attr("src") returns the decoded URL
                                    if (img.attr("src") == feed.imageUrl) {
                                        img.attr("src", "file://${feed.localImagePath}")
                                        changed = true
                                    }
                                }
                            }

                            // Strategy 2: Fallback to "Article Image" alt text if specific replacement failed
                            // This catches cases where the URL might differ slightly or Strategy 1 missed it
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
                            // Fallback to simple replacement if Jsoup fails for some reason
                            if (feed.imageUrl != null) {
                                content = content.replace(feed.imageUrl, "file://${feed.localImagePath}")
                            }
                        }
                    }
                    // Use file:/// base URL to allow loading local images
                    webView.loadDataWithBaseURL("file:///", content, "text/html", "UTF-8", null)
                }
            }
        }

        fabDarkMode.setOnClickListener {
            toggleDarkMode(webView)
        }

        setupBottomNav()
    }

    private fun toggleDarkMode(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            isDarkMode = !isDarkMode
            if (isDarkMode) {
                WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_ON)
                Toast.makeText(this, "Dark Mode On", Toast.LENGTH_SHORT).show()
            } else {
                WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_OFF)
                Toast.makeText(this, "Dark Mode Off", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Dark Mode not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.nav_content).setOnClickListener {
             // Navigate to HomeActivity in Content mode
             val intent = Intent(this, HomeActivity::class.java)
             intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
             intent.putExtra("OPEN_CONTENT", true)
             startActivity(intent)
             finish()
        }
        findViewById<View>(R.id.nav_settings).setOnClickListener {
             startActivity(Intent(this, FeedSettingsActivity::class.java))
        }
    }
}
