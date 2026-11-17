package com.example.offlinebrowser.data.repository

import android.content.Context
import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.local.FeedDao
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.data.network.HtmlDownloadResult
import com.example.offlinebrowser.data.network.HtmlDownloader
import com.example.offlinebrowser.data.network.ImageDownloader
import com.example.offlinebrowser.data.network.ParsedFeedItem
import com.example.offlinebrowser.data.network.RssParser
import com.example.offlinebrowser.data.network.ScraperEngine
import com.example.offlinebrowser.util.ArticleClassifier
import com.example.offlinebrowser.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
    private val imageDownloader = ImageDownloader(context)

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

    suspend fun syncFeed(feed: Feed) = withContext(Dispatchers.IO) {
        // Ensure plugins are loaded before any scraping happens
        scraperPluginRepository.ensureDefaultPlugins()
        val recipes = scraperPluginRepository.loadAllRecipes()
        downloader.scraperEngine.loadRecipes(recipes)

        val enforceFilters = preferencesRepository.enforcePluginFilters

        if (feed.type == FeedType.RSS || feed.type == FeedType.MASTODON) {
            if (preferencesRepository.detailedDebuggingEnabled) {
                fileLogger.log("Starting sync for feed: ${feed.url}")
            }
            val parsedItems = parser.fetchFeedItems(feed)

            for (item in parsedItems) {
                ingestParsedItem(
                    feed = feed,
                    item = item,
                    articleDao = articleDao,
                    scraperEngine = downloader.scraperEngine,
                    enforceFilters = enforceFilters,
                    downloadThumbnail = { url -> imageDownloader.downloadImage(url) }
                )
            }

            // Apply limits
            applyLimits(feed)

            // Auto-download articles if limit > 0
            if (feed.downloadLimit > 0) {
                val articlesToDownload = articleDao.getTopUncachedArticles(feed.id, feed.downloadLimit)
                for (article in articlesToDownload) {
                    var imageUrl = article.imageUrl
                    var localImagePath = article.localImagePath

                    if (imageUrl != null && localImagePath == null) {
                        localImagePath = imageDownloader.downloadImage(imageUrl)
                    }

                    val imagePathForInjection = if (localImagePath != null) "file://$localImagePath" else imageUrl
                    val downloadResult = downloader.downloadHtmlWithMetadata(
                        url = article.url,
                        rssImageUrl = imagePathForInjection,
                        enforceFilters = enforceFilters
                    )

                    if (downloadResult != null) {
                        applyAutoDownloadResult(
                            article = article,
                            downloadResult = downloadResult,
                            initialImageUrl = imageUrl,
                            initialLocalImagePath = localImagePath,
                            articleDao = articleDao,
                            scraperEngine = downloader.scraperEngine,
                            enforceFilters = enforceFilters,
                            downloadThumbnail = { url -> imageDownloader.downloadImage(url) }
                        )
                    }
                }
            }
        } else if (feed.type == FeedType.HTML) {
            val recipe = downloader.scraperEngine.findRecipeForUrl(feed.url)
            val stage1 = ArticleClassifier.evaluateRss(
                url = feed.url,
                title = feed.title,
                author = null,
                contentHtml = "",
                rssCategories = emptyList(),
                mediaCount = 0,
                recipe = recipe,
                feedCategory = feed.category,
                enforceFilters = enforceFilters
            )
            val article = Article(
                feedId = feed.id,
                title = feed.url,
                url = feed.url,
                content = "",
                publishedDate = System.currentTimeMillis(),
                imageUrl = null,
                category = stage1.category,
                subcategory = stage1.subcategory,
                isHidden = stage1.shouldHide
            )

            val existing = articleDao.getArticleByUrl(feed.id, article.url)
            if (existing == null) {
                val downloadResult = downloader.downloadHtmlWithMetadata(feed.url, null, enforceFilters)
                val content = downloadResult?.html
                if (content != null) {
                    val imageUrl = downloader.scraperEngine.extractImage(content)
                    var localImagePath: String? = null
                    if (imageUrl != null) {
                        localImagePath = imageDownloader.downloadImage(imageUrl)
                    }

                    val downloadedArticle = article.copy(
                        content = content,
                        isCached = true,
                        imageUrl = imageUrl,
                        localImagePath = localImagePath,
                        category = downloadResult.scrapeResult?.category ?: article.category,
                        subcategory = downloadResult.scrapeResult?.subcategory ?: article.subcategory
                    )
                    articleDao.insertArticle(downloadedArticle)
                } else {
                    articleDao.insertArticle(article)
                }
            } else {
                var localImagePath = existing.localImagePath
                if (localImagePath == null && existing.imageUrl != null) {
                    localImagePath = imageDownloader.downloadImage(existing.imageUrl)
                }

                val imagePathForInjection = if (localImagePath != null) "file://$localImagePath" else existing.imageUrl
                val downloadResult = downloader.downloadHtmlWithMetadata(feed.url, imagePathForInjection, enforceFilters)
                val content = downloadResult?.html

                if (content != null) {
                    val effectiveCategory = if (existing.isManuallyOverridden) {
                        existing.category
                    } else {
                        downloadResult.scrapeResult?.category ?: stage1.category ?: existing.category
                    }
                    val updated = existing.copy(
                        content = content,
                        isCached = true,
                        publishedDate = System.currentTimeMillis(),
                        localImagePath = localImagePath,
                        category = effectiveCategory,
                        subcategory = downloadResult.scrapeResult?.subcategory ?: existing.subcategory
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

    companion object {
        internal suspend fun ingestParsedItem(
            feed: Feed,
            item: ParsedFeedItem,
            articleDao: ArticleDao,
            scraperEngine: ScraperEngine,
            enforceFilters: Boolean,
            downloadThumbnail: suspend (String) -> String? = { null }
        ) {
            val article = item.article
            val recipe = scraperEngine.findRecipeForUrl(article.url)

            val stage1 = ArticleClassifier.evaluateRss(
                url = article.url,
                title = article.title,
                author = item.author,
                contentHtml = item.rawContentHtml,
                rssCategories = item.categories,
                mediaCount = item.mediaCount,
                recipe = recipe,
                feedCategory = feed.category,
                enforceFilters = enforceFilters
            )

            val cleanedSnippet = ArticleClassifier.applySummaryRules(article.content, recipe)
            val tagsCsv = stage1.tags.takeIf { it.isNotEmpty() }?.joinToString(",")

            val existing = articleDao.getArticleByUrl(feed.id, article.url)
            var localImagePath: String? = null

            if (existing != null) {
                localImagePath = existing.localImagePath
                val effectiveImageUrl = existing.imageUrl ?: article.imageUrl

                val keepStage2Category = existing.isCached &&
                    !recipe?.classification?.pageRules.isNullOrEmpty() &&
                    !existing.category.isNullOrBlank()

                val effectiveCategory = when {
                    existing.isManuallyOverridden -> existing.category
                    keepStage2Category -> existing.category
                    else -> stage1.category
                }
                val effectiveSubcategory = when {
                    existing.isManuallyOverridden -> existing.subcategory
                    keepStage2Category -> existing.subcategory
                    else -> stage1.subcategory
                }
                val effectiveIsHidden = when {
                    existing.isManuallyOverridden -> existing.isHidden
                    keepStage2Category -> existing.isHidden
                    else -> stage1.shouldHide
                }

                if (!effectiveIsHidden && localImagePath == null && effectiveImageUrl != null) {
                    localImagePath = downloadThumbnail(effectiveImageUrl)
                }

                val updated = article.copy(
                    id = existing.id,
                    content = if (existing.isCached) existing.content else cleanedSnippet,
                    isCached = existing.isCached,
                    localPath = existing.localPath,
                    isFavorite = existing.isFavorite,
                    isRead = existing.isRead,
                    imageUrl = effectiveImageUrl,
                    localImagePath = localImagePath,
                    summary = existing.summary,
                    category = effectiveCategory,
                    subcategory = effectiveSubcategory,
                    tags = tagsCsv ?: existing.tags,
                    isHidden = effectiveIsHidden,
                    isManuallyOverridden = existing.isManuallyOverridden
                )
                articleDao.insertArticle(updated)
            } else {
                if (!stage1.shouldHide && article.imageUrl != null) {
                    localImagePath = downloadThumbnail(article.imageUrl)
                }
                articleDao.insertArticle(
                    article.copy(
                        content = cleanedSnippet,
                        localImagePath = localImagePath,
                        category = stage1.category,
                        subcategory = stage1.subcategory,
                        tags = tagsCsv,
                        isHidden = stage1.shouldHide,
                        isManuallyOverridden = false
                    )
                )
            }
        }

        internal suspend fun applyAutoDownloadResult(
            article: Article,
            downloadResult: HtmlDownloadResult,
            initialImageUrl: String?,
            initialLocalImagePath: String?,
            articleDao: ArticleDao,
            scraperEngine: ScraperEngine,
            enforceFilters: Boolean,
            downloadThumbnail: suspend (String) -> String? = { null }
        ) {
            val scrape = downloadResult.scrapeResult
            val resolvedCategory = if (article.isManuallyOverridden) {
                article.category
            } else {
                scrape?.category ?: article.category
            }
            val resolvedSubcategory = scrape?.subcategory ?: article.subcategory

            val existingTags = article.tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val mergedTags = (existingTags + (scrape?.tags ?: emptyList())).distinct()

            val shouldHideFromStage2 = enforceFilters &&
                !article.isManuallyOverridden &&
                scrape?.category != null &&
                scrape.recipe?.classification != null &&
                ArticleClassifier.shouldDrop(
                    category = resolvedCategory,
                    tags = mergedTags,
                    config = scrape.recipe.classification
                )

            if (shouldHideFromStage2) {
                articleDao.updateArticle(
                    article.copy(
                        category = resolvedCategory,
                        subcategory = resolvedSubcategory,
                        isHidden = true
                    )
                )
            } else if (downloadResult.html != null) {
                val content = downloadResult.html
                var imageUrl = initialImageUrl
                var localImagePath = initialLocalImagePath
                if (imageUrl == null) {
                    imageUrl = scraperEngine.extractImage(content)
                    if (imageUrl != null) {
                        localImagePath = downloadThumbnail(imageUrl)
                    }
                }

                val downloaded = article.copy(
                    content = content,
                    isCached = true,
                    imageUrl = imageUrl,
                    localImagePath = localImagePath,
                    category = resolvedCategory,
                    subcategory = resolvedSubcategory
                )
                articleDao.updateArticle(downloaded)
            }
        }
    }
}
