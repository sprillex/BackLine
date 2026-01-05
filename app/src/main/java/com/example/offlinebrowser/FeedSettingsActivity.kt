package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.ui.BinderyActivity
import com.example.offlinebrowser.ui.FeedAdapter
import com.example.offlinebrowser.ui.KiwixSearchActivity
import com.example.offlinebrowser.ui.LibraryActivity
import com.example.offlinebrowser.ui.WeatherActivity
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class FeedSettingsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var feedAdapter: FeedAdapter

    private val importCsvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            importFeedsFromCsv(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_settings)

        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnCustomFeeds = findViewById<Button>(R.id.btnCustomFeeds)
        val btnBrowseTopFeeds = findViewById<Button>(R.id.btnBrowseTopFeeds)
        val btnImportFeedList = findViewById<Button>(R.id.btnImportFeedList)
        val rvFeeds = findViewById<RecyclerView>(R.id.rvFeeds)
        val btnWeather = findViewById<Button>(R.id.btnWeather)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnImportFeedList.setOnClickListener {
            importCsvLauncher.launch("text/*")
        }

        btnCustomFeeds.setOnClickListener {
            startActivity(Intent(this, CustomFeedsActivity::class.java))
        }

        feedAdapter = FeedAdapter(
            onFeedClick = { feed ->
                // Navigate to Article List
                 val intent = Intent(this, HomeActivity::class.java)
                 intent.putExtra("FEED_ID", feed.id)
                 startActivity(intent)
            },
            onSyncClick = { feed -> viewModel.syncFeed(feed) },
            onDeleteClick = { feed -> viewModel.deleteFeed(feed) },
            onEditClick = { feed ->
                val intent = Intent(this, EditFeedActivity::class.java)
                intent.putExtra("FEED_ID", feed.id)
                startActivity(intent)
            }
        )

        rvFeeds.layoutManager = LinearLayoutManager(this)
        rvFeeds.adapter = feedAdapter

        btnBrowseTopFeeds.setOnClickListener {
            val intent = Intent(this, SuggestedFeedsActivity::class.java)
            startActivity(intent)
        }

        btnWeather.setOnClickListener {
             val intent = Intent(this, WeatherActivity::class.java)
             startActivity(intent)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.feeds.collect { feeds ->
                    feedAdapter.submitList(feeds)
                }
            }
        }
    }

    private fun importFeedsFromCsv(uri: Uri) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                        val lines = reader.readLines()
                        val feeds = parseCsvLines(lines)
                        var addedCount = 0
                        feeds.forEach { (name, url, category) ->
                            viewModel.addFeed(
                                url = url,
                                name = name,
                                category = category,
                                type = FeedType.RSS, // Defaulting to RSS
                                downloadLimit = 5,
                                syncNow = false
                            )
                            addedCount++
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(this@FeedSettingsActivity, "Imported $addedCount feeds", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                 kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                     Toast.makeText(this@FeedSettingsActivity, "Error importing CSV: ${e.message}", Toast.LENGTH_LONG).show()
                 }
                 e.printStackTrace()
            }
        }
    }

    private data class ParsedFeed(val name: String, val url: String, val category: String)

    private val csvSplitRegex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()

    private fun parseCsvLines(lines: List<String>): List<ParsedFeed> {
        val feeds = mutableListOf<ParsedFeed>()
        if (lines.isEmpty()) return feeds

        var nameIdx = -1
        var urlIdx = -1
        var catIdx = -1
        var headerRowIndex = -1

        // 1. Try to find a header row
        for (i in 0 until minOf(lines.size, 5)) {
            val line = lines[i]
            if (line.isBlank()) continue
            val tokens = line.lowercase().split(csvSplitRegex).map { it.trim().removeSurrounding("\"") }

            if (tokens.contains("url") || tokens.contains("link") || tokens.contains("address")) {
                headerRowIndex = i
                urlIdx = tokens.indexOfFirst { it == "url" || it == "link" || it == "address" }
                nameIdx = tokens.indexOf("name")
                catIdx = tokens.indexOf("category")
                break
            }
        }

        // 2. If no header found, try to sniff columns from data
        if (urlIdx == -1) {
             for (i in 0 until minOf(lines.size, 5)) {
                val line = lines[i]
                if (line.isBlank()) continue
                val tokens = line.split(csvSplitRegex).map { it.trim().removeSurrounding("\"") }

                // Find a token that looks like a URL
                val foundUrlIdx = tokens.indexOfFirst { it.startsWith("http://", true) || it.startsWith("https://", true) }
                if (foundUrlIdx != -1) {
                    urlIdx = foundUrlIdx
                    // Guess name index
                    if (urlIdx > 0) nameIdx = 0
                    break
                }
             }
        }

        // 3. Parse data
        val startRow = if (headerRowIndex != -1) headerRowIndex + 1 else 0

        for (i in startRow until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val tokens = line.split(csvSplitRegex).map { it.trim().removeSurrounding("\"") }

            var name = ""
            var url = ""
            var category = "Imported"

            if (urlIdx != -1) {
                if (urlIdx < tokens.size) url = tokens[urlIdx]
                if (nameIdx != -1 && nameIdx < tokens.size) name = tokens[nameIdx]
                if (catIdx != -1 && catIdx < tokens.size) category = tokens[catIdx]
            } else {
                // Total fallback (Simple mode) - assumes URL is first or second column if not detected
                if (tokens.size == 1) {
                     url = tokens[0]
                     name = url
                } else if (tokens.size >= 2) {
                     // If we couldn't detect URL column, usually Name, URL is safer guess than URL, Name?
                     // But wait, standard is Name, URL.
                     name = tokens[0]
                     url = tokens[1]
                }
            }

            // Clean up
            url = url.trim()
            name = name.trim()
            category = category.trim()

            // Validate URL roughly
            if (url.isNotBlank() && (url.startsWith("http", true))) {
                if (name.isBlank()) name = url
                feeds.add(ParsedFeed(name, url, category))
            }
        }

        return feeds
    }
}
