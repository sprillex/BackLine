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
import com.example.offlinebrowser.data.model.Article
import java.net.URI

class ArticleAdapter(
    private val onArticleClick: (Article) -> Unit,
    private val onDownloadClick: (Article) -> Unit,
    private val onArticleLongClick: (Article, View) -> Unit
) : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(ArticleDiffCallback()) {

    class ArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivFavicon: ImageView = itemView.findViewById(R.id.ivFavicon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val btnDownload: Button = itemView.findViewById(R.id.btnDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_article, parent, false)
        return ArticleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        val article = getItem(position)
        holder.tvTitle.text = article.title
        holder.tvStatus.text = if (article.isCached) "Cached" else "Online"

        try {
            val uri = URI(article.url)
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

        holder.itemView.setOnClickListener { onArticleClick(article) }
        holder.itemView.setOnLongClickListener {
            onArticleLongClick(article, it)
            true
        }
        holder.btnDownload.setOnClickListener { onDownloadClick(article) }

        holder.btnDownload.visibility = if (article.isCached) View.GONE else View.VISIBLE

        // Visual indicator for Read status
        if (article.isRead) {
            holder.tvTitle.setTextColor(android.graphics.Color.GRAY)
        } else {
            // Reset to default text color (assuming it's black/primary from theme)
            // Ideally we should get this from the theme attributes to support dark mode correctly
            val typedValue = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            if (typedValue.resourceId != 0) {
                 holder.tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, typedValue.resourceId))
            } else {
                 holder.tvTitle.setTextColor(typedValue.data)
            }
        }

        // Add visual indicator for Favorite if desired, e.g., append a star to title or use a separate icon
        // For now just appending "★" to status if favorite
        if (article.isFavorite) {
            holder.tvStatus.text = "${holder.tvStatus.text} ★"
        }
    }
}

class ArticleDiffCallback : DiffUtil.ItemCallback<Article>() {
    override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean = oldItem == newItem
}
