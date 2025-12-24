package com.example.offlinebrowser

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class ArticleViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        val content = intent.getStringExtra("CONTENT") ?: ""
        // Use loadDataWithBaseURL to handle basic html
        webView.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
    }
}
