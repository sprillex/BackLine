package com.example.offlinebrowser.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.model.TrustedServer
import com.example.offlinebrowser.data.network.SafeClientFactory
import com.example.offlinebrowser.workers.ZimDownloadWorker
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException

class BinderyActivity : AppCompatActivity() {

    private lateinit var etBinderyUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnScanQr: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var rvModules: RecyclerView
    private var currentUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bindery)

        etBinderyUrl = findViewById(R.id.etBinderyUrl)
        btnConnect = findViewById(R.id.btnConnect)
        btnScanQr = findViewById(R.id.btnScanQr)
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

        btnScanQr.setOnClickListener {
            startQrScan()
        }
    }

    private fun startQrScan() {
        val scanner = GmsBarcodeScanning.getClient(this)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue != null) {
                    processQrContent(rawValue)
                } else {
                    Toast.makeText(this, "QR Code empty", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "QR Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processQrContent(json: String) {
        lifecycleScope.launch {
            try {
                val jsonObject = JSONObject(json)
                val ip = jsonObject.getString("ip")
                val port = jsonObject.getInt("port")
                val hash = jsonObject.getString("hash")

                val database = OfflineDatabase.getDatabase(applicationContext)
                val trustedServer = TrustedServer(ip = ip, port = port, fingerprint = hash)
                database.trustedServerDao().insertTrustedServer(trustedServer)

                etBinderyUrl.setText("https://$ip:$port/")
                Toast.makeText(this@BinderyActivity, "Server added and trusted.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BinderyActivity, "Invalid QR Format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchModules(url: String) {
        progressBar.visibility = View.VISIBLE
        rvModules.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use SafeClientFactory to support self-signed pinned certs
                val client = SafeClientFactory.create(applicationContext)
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                     throw IOException("Unexpected code $response")
                }
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html, url)

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

        // Use WorkManager for download to support secure client
        val data = Data.Builder()
            .putString("url", module.downloadUrl)
            .putString("title", module.title)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<ZimDownloadWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueue(downloadWork)
        Toast.makeText(this, "Download queued...", Toast.LENGTH_SHORT).show()
    }
}
