package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.Feed
import java.net.URI

class FeedAdapter(
    private val onFeedClick: (Feed) -> Unit,
    private val onSyncClick: (Feed) -> Unit,
    private val onDeleteClick: (Feed) -> Unit,
    private val onEditClick: (Feed) -> Unit
) : ListAdapter<Feed, FeedAdapter.FeedViewHolder>(FeedDiffCallback()) {

    class FeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivFavicon: ImageView = itemView.findViewById(R.id.ivFavicon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        val btnSync: Button = itemView.findViewById(R.id.btnSync)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false)
        return FeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val feed = getItem(position)
        holder.tvTitle.text = feed.title
        holder.tvUrl.text = feed.url

        try {
            val uri = URI(feed.url)
            val domain = uri.host
            val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"

            Glide.with(holder.itemView.context)
                .load(faviconUrl)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(holder.ivFavicon)
        } catch (e: Exception) {
            holder.ivFavicon.setImageResource(R.mipmap.ic_launcher)
        }

        holder.itemView.setOnClickListener { onFeedClick(feed) }
        holder.btnSync.setOnClickListener { onSyncClick(feed) }
        holder.btnDelete.setOnClickListener { onDeleteClick(feed) }
        holder.btnEdit.setOnClickListener { onEditClick(feed) }
    }
}

class FeedDiffCallback : DiffUtil.ItemCallback<Feed>() {
    override fun areItemsTheSame(oldItem: Feed, newItem: Feed): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Feed, newItem: Feed): Boolean = oldItem == newItem
}
