package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.network.HtmlDownloader
import kotlinx.coroutines.flow.Flow

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val htmlDownloader: HtmlDownloader
) {
    fun getArticlesForFeed(feedId: Int): Flow<List<Article>> = articleDao.getArticlesForFeed(feedId)

    suspend fun downloadArticleContent(article: Article) {
        if (article.isCached) return

        val content = htmlDownloader.downloadHtml(article.url)
        if (content != null) {
            val updatedArticle = article.copy(content = content, isCached = true)
            articleDao.insertArticle(updatedArticle)
        }
    }
}
