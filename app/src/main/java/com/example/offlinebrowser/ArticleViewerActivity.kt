package com.example.offlinebrowser

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

class ArticleViewerActivity : AppCompatActivity() {

    private var isDarkMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_viewer)

        val webView = findViewById<WebView>(R.id.webView)
        // Set transparent background to avoid white flashes
        webView.setBackgroundColor(Color.TRANSPARENT)
        // Enable JavaScript for the theme toggle
        webView.settings.javaScriptEnabled = true

        val fabDarkMode = findViewById<FloatingActionButton>(R.id.fab_dark_mode)

        val articleId = intent.getIntExtra("ARTICLE_ID", -1)
        if (articleId != -1) {
            // Fetch content from DB
            val database = OfflineDatabase.getDatabase(this)

            lifecycleScope.launch {
                val processedHtml = withContext(Dispatchers.IO) {
                    val feed = database.articleDao().getArticleById(articleId)
                    if (feed != null) {
                        processContentForDisplay(feed.content)
                    } else {
                        null
                    }
                }

                if (processedHtml != null) {
                    webView.loadDataWithBaseURL(null, processedHtml, "text/html", "UTF-8", null)
                }
            }
        }

        fabDarkMode.setOnClickListener {
            toggleDarkMode(webView)
        }

        setupBottomNav()
    }

    private fun processContentForDisplay(rawContent: String): String {
        // 1. Define Safelist (Relaxed but with necessary restrictions)
        // We use relaxed to allow basic formatting and images, but we must ensure we don't block
        // standard layout tags too aggressively. Relaxed covers most:
        // a, b, blockquote, br, caption, cite, code, col, colgroup, dd, div, dl, dt, em, h1-h6, i, img, li, ol, p, pre, q, small, span, strike, strong, sub, sup, table, tbody, td, tfoot, th, thead, tr, u, ul
        val safelist = Safelist.relaxed()
            .addTags("iframe", "video", "source") // Allow media if trusted, but careful with iframes. For now, we allow them as they are common in feeds.
            .addAttributes("iframe", "src", "width", "height", "allowfullscreen")
            .addAttributes("video", "src", "controls", "width", "height")
            .addAttributes(":all", "class", "id") // Allow class/id for styling if needed, but we strip inline styles below implicitly via clean
            // explicitly strip style attributes if Safelist doesn't already (Relaxed defaults to stripping them)
            // Safelist.relaxed() DOES NOT allow 'style' attribute by default, so it will be stripped.

        // 2. Parse and Clean
        // Jsoup.parse handles both fragments and full docs.
        // If it's a fragment, it puts it in body.
        val dirtyDoc = Jsoup.parse(rawContent)

        // Cleaner(safelist).clean(dirtyDoc) creates a new clean Document.
        // It preserves the structure that matches the safelist.
        val cleaner = Cleaner(safelist)
        val cleanDoc = cleaner.clean(dirtyDoc)

        // 3. Inject our Dark Mode CSS and JS
        // Ensure head exists (cleaner might have made a fresh one)
        val head = cleanDoc.head() ?: cleanDoc.appendElement("head")

        head.append("""
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    font-family: system-ui, -apple-system, sans-serif;
                    line-height: 1.6;
                    padding: 16px;
                    color: #212121;
                    background-color: #ffffff;
                    transition: background-color 0.3s, color 0.3s;
                }
                img { max-width: 100%; height: auto; }

                /* Dark Mode Class */
                body.dark-mode {
                    background-color: #121212 !important;
                    color: #e0e0e0 !important;
                }
                body.dark-mode a { color: #8ab4f8 !important; }

                /* Force text elements to inherit color in dark mode */
                body.dark-mode p, body.dark-mode h1, body.dark-mode h2, body.dark-mode h3,
                body.dark-mode h4, body.dark-mode h5, body.dark-mode h6, body.dark-mode span,
                body.dark-mode div, body.dark-mode li, body.dark-mode td, body.dark-mode th,
                body.dark-mode blockquote, body.dark-mode pre, body.dark-mode code {
                    color: #e0e0e0 !important;
                    background-color: transparent !important;
                }
            </style>
            <script>
                function toggleTheme() {
                    document.body.classList.toggle('dark-mode');
                }
            </script>
        """.trimIndent())

        return cleanDoc.outerHtml()
    }

    private fun toggleDarkMode(webView: WebView) {
        // We now rely on JS toggle instead of Force Dark
        webView.evaluateJavascript("toggleTheme()", null)

        isDarkMode = !isDarkMode
        if (isDarkMode) {
            Toast.makeText(this, "Dark Mode On", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Dark Mode Off", Toast.LENGTH_SHORT).show()
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
