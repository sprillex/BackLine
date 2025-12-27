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
            Log.d(TAG, "Opened ZIM file: ${file.name}")

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    return handleRequest(url)
                }
            }

            /*
            // Commenting out method calls to verify imports and basic instantiation first
            // Load main page
            val randomTitle = zimReader?.getRandomTitle()
            if (randomTitle != null) {
                 val url = "zim://${randomTitle}"
                 webView.loadUrl(url)
            } else {
                 webView.loadData("<h1>Unable to read ZIM file</h1>", "text/html", "UTF-8")
            }
            */
            webView.loadData("<h1>ZIM Reader Debug Mode</h1><p>File opened successfully.</p>", "text/html", "UTF-8")

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening ZIM: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleRequest(url: String): WebResourceResponse? {
        if (!url.startsWith("zim://")) return null

        // val title = url.removePrefix("zim://")

        try {
            /*
            // Commenting out method calls to verify imports
            val html = zimReader?.getHtmlForTitle(title)
            if (html != null) {
                return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
            }
            */

            return null

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            zimReader?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "ZimViewerActivity"
    }
}
