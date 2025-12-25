package com.example.offlinebrowser.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

class BinderyActivity : AppCompatActivity() {

    private lateinit var etBinderyUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var rvModules: RecyclerView
    private var currentUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bindery)

        etBinderyUrl = findViewById(R.id.etBinderyUrl)
        btnConnect = findViewById(R.id.btnConnect)
        progressBar = findViewById(R.id.progressBar)
        rvModules = findViewById(R.id.rvModules)

        rvModules.layoutManager = LinearLayoutManager(this)

        btnConnect.setOnClickListener {
            val url = etBinderyUrl.text.toString()
            if (url.isNotEmpty()) {
                currentUrl = url
                fetchModules(url)
            }
        }
    }

    private fun fetchModules(url: String) {
        progressBar.visibility = View.VISIBLE
        rvModules.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url).get()
                val moduleDivs = doc.select("div.module")
                val modules = moduleDivs.map { div ->
                    val title = div.select("h3").text()
                    // Description is the first p tag
                    val description = div.select("p").first()?.text() ?: ""

                    // Download link is in the anchor with text "Download ZIM"
                    val downloadLink = div.select("a").firstOrNull { it.text().contains("Download ZIM", ignoreCase = true) }
                    val href = downloadLink?.attr("href") ?: ""

                    // Construct full URL
                    val fullDownloadUrl = if (href.startsWith("http")) {
                        href
                    } else {
                        val baseUrl = if (url.endsWith("/")) url.dropLast(1) else url
                        val relativePath = if (href.startsWith("/")) href else "/$href"
                        baseUrl + relativePath
                    }

                    BinderyModule(title, description, fullDownloadUrl)
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    rvModules.visibility = View.VISIBLE
                    rvModules.adapter = BinderyAdapter(modules) { module ->
                        downloadZim(module)
                    }
                    if (modules.isEmpty()) {
                        Toast.makeText(this@BinderyActivity, "No modules found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@BinderyActivity, "Error connecting: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadZim(module: BinderyModule) {
        if (module.downloadUrl.isEmpty()) {
            Toast.makeText(this, "No download URL found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(module.downloadUrl))
            request.setTitle("Downloading ${module.title}")
            request.setDescription("Downloading ZIM file for ${module.title}")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${module.title.replace(" ", "_")}.zim")

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
