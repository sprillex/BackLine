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
import com.example.offlinebrowser.ui.KiwixSearchActivity
import com.example.offlinebrowser.ui.LibraryActivity
import com.example.offlinebrowser.ui.WeatherActivity
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class FeedSettingsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var feedAdapter: FeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_settings)

        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnCustomFeeds = findViewById<Button>(R.id.btnCustomFeeds)
        val btnBrowseTopFeeds = findViewById<Button>(R.id.btnBrowseTopFeeds)
        val rvFeeds = findViewById<RecyclerView>(R.id.rvFeeds)
        val btnWeather = findViewById<Button>(R.id.btnWeather)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

}
