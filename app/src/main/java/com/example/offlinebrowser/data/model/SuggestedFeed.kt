package com.example.offlinebrowser.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggested_feeds")
data class SuggestedFeed @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,
    val language: String,
    val summary: String = "",
    val rank: Int,
    val url: String,
    val contentType: String
)
