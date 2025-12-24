package com.example.offlinebrowser.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [ForeignKey(
        entity = Feed::class,
        parentColumns = ["id"],
        childColumns = ["feedId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["feedId", "url"], unique = true)]
)
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val feedId: Int,
    val title: String,
    val url: String,
    val content: String, // HTML or text content
    val publishedDate: Long,
    val isCached: Boolean = false,
    val localPath: String? = null // Path to local file if stored as file
)
