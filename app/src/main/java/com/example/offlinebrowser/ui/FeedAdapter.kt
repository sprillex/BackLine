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
import com.example.offlinebrowser.data.model.Feed

class FeedAdapter(
    private val onFeedClick: (Feed) -> Unit,
    private val onSyncClick: (Feed) -> Unit,
    private val onDeleteClick: (Feed) -> Unit
) : ListAdapter<Feed, FeedAdapter.FeedViewHolder>(FeedDiffCallback()) {

    class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        val btnSync: Button = itemView.findViewById(R.id.btnSync)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val feed = getItem(position)
        holder.tvTitle.text = feed.title
        holder.tvUrl.text = feed.url
        holder.itemView.setOnClickListener { onFeedClick(feed) }
        holder.btnSync.setOnClickListener { onSyncClick(feed) }
        holder.btnDelete.setOnClickListener { onDeleteClick(feed) }
    }
}

class FeedDiffCallback : DiffUtil.ItemCallback<Feed>() {
    override fun areItemsTheSame(oldItem: Feed, newItem: Feed): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Feed, newItem: Feed): Boolean = oldItem == newItem
}
