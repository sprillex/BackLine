package com.example.offlinebrowser.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.OfflineBrowserApp
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.GitHubContent
import kotlinx.coroutines.launch

class RepositoryBrowserDialogFragment : DialogFragment() {

    private lateinit var adapter: BrowserAdapter
    private var currentPath = "https://api.github.com/repos/sprillex/BackLine/contents/rss_feeds"
    private val pathStack = mutableListOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle("Browse Repository")
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Since we don't have a layout XML for this, we'll create one programmatically or use a simple existing one
        // For simplicity, I'll create a RecyclerView dynamically
        val context = requireContext()
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        adapter = BrowserAdapter { item ->
            handleItemClick(item)
        }
        recyclerView.adapter = adapter

        loadDirectory(currentPath)

        return recyclerView
    }

    private fun handleItemClick(item: GitHubContent) {
        if (item.name == "..") {
            if (pathStack.isNotEmpty()) {
                currentPath = pathStack.removeAt(pathStack.size - 1)
                loadDirectory(currentPath)
            }
        } else if (item.type == "dir") {
            pathStack.add(currentPath)
            currentPath = item.url
            loadDirectory(currentPath)
        } else if (item.name.endsWith(".csv") && item.download_url != null) {
            downloadFeed(item.download_url)
        }
    }

    private fun loadDirectory(url: String) {
        val repository = (requireActivity().application as OfflineBrowserApp).suggestedFeedRepository
        lifecycleScope.launch {
            try {
                val items = repository.fetchRemoteDirectory(url).toMutableList()
                if (pathStack.isNotEmpty()) {
                    items.add(0, GitHubContent("..", "", "", 0, "", "", "", null, "dir"))
                }
                adapter.submitList(items)
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
                if (pathStack.isNotEmpty()) {
                    currentPath = pathStack.removeAt(pathStack.size - 1)
                }
            }
        }
    }

    private fun downloadFeed(url: String) {
        val repository = (requireActivity().application as OfflineBrowserApp).suggestedFeedRepository
        lifecycleScope.launch {
            try {
                Toast.makeText(context, "Downloading feeds...", Toast.LENGTH_SHORT).show()
                repository.downloadAndImportFeeds(url)
                Toast.makeText(context, "Feeds imported successfully!", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Error downloading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class BrowserAdapter(private val onItemClick: (GitHubContent) -> Unit) :
        RecyclerView.Adapter<BrowserAdapter.ViewHolder>() {

        private var items = listOf<GitHubContent>()

        fun submitList(newItems: List<GitHubContent>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Using android.R.layout.simple_list_item_1 for simplicity
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = if (item.type == "dir") "📁 ${item.name}" else "📄 ${item.name}"
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(android.R.id.text1)
        }
    }
}
