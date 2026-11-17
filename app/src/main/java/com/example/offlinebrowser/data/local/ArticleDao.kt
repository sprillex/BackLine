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
    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl, localImagePath, category, subcategory, tags, isHidden, isManuallyOverridden FROM articles WHERE feedId = :feedId AND isHidden = 0 ORDER BY isRead ASC, publishedDate DESC")
    fun getArticlesForFeed(feedId: Int): Flow<List<ArticleListItem>>

    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl, localImagePath, category, subcategory, tags, isHidden, isManuallyOverridden FROM articles WHERE category = :category COLLATE NOCASE AND isHidden = 0 ORDER BY isRead ASC, publishedDate DESC")
    fun getArticlesByCategory(category: String): Flow<List<ArticleListItem>>

    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl, localImagePath, category, subcategory, tags, isHidden, isManuallyOverridden FROM articles WHERE isHidden = 0 ORDER BY isRead ASC, publishedDate DESC")
    fun getAllArticles(): Flow<List<ArticleListItem>>

    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl, localImagePath, category, subcategory, tags, isHidden, isManuallyOverridden FROM articles WHERE isHidden = 0 AND title LIKE :query ORDER BY isRead ASC, publishedDate DESC")
    fun searchArticles(query: String): Flow<List<ArticleListItem>>

    @Query("SELECT id, feedId, title, url, publishedDate, isCached, localPath, isFavorite, isRead, imageUrl, localImagePath, category, subcategory, tags, isHidden, isManuallyOverridden FROM articles WHERE isHidden = 1 ORDER BY publishedDate DESC")
    fun getHiddenArticles(): Flow<List<ArticleListItem>>

    @Query("SELECT DISTINCT category FROM articles WHERE isHidden = 0 AND category IS NOT NULL AND TRIM(category) != '' ORDER BY category ASC")
    fun getDistinctVisibleCategories(): Flow<List<String>>

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

    @Query("UPDATE articles SET summary = :summary WHERE id = :id")
    suspend fun updateArticleSummary(id: Int, summary: String)

    @Query("UPDATE articles SET isHidden = :isHidden, isManuallyOverridden = :isManuallyOverridden WHERE id = :id")
    suspend fun updateArticleHiddenStatus(id: Int, isHidden: Boolean, isManuallyOverridden: Boolean)

    @Query("UPDATE articles SET category = :category, isManuallyOverridden = :isManuallyOverridden WHERE id = :id")
    suspend fun updateArticleCategory(id: Int, category: String?, isManuallyOverridden: Boolean)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND publishedDate < :timestamp AND isFavorite = 0")
    suspend fun deleteOldArticles(feedId: Int, timestamp: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isFavorite = 0 AND id NOT IN (SELECT id FROM articles WHERE feedId = :feedId ORDER BY publishedDate DESC LIMIT :limit)")
    suspend fun deleteExcessArticles(feedId: Int, limit: Int)

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND isCached = 0 AND isHidden = 0 ORDER BY publishedDate DESC LIMIT :limit")
    suspend fun getTopUncachedArticles(feedId: Int, limit: Int): List<Article>
}
