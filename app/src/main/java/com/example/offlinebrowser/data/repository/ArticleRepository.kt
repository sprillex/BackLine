package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.ArticleListItem
import com.example.offlinebrowser.data.network.HtmlDownloader
import kotlinx.coroutines.flow.Flow

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val htmlDownloader: HtmlDownloader
) {
    fun getArticlesForFeed(feedId: Int): Flow<List<ArticleListItem>> = articleDao.getArticlesForFeed(feedId)

    fun getArticlesByCategory(category: String): Flow<List<ArticleListItem>> = articleDao.getArticlesByCategory(category)

    fun getAllArticles(): Flow<List<ArticleListItem>> = articleDao.getAllArticles()

    suspend fun downloadArticleContent(articleItem: ArticleListItem) {
        if (articleItem.isCached) return

        val content = htmlDownloader.downloadHtml(articleItem.url)
        if (content != null) {
            // We need to fetch the full article to update it, or construct it if we are sure
            val existingArticle = articleDao.getArticleById(articleItem.id)
            if (existingArticle != null) {
                val updatedArticle = existingArticle.copy(content = content, isCached = true)
                articleDao.insertArticle(updatedArticle)
            }
        }
    }

    suspend fun downloadArticleContent(article: Article) {
        if (article.isCached) return

        val content = htmlDownloader.downloadHtml(article.url)
        if (content != null) {
            val updatedArticle = article.copy(content = content, isCached = true)
            articleDao.insertArticle(updatedArticle)
        }
    }

    suspend fun updateArticleFavoriteStatus(id: Int, isFavorite: Boolean) {
        articleDao.updateArticleFavoriteStatus(id, isFavorite)
    }

    suspend fun updateArticleReadStatus(id: Int, isRead: Boolean) {
        articleDao.updateArticleReadStatus(id, isRead)
    }
}
