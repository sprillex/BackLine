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
import com.example.offlinebrowser.data.model.SuggestedFeed
import com.example.offlinebrowser.ui.SuggestedFeedAdapter
import android.view.Menu
import android.view.MenuItem
import com.example.offlinebrowser.ui.RepositoryBrowserDialogFragment
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class SuggestedFeedsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: SuggestedFeedAdapter
    private var allFeeds: List<SuggestedFeed> = emptyList()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggested_feeds)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val rvSuggestedFeeds = findViewById<RecyclerView>(R.id.rvSuggestedFeeds)
        val fab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_feed)

        fab.setOnClickListener {
            RepositoryBrowserDialogFragment().show(supportFragmentManager, "browser")
        }

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
                    allFeeds = feeds
                    filterFeeds(currentQuery)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_suggested_feeds, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                filterFeeds(currentQuery)
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_download_feeds) {
            RepositoryBrowserDialogFragment().show(supportFragmentManager, "browser")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun filterFeeds(query: String) {
        val filteredList = if (query.isEmpty()) {
            allFeeds
        } else {
            allFeeds.filter { feed ->
                feed.name.contains(query, ignoreCase = true) ||
                        feed.category.contains(query, ignoreCase = true) ||
                        feed.url.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filteredList)
    }
}
