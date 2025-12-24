package com.example.offlinebrowser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.Weather

@Database(entities = [Feed::class, Article::class, Weather::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun weatherDao(): WeatherDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "offline_browser_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
