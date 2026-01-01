package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.SuggestedFeed

class SuggestedFeedAdapter(
    private val onAddClick: (SuggestedFeed) -> Unit
) : ListAdapter<SuggestedFeed, SuggestedFeedAdapter.ViewHolder>(SuggestedFeedDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggested_feed, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onAddClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        private val btnAdd: Button = itemView.findViewById(R.id.btnAdd)

        fun bind(feed: SuggestedFeed, onAddClick: (SuggestedFeed) -> Unit) {
            tvName.text = feed.name
            tvCategory.text = feed.category
            tvUrl.text = feed.url
            btnAdd.setOnClickListener {
                onAddClick(feed)
            }
        }
    }

    class SuggestedFeedDiffCallback : DiffUtil.ItemCallback<SuggestedFeed>() {
        override fun areItemsTheSame(oldItem: SuggestedFeed, newItem: SuggestedFeed): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SuggestedFeed, newItem: SuggestedFeed): Boolean {
            return oldItem == newItem
        }
    }
}
