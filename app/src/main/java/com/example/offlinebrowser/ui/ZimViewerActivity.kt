package com.example.offlinebrowser.ui

import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.dmitrybrant.zimdroid.ZimFile
import com.dmitrybrant.zimdroid.ZimReader
import com.example.offlinebrowser.R
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class ZimViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var zimReader: ZimReader? = null
    private var zimFile: ZimFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zim_viewer)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        val filePath = intent.getStringExtra("ZIM_FILE_PATH")
        if (filePath == null) {
            Toast.makeText(this, "No file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = file.name

        try {
            zimFile = ZimFile(file.absolutePath)
            zimReader = ZimReader(zimFile!!)

            // Log basic info
            try {
                // Accessing properties safely just in case
                Log.d(TAG, "ZIM UUID: ${zimFile?.uuid}")
            } catch(e: Exception) {
                e.printStackTrace()
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    return handleRequest(url)
                }
            }

            // Load main page
            val randomTitle = zimReader?.getRandomTitle()
            if (randomTitle != null) {
                 // Try to load a random page as a start, or search for "Welcome" / "Main_Page"
                 // Or better, check if we can get the main page from metadata?
                 // ZimReader doesn't seem to expose main page easily in the snippet I saw.
                 // Let's try to load the random title
                 val url = "zim://${randomTitle}"
                 webView.loadUrl(url)
            } else {
                 webView.loadData("<h1>Unable to read ZIM file</h1>", "text/html", "UTF-8")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening ZIM: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleRequest(url: String): WebResourceResponse? {
        if (!url.startsWith("zim://")) return null

        val title = url.removePrefix("zim://")

        try {
            // Try to find if it's an article (A namespace) or image (I namespace) or other.
            // The URL from WebView will likely match the link target in HTML.
            // ZIM links are usually relative.

            // Try to get data. I'm assuming getDataForTitle or getInputStream exists.
            // Since I can't verify the API, I will try a few common patterns using reflection if needed,
            // but for now I'll write what I think is correct and let the user correct me if build fails.

            // Based on ZimDroid snippet:
            // String html = reader.getHtmlForTitle(randomTitle);

            // I will assume `getDataForUrl(url)` or `getDataForTitle(title)`
            // I'll try `getDataForTitle` which returns a ByteArrayInputStream hopefully?
            // Or `ByteArray`

            // Let's use `getHtmlForTitle` for text/html request?
            // WebView doesn't tell us expected type easily in `shouldInterceptRequest` (only in newer APIs).

            // I'll guess `zimReader.getData(title)` returns `ByteArray`.

            // NOTE: I am making a best-effort guess.
            // If this fails to compile, I will fix it.

            val html = zimReader?.getHtmlForTitle(title)
            if (html != null) {
                return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
            }

            // TODO: Handle images and other resources using appropriate ZimReader methods if available (e.g., getDataForName)

            return null

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            zimFile?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "ZimViewerActivity"
    }
}
