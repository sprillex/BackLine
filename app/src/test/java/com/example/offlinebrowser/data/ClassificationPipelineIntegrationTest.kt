package com.example.offlinebrowser.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.model.Article
import com.example.offlinebrowser.data.model.ArticleListItem
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.model.SettingsConfig
import com.example.offlinebrowser.data.network.HtmlDownloadResult
import com.example.offlinebrowser.data.network.RssParser
import com.example.offlinebrowser.data.network.ScraperEngine
import com.example.offlinebrowser.data.repository.FeedRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.Proxy

class ClassificationPipelineIntegrationTest {

    private val gson = Gson()

    private fun loadPluginRecipe(relativePath: String): ScraperRecipe {
        val candidates = listOf(File(relativePath), File("../$relativePath"))
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Missing plugin file: $relativePath")
        return gson.fromJson(file.readText(), ScraperRecipe::class.java)
    }

    private class FakeArticleDao : ArticleDao {
        private var nextId = 1
        private val articles = MutableStateFlow<List<Article>>(emptyList())

        private fun Article.toListItem() = ArticleListItem(
            id = id,
            feedId = feedId,
            title = title,
            url = url,
            publishedDate = publishedDate,
            isCached = isCached,
            localPath = localPath,
            isFavorite = isFavorite,
            isRead = isRead,
            imageUrl = imageUrl,
            localImagePath = localImagePath,
            category = category,
            subcategory = subcategory,
            tags = tags,
            isHidden = isHidden,
            isManuallyOverridden = isManuallyOverridden
        )

        override fun getArticlesForFeed(feedId: Int): Flow<List<ArticleListItem>> =
            articles.map { list -> list.filter { it.feedId == feedId && !it.isHidden }.map { it.toListItem() } }

        override fun getArticlesByCategory(category: String): Flow<List<ArticleListItem>> =
            articles.map { list ->
                list.filter { !it.isHidden && it.category.equals(category, ignoreCase = true) }.map { it.toListItem() }
            }

        override fun getAllArticles(): Flow<List<ArticleListItem>> =
            articles.map { list -> list.filter { !it.isHidden }.map { it.toListItem() } }

        override fun searchArticles(query: String): Flow<List<ArticleListItem>> {
            val clean = query.trim('%')
            return articles.map { list ->
                list.filter { !it.isHidden && it.title.contains(clean, ignoreCase = true) }.map { it.toListItem() }
            }
        }

        override fun getHiddenArticles(): Flow<List<ArticleListItem>> =
            articles.map { list -> list.filter { it.isHidden }.map { it.toListItem() } }

        override fun getDistinctVisibleCategories(): Flow<List<String>> =
            articles.map { list ->
                list.filter { !it.isHidden && !it.category.isNullOrBlank() }
                    .map { it.category!! }
                    .distinct()
            }

        override suspend fun getArticleByUrl(feedId: Int, url: String): Article? =
            articles.value.firstOrNull { it.feedId == feedId && it.url == url }

        override suspend fun getArticleById(id: Int): Article? =
            articles.value.firstOrNull { it.id == id }

        override suspend fun insertArticle(article: Article) {
            val current = articles.value.toMutableList()
            val assigned = if (article.id == 0) article.copy(id = nextId++) else article
            val idx = current.indexOfFirst { it.id == assigned.id || (it.feedId == assigned.feedId && it.url == assigned.url) }
            if (idx >= 0) {
                current[idx] = assigned.copy(id = current[idx].id)
            } else {
                current.add(assigned)
            }
            articles.value = current
        }

        override suspend fun updateArticle(article: Article) {
            val current = articles.value.toMutableList()
            val idx = current.indexOfFirst { it.id == article.id }
            if (idx >= 0) {
                current[idx] = article
                articles.value = current
            }
        }

        override suspend fun deleteArticle(article: Article) {
            articles.value = articles.value.filterNot { it.id == article.id }
        }

        override suspend fun updateArticleFavoriteStatus(id: Int, isFavorite: Boolean) {
            articles.value = articles.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
        }

        override suspend fun updateArticleReadStatus(id: Int, isRead: Boolean) {
            articles.value = articles.value.map { if (it.id == id) it.copy(isRead = isRead) else it }
        }

        override suspend fun updateArticleSummary(id: Int, summary: String) {
            articles.value = articles.value.map { if (it.id == id) it.copy(summary = summary) else it }
        }

