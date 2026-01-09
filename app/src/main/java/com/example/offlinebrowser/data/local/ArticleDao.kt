package com.example.offlinebrowser.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.ArticleListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl FROM articles WHERE feedId = :feedId ORDER BY isRead ASC, publishedDate DESC")
    fun getArticlesForFeed(feedId: Int): Flow<List<ArticleListItem>>

    @Query("SELECT articles.id, articles.feedId, articles.title, articles.url, articles.publishedDate, articles.isCached, articles.localPath, articles.isFavorite, articles.isRead, articles.imageUrl FROM articles INNER JOIN feeds ON articles.feedId = feeds.id WHERE feeds.category = :category ORDER BY articles.isRead ASC, articles.publishedDate DESC")
    fun getArticlesByCategory(category: String): Flow<List<ArticleListItem>>

    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl FROM articles ORDER BY isRead ASC, publishedDate DESC")
    fun getAllArticles(): Flow<List<ArticleListItem>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND url = :url LIMIT 1")
    suspend fun getArticleByUrl(feedId: Int, url: String): Article?

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Int): Article?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: Article)

    @Update
    suspend fun updateArticle(article: Article)

    @Delete
    suspend fun deleteArticle(article: Article)

    @Query("UPDATE articles SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateArticleFavoriteStatus(id: Int, isFavorite: Boolean)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun updateArticleReadStatus(id: Int, isRead: Boolean)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND publishedDate < :timestamp AND isFavorite = 0")
    suspend fun deleteOldArticles(feedId: Int, timestamp: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isFavorite = 0 AND id NOT IN (SELECT id FROM articles WHERE feedId = :feedId ORDER BY publishedDate DESC LIMIT :limit)")
    suspend fun deleteExcessArticles(feedId: Int, limit: Int)

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND isCached = 0 ORDER BY publishedDate DESC LIMIT :limit")
    suspend fun getTopUncachedArticles(feedId: Int, limit: Int): List<Article>
}
