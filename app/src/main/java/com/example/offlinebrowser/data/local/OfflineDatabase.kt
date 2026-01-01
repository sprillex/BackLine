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
import com.example.offlinebrowser.data.model.SuggestedFeed
import com.example.offlinebrowser.data.model.TrustedServer
import com.example.offlinebrowser.data.model.Weather

@Database(entities = [Feed::class, Article::class, Weather::class, TrustedServer::class, SuggestedFeed::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun weatherDao(): WeatherDao
    abstract fun trustedServerDao(): TrustedServerDao
    abstract fun suggestedFeedDao(): SuggestedFeedDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `trusted_servers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ip` TEXT NOT NULL, `port` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE feeds ADD COLUMN downloadLimit INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE feeds ADD COLUMN category TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE articles ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE articles ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `suggested_feeds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `language` TEXT NOT NULL, `rank` INTEGER NOT NULL, `url` TEXT NOT NULL, `contentType` TEXT NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE suggested_feeds ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "offline_browser_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
