package com.example.offlinebrowser.data.local

import androidx.room.TypeConverter
import com.example.offlinebrowser.data.model.FeedType

class Converters {
    @TypeConverter
    fun fromFeedType(value: FeedType): String {
        return value.name
    }

    @TypeConverter
    fun toFeedType(value: String): FeedType {
        return try {
            FeedType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            FeedType.RSS // Default fallback
        }
    }
}