        override suspend fun updateArticleHiddenStatus(id: Int, isHidden: Boolean, isManuallyOverridden: Boolean) {
            articles.value = articles.value.map {
                if (it.id == id) it.copy(isHidden = isHidden, isManuallyOverridden = isManuallyOverridden) else it
            }
        }

        override suspend fun updateArticleCategory(id: Int, category: String?, isManuallyOverridden: Boolean) {
            articles.value = articles.value.map {
                if (it.id == id) it.copy(category = category, isManuallyOverridden = isManuallyOverridden) else it
            }
        }

        override suspend fun deleteOldArticles(feedId: Int, timestamp: Long) = Unit

        override suspend fun deleteExcessArticles(feedId: Int, limit: Int) = Unit

        override suspend fun getTopUncachedArticles(feedId: Int, limit: Int): List<Article> =
            articles.value
                .filter { it.feedId == feedId && !it.isCached && !it.isHidden }
                .sortedByDescending { it.publishedDate }
                .take(limit)
    }

    @Test
    fun testRssParserExtractsCategoriesAuthorHtmlAndMediaCount() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Test Feed</title>
                <link>https://www.toledoblade.com/</link>
                <description>Test</description>
                <item>
                  <title>Photo Gallery: Downtown</title>
                  <link>https://www.toledoblade.com/local/2026/11/17/gallery/stories/1</link>
                  <author>posteditor</author>
                  <category>Local</category>
                  <category>Photos</category>
                  <description><![CDATA[<p>Lead teaser</p>]]></description>
                  <content:encoded><![CDATA[<p>Full HTML body with pc_aff_link=1</p>]]></content:encoded>
                  <media:content url="https://example.com/1.jpg" medium="image"/>
                  <media:content url="https://example.com/2.jpg" medium="image"/>
                  <media:content url="https://example.com/3.jpg" medium="image"/>
                  <media:content url="https://example.com/4.jpg" medium="image"/>
                  <media:content url="https://example.com/5.jpg" medium="image"/>
                  <media:content url="https://example.com/6.jpg" medium="image"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = Feed(id = 1, url = "https://www.toledoblade.com/rss", title = "Blade", type = FeedType.RSS)
        val items = RssParser().parseStream(feed, ByteArrayInputStream(rssXml.toByteArray()))

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(listOf("Local", "Photos"), item.categories)
        assertEquals("posteditor", item.author)
        assertTrue(item.rawContentHtml.contains("pc_aff_link=1"))
        assertEquals(6, item.mediaCount)
    }

    @Test
    fun testSoftHideExclusionFromAutoDownloadAndManualOverridePreservation() = runBlocking {
        val mlive = loadPluginRecipe("plugins/en/News/mlive.json")
        val oscoda = loadPluginRecipe("plugins/en/News/oscodaherald.json")
        val toledo = loadPluginRecipe("plugins/en/News/toledoblade.json")

        val engine = ScraperEngine().apply { loadRecipes(listOf(mlive, oscoda, toledo)) }
        val dao = FakeArticleDao()

        val mliveFeed = Feed(id = 1, url = "https://www.mlive.com/rss", title = "MLive", type = FeedType.RSS, downloadLimit = 10, category = "Michigan")
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>MLive</title>
                <link>https://www.mlive.com/</link>
                <description>News</description>
                <item>
                  <title>State Legislature Passes Bill</title>
                  <link>https://www.mlive.com/politics/2026/11/bill.html</link>
                  <description><![CDATA[<p>Summary</p>]]></description>
                </item>
                <item>
                  <title>Lions Win Thriller</title>
                  <link>https://www.mlive.com/lions/2026/11/win.html</link>
                  <description><![CDATA[<p>Game recap</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = RssParser().parseStream(mliveFeed, ByteArrayInputStream(rssXml.toByteArray()))
        for (item in parsed) {
            FeedRepository.ingestParsedItem(mliveFeed, item, dao, engine, enforceFilters = true)
        }

        val newsArticle = dao.getArticleByUrl(1, "https://www.mlive.com/politics/2026/11/bill.html")!!
        val lionsArticle = dao.getArticleByUrl(1, "https://www.mlive.com/lions/2026/11/win.html")!!

        assertEquals("News", newsArticle.category)
        assertFalse(newsArticle.isHidden)

        assertEquals("Sports", lionsArticle.category)
        assertTrue(lionsArticle.isHidden)

        // Soft-hidden article MUST NOT be returned by getTopUncachedArticles
        val uncached = dao.getTopUncachedArticles(1, 10)
        assertEquals(1, uncached.size)
        assertEquals(newsArticle.id, uncached[0].id)

        // Manual override: user unhides Lions article and assigns custom category "Favorites"
        dao.updateArticleHiddenStatus(lionsArticle.id, isHidden = false, isManuallyOverridden = true)
        dao.updateArticleCategory(lionsArticle.id, category = "Favorites", isManuallyOverridden = true)

        // Re-sync the same feed: manual overrides MUST be preserved
        for (item in parsed) {
            FeedRepository.ingestParsedItem(mliveFeed, item, dao, engine, enforceFilters = true)
        }
        val afterResync = dao.getArticleByUrl(1, "https://www.mlive.com/lions/2026/11/win.html")!!
        assertFalse(afterResync.isHidden)
        assertEquals("Favorites", afterResync.category)
        assertTrue(afterResync.isManuallyOverridden)
    }

    @Test
    fun testStage2AutoDownloadSoftHidesWithoutCachingFullHtml() = runBlocking {
        val toledo = loadPluginRecipe("plugins/en/News/toledoblade.json")
        val engine = ScraperEngine().apply { loadRecipes(listOf(toledo)) }
        val dao = FakeArticleDao()

        val feed = Feed(id = 2, url = "https://www.toledoblade.com/rss", title = "Blade", type = FeedType.RSS, downloadLimit = 5, category = "News")
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Blade</title>
                <link>https://www.toledoblade.com/</link>
                <description>News</description>
                <item>
                  <title>Team Preview</title>
                  <link>https://www.toledoblade.com/team-preview</link>
                  <description><![CDATA[<p>Short RSS teaser</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val item = RssParser().parseStream(feed, ByteArrayInputStream(rssXml.toByteArray())).single()
        FeedRepository.ingestParsedItem(feed, item, dao, engine, enforceFilters = true)

        val queued = dao.getTopUncachedArticles(2, 5).single()
        assertEquals("News", queued.category)
        assertFalse(queued.isHidden)

        val pageHtml = """
            <html><head><script>
            var pgStoryZeroJSON = {
              "articles": [{
                "title": "Team Preview",
                "section": "sports",
                "subsection": "hockey",
                "body": "<p>Full sports story body that should not be cached.</p>"
              }]
            };
            </script></head><body></body></html>
        """.trimIndent()

        val scrapeResult = engine.processWithMetadata(queued.url, pageHtml, null, enforceFilters = true)
        FeedRepository.applyAutoDownloadResult(
            article = queued,
            downloadResult = HtmlDownloadResult(scrapeResult?.html, queued.url, scrapeResult),
            initialImageUrl = queued.imageUrl,
            initialLocalImagePath = queued.localImagePath,
            articleDao = dao,
            scraperEngine = engine,
            enforceFilters = true
        )

        val updated = dao.getArticleById(queued.id)!!
        assertTrue(updated.isHidden)
        assertEquals("Sports", updated.category)
        assertEquals("hockey", updated.subcategory)
        assertFalse(updated.isCached)
        assertEquals("<p>Short RSS teaser</p>", updated.content)
    }

    @Test
    fun testMigration10To11ExecutesAllAlterTableStatements() {
        val migration = OfflineDatabase.MIGRATION_10_11
        assertEquals(10, migration.startVersion)
        assertEquals(11, migration.endVersion)

        val executedSql = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        migration.migrate(fakeDb)

        assertEquals(
            listOf(
                "ALTER TABLE articles ADD COLUMN category TEXT",
                "ALTER TABLE articles ADD COLUMN subcategory TEXT",
                "ALTER TABLE articles ADD COLUMN tags TEXT",
                "ALTER TABLE articles ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE articles ADD COLUMN isManuallyOverridden INTEGER NOT NULL DEFAULT 0"
            ),
            executedSql
        )
    }

    @Test
    fun testArticleDaoQueryDefinitions() {
        val candidates = listOf(
            File("src/main/java/com/example/offlinebrowser/data/local/ArticleDao.kt"),
            File("app/src/main/java/com/example/offlinebrowser/data/local/ArticleDao.kt")
        )
        val daoSource = candidates.firstOrNull { it.exists() }?.readText()
        assertNotNull("Could not find ArticleDao.kt", daoSource)
        val src = daoSource!!

        fun queryAbove(methodName: String): String {
            val idx = src.indexOf("fun $methodName(")
            assertTrue("Missing method $methodName in ArticleDao.kt", idx > 0)
            val prefix = src.substring(0, idx)
            val queryStart = prefix.lastIndexOf("@Query(")
            assertTrue("Missing @Query on $methodName", queryStart >= 0)
            return prefix.substring(queryStart)
        }

        assertTrue(queryAbove("getAllArticles").contains("isHidden = 0"))
        assertTrue(queryAbove("getArticlesForFeed").contains("isHidden = 0"))
        assertTrue(queryAbove("searchArticles").contains("isHidden = 0"))

        val byCat = queryAbove("getArticlesByCategory")
        assertTrue(byCat.contains("category = :category COLLATE NOCASE"))
        assertTrue(byCat.contains("isHidden = 0"))
        assertFalse(byCat.contains("INNER JOIN feeds"))

        assertTrue(queryAbove("getHiddenArticles").contains("isHidden = 1"))
        assertTrue(queryAbove("getDistinctVisibleCategories").contains("isHidden = 0"))
        assertTrue(queryAbove("getTopUncachedArticles").contains("isHidden = 0"))
        assertTrue(queryAbove("updateArticleHiddenStatus").contains("isManuallyOverridden"))
        assertTrue(queryAbove("updateArticleCategory").contains("isManuallyOverridden"))
    }

    @Test
    fun testSettingsConfigBackwardsCompatibleDefaults() {
        val legacyJson = """
            {
              "wifi_only": true,
              "refresh_interval": 30,
              "feed_limit_count": 25,
              "feed_limit_days": 14,
              "weather_units": "imperial"
            }
        """.trimIndent()
        val parsed = gson.fromJson(legacyJson, SettingsConfig::class.java)
        assertEquals(false, parsed.showHiddenArticles ?: false)
        assertEquals(true, parsed.enforcePluginFilters ?: true)
    }

    @Test
    fun testOscodaPaywallStage2HidesPersistsTagAndSkipsCachingStub() = runBlocking {
        val oscoda = loadPluginRecipe("plugins/en/News/oscodaherald.json")
        val engine = ScraperEngine().apply { loadRecipes(listOf(oscoda)) }
        val dao = FakeArticleDao()

        val feed = Feed(id = 3, url = "https://www.oscodaherald.com/feed", title = "Oscoda Herald", type = FeedType.RSS, downloadLimit = 5)
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Oscoda Herald</title>
                <link>https://www.oscodaherald.com/</link>
                <description>Local</description>
                <item>
                  <title>Road Commission Approves Plan</title>
                  <link>https://www.oscodaherald.com/road-plan</link>
                  <category>News</category>
                  <description><![CDATA[<p>Short teaser before paywall.</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val item = RssParser().parseStream(feed, ByteArrayInputStream(rssXml.toByteArray())).single()
        FeedRepository.ingestParsedItem(feed, item, dao, engine, enforceFilters = true)

        val queued = dao.getTopUncachedArticles(3, 5).single()
        assertFalse(queued.isHidden)
        assertFalse(queued.isCached)

        val paywalledPage = """
            <html><body>
                <h1 class="entry-title">Road Commission Approves Plan</h1>
                <div class="entry-content">
                    <p>Short teaser before paywall.</p>
                    <p>Login to continue reading</p>
                </div>
                <div id="checkout_form_wrapper" class="cherryroad-media-paywall">Subscribe now</div>
            </body></html>
        """.trimIndent()

        val scrapeResult = engine.processWithMetadata(queued.url, paywalledPage, null, enforceFilters = true)
        FeedRepository.applyAutoDownloadResult(
            article = queued,
            downloadResult = HtmlDownloadResult(scrapeResult?.html, queued.url, scrapeResult),
            initialImageUrl = queued.imageUrl,
            initialLocalImagePath = queued.localImagePath,
            articleDao = dao,
            scraperEngine = engine,
            enforceFilters = true
        )

        val updated = dao.getArticleById(queued.id)!!
        assertTrue(updated.isHidden)
        assertEquals("paywalled", updated.tags)
        assertFalse(updated.isCached)
        assertEquals("<p>Short teaser before paywall.</p>", updated.content)

        // Subsequent RSS sync must keep Stage 2 paywalled hide status & tag even though isCached is false
        FeedRepository.ingestParsedItem(feed, item, dao, engine, enforceFilters = true)
        val afterResync = dao.getArticleById(queued.id)!!
        assertTrue(afterResync.isHidden)
        assertTrue(afterResync.tags?.contains("paywalled") == true)
    }

    @Test
    fun testRssContentEncodedExtractionAndHttp403FallbackCachesSubstantiveBody() = runBlocking {
        val mlive = loadPluginRecipe("plugins/en/News/mlive.json")
        val engine = ScraperEngine().apply { loadRecipes(listOf(mlive)) }
        val dao = FakeArticleDao()

        val longParagraph = "Michigan lawmakers advanced a comprehensive infrastructure funding bill after months of bipartisan committee hearings in Lansing, outlining bridge repairs and rural road maintenance across northern counties. ".repeat(4)
        assertTrue(longParagraph.length >= 500)

        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>MLive</title>
                <link>https://www.mlive.com/</link>
                <description>News</description>
                <item>
                  <title>Infrastructure Bill Advances</title>
                  <link>https://www.mlive.com/politics/2026/11/infra-bill.html</link>
                  <description><![CDATA[<p>Short teaser only.</p>]]></description>
                  <content:encoded><![CDATA[<p>$longParagraph</p>]]></content:encoded>
                </item>
                <item>
                  <title>Brief Notice</title>
                  <link>https://www.mlive.com/news/2026/11/brief.html</link>
                  <description><![CDATA[<p>Short notice without encoded body.</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = Feed(id = 4, url = "https://www.mlive.com/arc/outboundfeeds/rss/", title = "MLive", type = FeedType.RSS, downloadLimit = 5)
        val parsed = RssParser().parseStream(feed, ByteArrayInputStream(rssXml.toByteArray()))
        assertEquals(2, parsed.size)

        val longItem = parsed[0]
        val shortItem = parsed[1]

        // RssParser must prefer <content:encoded> over <description> when available, and fall back to <description>
        assertTrue(longItem.article.content.contains("bipartisan committee hearings"))
        assertEquals("<p>Short notice without encoded body.</p>", shortItem.article.content)

        // Ingest marks substantive <content:encoded> item as cached immediately
        FeedRepository.ingestParsedItem(feed, longItem, dao, engine, enforceFilters = true)
        FeedRepository.ingestParsedItem(feed, shortItem, dao, engine, enforceFilters = true)

        val ingestedLong = dao.getArticleByUrl(4, longItem.article.url)!!
        assertTrue(ingestedLong.isCached)
        assertTrue(ingestedLong.content.contains("bipartisan committee hearings"))

        val ingestedShort = dao.getArticleByUrl(4, shortItem.article.url)!!
        assertFalse(ingestedShort.isCached)

        // Also verify applyAutoDownloadResult preserves substantive <content:encoded> on HTTP 403 failure
        dao.insertArticle(ingestedLong.copy(isCached = false))
        val uncachedLong = dao.getArticleById(ingestedLong.id)!!
        FeedRepository.applyAutoDownloadResult(
            article = uncachedLong,
            downloadResult = HtmlDownloadResult(html = null, finalUrl = uncachedLong.url, scrapeResult = null, httpStatusCode = 403),
            initialImageUrl = uncachedLong.imageUrl,
            initialLocalImagePath = uncachedLong.localImagePath,
            articleDao = dao,
            scraperEngine = engine,
            enforceFilters = true
        )
        val fallbackUpdated = dao.getArticleById(ingestedLong.id)!!
        assertTrue(fallbackUpdated.isCached)
        assertTrue(fallbackUpdated.content.contains("bipartisan committee hearings"))

        // Short teaser without substantive body remains uncached on HTTP 403
        FeedRepository.applyAutoDownloadResult(
            article = ingestedShort,
            downloadResult = HtmlDownloadResult(html = null, finalUrl = ingestedShort.url, scrapeResult = null, httpStatusCode = 403),
            initialImageUrl = ingestedShort.imageUrl,
            initialLocalImagePath = ingestedShort.localImagePath,
            articleDao = dao,
            scraperEngine = engine,
            enforceFilters = true
        )
        val shortAfter403 = dao.getArticleById(ingestedShort.id)!!
        assertFalse(shortAfter403.isCached)
    }
}
