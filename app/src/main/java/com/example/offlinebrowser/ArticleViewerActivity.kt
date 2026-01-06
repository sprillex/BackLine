package com.example.offlinebrowser

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.offlinebrowser.data.local.OfflineDatabase
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
        // Set WebView background to transparent to allow activity background to show through if needed,
        // but typically we want the HTML body to define the background.
        // However, for Force Dark to work effectively, keeping it default or handling it via content is best.
        // Setting it to transparent might help avoid the white flash or white background if content is transparent.
        webView.setBackgroundColor(Color.TRANSPARENT)

        val fabDarkMode = findViewById<FloatingActionButton>(R.id.fab_dark_mode)

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
                    val content = feed.content
                    val html = if (content.contains("<html", ignoreCase = true)) {
                        content
                    } else {
                        wrapContent(content)
                    }
                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            }
        }

        fabDarkMode.setOnClickListener {
            toggleDarkMode(webView)
        }

        setupBottomNav()
    }

    private fun wrapContent(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: system-ui, -apple-system, sans-serif;
                        line-height: 1.6;
                        padding: 16px;
                        color: #212121;
                        background-color: #ffffff;
                    }
                    img { max-width: 100%; height: auto; }
                    /* Support system dark mode preference */
                    @media (prefers-color-scheme: dark) {
                        body {
                            color: #e0e0e0;
                            background-color: #121212;
                        }
                        a { color: #8ab4f8; }
                    }
                </style>
            </head>
            <body>
                $content
            </body>
            </html>
        """.trimIndent()
    }

    private fun toggleDarkMode(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            isDarkMode = !isDarkMode
            if (isDarkMode) {
                // FORCE_DARK_ON will force dark mode even if the content doesn't support it.
                // It might invert our already dark-themed content if we are not careful,
                // but usually it respects dark themes if they are explicit.
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
