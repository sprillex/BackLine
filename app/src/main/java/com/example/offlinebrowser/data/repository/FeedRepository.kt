package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.local.FeedDao
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.data.network.RssParser
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

import android.content.Context

import com.example.offlinebrowser.data.network.HtmlDownloader

class FeedRepository(
    private val context: Context,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssParser: RssParser,
    private val htmlDownloader: HtmlDownloader = HtmlDownloader()
) {
    private val preferencesRepository = PreferencesRepository(context)
    val allFeeds: Flow<List<Feed>> = feedDao.getAllFeeds()

    suspend fun addFeed(url: String, type: FeedType) {
        val feed = Feed(url = url, title = url, type = type) // Title will be updated on sync
        feedDao.insertFeed(feed)
    }

    suspend fun updateFeed(feed: Feed) {
        feedDao.updateFeed(feed)
    }

    suspend fun deleteFeed(feed: Feed) {
        feedDao.deleteFeed(feed)
    }

    suspend fun syncFeed(feed: Feed) {
        if (feed.type == FeedType.RSS || feed.type == FeedType.MASTODON) {
            val articles = rssParser.fetchFeed(feed)

            for (article in articles) {
                 val existing = articleDao.getArticleByUrl(feed.id, article.url)
                 if (existing != null) {
                     // Update existing article but preserve cached content and status
                     val updated = article.copy(
                         id = existing.id,
                         content = if (existing.isCached) existing.content else article.content,
                         isCached = existing.isCached
                     )
                     articleDao.insertArticle(updated)
                 } else {
                     articleDao.insertArticle(article)
                 }
            }

            // Apply limits
            applyLimits(feed)
        } else if (feed.type == FeedType.HTML) {
            // For HTML "feeds", we treat the URL as a single page article.
            // We create a single "Article" entry for this feed which is the page itself.
            // The content will be downloaded via HtmlDownloader later (or here).
            // Actually, let's just create the article entry so it appears in the list.
            val article = com.example.offlinebrowser.data.model.Article(
                feedId = feed.id,
                title = feed.url, // Default title
                url = feed.url,
                content = "", // Will be filled by ArticleRepository
                publishedDate = System.currentTimeMillis()
            )

            val existing = articleDao.getArticleByUrl(feed.id, article.url)
            if (existing == null) {
                // New article, download content immediately
                val content = htmlDownloader.downloadHtml(feed.url)
                if (content != null) {
                    val downloadedArticle = article.copy(content = content, isCached = true)
                    articleDao.insertArticle(downloadedArticle)
                } else {
                    // Failed to download, insert as non-cached
                    articleDao.insertArticle(article)
                }
            } else {
                 // Update content if needed? For simple HTML page feed, we assume we always want latest.
                 val content = htmlDownloader.downloadHtml(feed.url)
                 if (content != null) {
                     val updated = existing.copy(
                         content = content,
                         isCached = true,
                         publishedDate = System.currentTimeMillis() // Update timestamp to show it's fresh
                     )
                     articleDao.insertArticle(updated)
                 }
            }
        }
    }

    private suspend fun applyLimits(feed: Feed) {
        val limitCount = preferencesRepository.feedLimitCount
        val limitDays = preferencesRepository.feedLimitDays

        // Limit by count
        articleDao.deleteExcessArticles(feed.id, limitCount)

        // Limit by days
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(limitDays.toLong())
        articleDao.deleteOldArticles(feed.id, cutoff)
    }
}
