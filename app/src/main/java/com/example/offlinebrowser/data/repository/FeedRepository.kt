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
import com.example.offlinebrowser.util.FileLogger

class FeedRepository(
    private val context: Context,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssParser: RssParser? = null,
    private val htmlDownloader: HtmlDownloader? = null
) {
    private val preferencesRepository = PreferencesRepository(context)
    private val fileLogger = FileLogger(context)
    private val scraperPluginRepository = ScraperPluginRepository(context)

    private val logCallback: (String) -> Unit = { message ->
        if (preferencesRepository.detailedDebuggingEnabled) {
            fileLogger.log(message)
        }
    }

    // Use passed parser or create one with logging if needed
    private val parser: RssParser by lazy {
        rssParser ?: RssParser(logCallback)
    }

    private val downloader: HtmlDownloader by lazy {
        htmlDownloader ?: HtmlDownloader(logCallback)
    }

    val allFeeds: Flow<List<Feed>> = feedDao.getAllFeeds()

    suspend fun addFeed(url: String, type: FeedType, downloadLimit: Int = 0, category: String? = null): Long {
        val feed = Feed(url = url, title = url, type = type, downloadLimit = downloadLimit, category = category)
        return feedDao.insertFeed(feed)
    }

    suspend fun updateFeed(feed: Feed) {
        feedDao.updateFeed(feed)
    }

    suspend fun getFeedById(id: Int): Feed? {
        return feedDao.getFeedById(id)
    }

    suspend fun deleteFeed(feed: Feed) {
        feedDao.deleteFeed(feed)
    }

    suspend fun syncFeed(feed: Feed) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Ensure plugins are loaded before any scraping happens
        scraperPluginRepository.ensureDefaultPlugins()
        val recipes = scraperPluginRepository.loadAllRecipes()
        downloader.scraperEngine.loadRecipes(recipes)

        if (feed.type == FeedType.RSS || feed.type == FeedType.MASTODON) {
            if (preferencesRepository.detailedDebuggingEnabled) {
                fileLogger.log("Starting sync for feed: ${feed.url}")
            }
            val articles = parser.fetchFeed(feed)

            for (article in articles) {
                 val existing = articleDao.getArticleByUrl(feed.id, article.url)
                 if (existing != null) {
                     // Update existing article but preserve cached content and status
                     val updated = article.copy(
                         id = existing.id,
                         content = if (existing.isCached) existing.content else article.content,
                         isCached = existing.isCached,
                         localPath = existing.localPath,
                         isFavorite = existing.isFavorite,
                         isRead = existing.isRead,
                         imageUrl = if (existing.imageUrl != null) existing.imageUrl else article.imageUrl
                     )
                     articleDao.insertArticle(updated)
                 } else {
                     articleDao.insertArticle(article)
                 }
            }

            // Apply limits
            applyLimits(feed)

            // Auto-download articles if limit > 0
            if (feed.downloadLimit > 0) {
                val articlesToDownload = articleDao.getTopUncachedArticles(feed.id, feed.downloadLimit)
                for (article in articlesToDownload) {
                    val content = downloader.downloadHtml(article.url)
                    if (content != null) {
                        // Extract image if missing
                        var imageUrl = article.imageUrl
                        if (imageUrl == null) {
                            imageUrl = downloader.scraperEngine.extractImage(content)
                        }
                        val downloaded = article.copy(content = content, isCached = true, imageUrl = imageUrl)
                        articleDao.updateArticle(downloaded)
                    }
                }
            }
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
                publishedDate = System.currentTimeMillis(),
                imageUrl = null
            )

            val existing = articleDao.getArticleByUrl(feed.id, article.url)
            if (existing == null) {
                // New article, download content immediately
                val content = downloader.downloadHtml(feed.url)
                if (content != null) {
                    val downloadedArticle = article.copy(content = content, isCached = true)
                    articleDao.insertArticle(downloadedArticle)
                } else {
                    // Failed to download, insert as non-cached
                    articleDao.insertArticle(article)
                }
            } else {
                 // Update content if needed? For simple HTML page feed, we assume we always want latest.
                 val content = downloader.downloadHtml(feed.url)
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
