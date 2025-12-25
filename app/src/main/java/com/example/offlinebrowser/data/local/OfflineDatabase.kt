package com.example.offlinebrowser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.TrustedServer
import com.example.offlinebrowser.data.model.Weather

@Database(entities = [Feed::class, Article::class, Weather::class, TrustedServer::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun weatherDao(): WeatherDao
    abstract fun trustedServerDao(): TrustedServerDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `trusted_servers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ip` TEXT NOT NULL, `port` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL)")
            }
        }

        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "offline_browser_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
