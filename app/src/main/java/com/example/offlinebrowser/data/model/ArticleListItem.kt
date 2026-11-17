package com.example.offlinebrowser.data.model

import androidx.room.ColumnInfo

data class ArticleListItem(
    val id: Int,
    val feedId: Int,
    val title: String,
    val url: String,
    val publishedDate: Long,
    val isCached: Boolean,
    val localPath: String?,
    val isFavorite: Boolean,
    val isRead: Boolean,
    val imageUrl: String?,
    val localImagePath: String?,
    val category: String? = null,
    val subcategory: String? = null,
    val tags: String? = null,
    val isHidden: Boolean = false,
    val isManuallyOverridden: Boolean = false
)
