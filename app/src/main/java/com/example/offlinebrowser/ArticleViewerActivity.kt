package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.util.FileLogger
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ArticleViewerActivity : AppCompatActivity() {

    private var isDarkMode = false
    private lateinit var fileLogger: FileLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_viewer)

        fileLogger = FileLogger(this)
        val webView = findViewById<WebView>(R.id.webView)
        val fabDarkMode = findViewById<FloatingActionButton>(R.id.fab_dark_mode)

        // Block network images to save data and ensure offline behavior
        webView.settings.blockNetworkImage = true
        // Allow file access to load locally cached images
        webView.settings.allowFileAccess = true
        // Allow file access from file URLs (important since we use file:/// base URL)
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true

        // Capture WebView console messages for debugging
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                fileLogger.log("WebView Console: ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                return true
            }
        }

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                if (preferencesRepository.detailedDebuggingEnabled) {
                     fileLogger.log("WebView Loading Resource: $url")
                }
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (preferencesRepository.detailedDebuggingEnabled) {
                    fileLogger.log("WebView Error: ${error?.description} for ${request?.url}")
                }
            }
        }

        // Handle user preference for showing images in article view
        // Since we are blocking network images, this mostly controls local/injected images
        val preferencesRepository = PreferencesRepository(this)
        val showImages = preferencesRepository.showImagesInArticleView
        webView.settings.loadsImagesAutomatically = showImages

        if (preferencesRepository.detailedDebuggingEnabled) {
            fileLogger.log("ArticleViewer: detailedDebuggingEnabled=true, showImages=$showImages")
        }

        val articleId = intent.getIntExtra("ARTICLE_ID", -1)
        if (articleId != -1) {
            // Fetch content from DB
            val database = OfflineDatabase.getDatabase(this)

            lifecycleScope.launch {
                val feed = withContext(Dispatchers.IO) {
                    val article = database.articleDao().getArticleById(articleId)
                    if (article != null) {
                        if (preferencesRepository.detailedDebuggingEnabled) {
                            fileLogger.log("ArticleViewer: Loaded article ${article.id}: ${article.title}")
                            fileLogger.log("ArticleViewer: imageUrl=${article.imageUrl}")
                            fileLogger.log("ArticleViewer: localImagePath=${article.localImagePath}")
                            if (article.localImagePath != null) {
                                val file = File(article.localImagePath)
                                fileLogger.log("ArticleViewer: Local file exists? ${file.exists()}, Path: ${file.absolutePath}, Size: ${if(file.exists()) file.length() else 0}")
                            }
                        }

                        var content = article.content
                        // Dynamically replace remote image URL with local path if available
                        val imageUrl = article.imageUrl
                        val localImagePath = article.localImagePath
                        if (!imageUrl.isNullOrEmpty() && !localImagePath.isNullOrEmpty()) {
                             // 1. Try to replace existing remote URL
                             val newContent = content.replace(imageUrl, "file://$localImagePath")

                             // 2. If replacement didn't happen (or URL wasn't there) and it's not already injected
                             if (newContent == content && !content.contains("file://$localImagePath")) {
                                 fileLogger.log("ArticleViewer: Image URL not found in content or no replacement made. Attempting injection.")
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
                                 fileLogger.log("ArticleViewer: Injected image tag.")
                             } else {
                                 fileLogger.log("ArticleViewer: Replaced remote URL with local path.")
                                 content = newContent
                             }
                        } else {
                            fileLogger.log("ArticleViewer: No image URL or local path to process.")
                        }
                        content
                    } else {
                        fileLogger.log("ArticleViewer: Article $articleId not found in database.")
                        null
                    }
                }

                if (feed != null) {
                    // Use file:/// base URL to allow loading local images
                    webView.loadDataWithBaseURL("file:///", feed, "text/html", "UTF-8", null)
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
