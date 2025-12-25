package com.example.offlinebrowser.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_servers")
data class TrustedServer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ip: String,
    val port: Int,
    val fingerprint: String
)
