package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
import com.example.offlinebrowser.ui.WeatherActivity
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class FeedSettingsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var feedAdapter: FeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_settings)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnAddRss = findViewById<Button>(R.id.btnAddRss)
        val btnAddMastodon = findViewById<Button>(R.id.btnAddMastodon)
        val btnAddHtml = findViewById<Button>(R.id.btnAddHtml)
        val rvFeeds = findViewById<RecyclerView>(R.id.rvFeeds)
        val btnWeather = findViewById<Button>(R.id.btnWeather)
        val btnBindery = findViewById<Button>(R.id.btnBindery)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        feedAdapter = FeedAdapter(
            onFeedClick = { feed ->
                // Navigate to Article List
                 val intent = Intent(this, ArticleListActivity::class.java)
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

        btnAddRss.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                showAddFeedDialog(url, FeedType.RSS)
                etUrl.text.clear()
            }
        }

        btnAddMastodon.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                showAddFeedDialog(url, FeedType.MASTODON)
                etUrl.text.clear()
            }
        }

        btnAddHtml.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                showAddFeedDialog(url, FeedType.HTML)
                etUrl.text.clear()
            }
        }

        btnWeather.setOnClickListener {
             val intent = Intent(this, WeatherActivity::class.java)
             startActivity(intent)
        }

        btnBindery.setOnClickListener {
            val intent = Intent(this, BinderyActivity::class.java)
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

    private fun showAddFeedDialog(url: String, type: FeedType) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_feed, null)
        val etDownloadLimit = dialogView.findViewById<EditText>(R.id.etDownloadLimit)
        val etCategory = dialogView.findViewById<EditText>(R.id.etCategory)
        val cbSyncNow = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSyncNow)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Feed Settings")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val limit = etDownloadLimit.text.toString().toIntOrNull() ?: 0
                val category = etCategory.text.toString().takeIf { it.isNotEmpty() }
                val syncNow = cbSyncNow.isChecked
                viewModel.addFeed(url, type, limit, category, syncNow)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
