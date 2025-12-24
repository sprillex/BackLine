package com.example.offlinebrowser

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinebrowser.data.local.OfflineDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArticleViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        val articleId = intent.getIntExtra("ARTICLE_ID", -1)
        if (articleId != -1) {
            // Fetch content from DB
            // Note: Ideally use ViewModel, but for simplicity here using GlobalScope/coroutines directly
            val database = OfflineDatabase.getDatabase(this)

            GlobalScope.launch(Dispatchers.Main) {
                val feed = withContext(Dispatchers.IO) {
                    // We need a method to get article by ID
                     database.articleDao().getArticleById(articleId)
                }

                if (feed != null) {
                    webView.loadDataWithBaseURL(null, feed.content, "text/html", "UTF-8", null)
                }
            }
        }
    }
}
