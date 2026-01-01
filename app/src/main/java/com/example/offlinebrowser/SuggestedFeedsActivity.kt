package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.ui.SuggestedFeedAdapter
import android.view.Menu
import android.view.MenuItem
import com.example.offlinebrowser.ui.RepositoryBrowserDialogFragment
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class SuggestedFeedsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: SuggestedFeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggested_feeds)

        val rvSuggestedFeeds = findViewById<RecyclerView>(R.id.rvSuggestedFeeds)

        adapter = SuggestedFeedAdapter { suggestedFeed ->
            viewModel.addFeed(
                url = suggestedFeed.url,
                type = FeedType.RSS, // Assuming RSS for now based on CSV
                downloadLimit = 0,
                category = suggestedFeed.category,
                syncNow = true,
                onFeedAdded = { feedId ->
                    val intent = Intent(this, EditFeedActivity::class.java)
                    intent.putExtra("FEED_ID", feedId.toInt())
                    startActivity(intent)
                }
            )
            Toast.makeText(this, "Added ${suggestedFeed.name}", Toast.LENGTH_SHORT).show()
        }

        rvSuggestedFeeds.layoutManager = LinearLayoutManager(this)
        rvSuggestedFeeds.adapter = adapter

        // Get repository from App (Manual DI)
        val repository = (application as OfflineBrowserApp).suggestedFeedRepository

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.suggestedFeeds.collect { feeds ->
                    adapter.submitList(feeds)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Download Official Feeds")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            RepositoryBrowserDialogFragment().show(supportFragmentManager, "browser")
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
