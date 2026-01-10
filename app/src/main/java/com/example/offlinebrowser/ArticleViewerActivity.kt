package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArticleViewerActivity : AppCompatActivity() {

    private var isDarkMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_viewer)

        val webView = findViewById<WebView>(R.id.webView)
        val fabDarkMode = findViewById<FloatingActionButton>(R.id.fab_dark_mode)

        // Block network images to save data and ensure offline behavior
        webView.settings.blockNetworkImage = true
        // Allow file access to load locally cached images
        webView.settings.allowFileAccess = true

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
                    // Dynamically replace remote image URL with local path if available
                    // This ensures that even if the HTML was saved with the remote URL, we use the cached image
                    val imageUrl = feed.imageUrl
                    val localImagePath = feed.localImagePath
                    if (!imageUrl.isNullOrEmpty() && !localImagePath.isNullOrEmpty()) {
                         // 1. Try to replace existing remote URL
                         val newContent = content.replace(imageUrl, "file://$localImagePath")

                         // 2. If replacement didn't happen (or URL wasn't there) and it's not already injected
                         if (newContent == content && !content.contains("file://$localImagePath")) {
                             // Inject the image manually
                             val imgTag = "<img src=\"file://$localImagePath\" alt=\"Article Image\" style=\"width:100%; height:auto; margin-bottom:16px;\" /><br/>"

                             // Try to insert after </h1> to match ScraperEngine style
                             val h1End = "</h1>"
                             val index = content.indexOf(h1End)
                             content = if (index != -1) {
                                 content.substring(0, index + h1End.length) + imgTag + content.substring(index + h1End.length)
                             } else {
                                 // Try to insert after <body>
                                 val bodyStart = "<body>"
                                 val bodyIndex = content.indexOf(bodyStart)
                                 if (bodyIndex != -1) {
                                     content.substring(0, bodyIndex + bodyStart.length) + imgTag + content.substring(bodyIndex + bodyStart.length)
                                 } else {
                                     // Just prepend
                                     imgTag + content
                                 }
                             }
                         } else {
                             content = newContent
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
