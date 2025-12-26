package com.example.offlinebrowser.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FeedType {
    RSS, MASTODON, HTML
}

@Entity(tableName = "feeds")
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val type: FeedType,
    val lastUpdated: Long = 0,
    val downloadLimit: Int = 0,
    val category: String? = null
)
