package com.example.offlinebrowser.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinebrowser.data.model.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedDate DESC")
    fun getArticlesForFeed(feedId: Int): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND url = :url LIMIT 1")
    suspend fun getArticleByUrl(feedId: Int, url: String): Article?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: Article)

    @Delete
    suspend fun deleteArticle(article: Article)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND publishedDate < :timestamp")
    suspend fun deleteOldArticles(feedId: Int, timestamp: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND id NOT IN (SELECT id FROM articles WHERE feedId = :feedId ORDER BY publishedDate DESC LIMIT :limit)")
    suspend fun deleteExcessArticles(feedId: Int, limit: Int)
}
