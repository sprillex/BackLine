package com.example.offlinebrowser.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather")
data class Weather(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val dataJson: String, // Store full JSON response to be parsed later
    val lastUpdated: Long
)
