package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.local.ArticleDao
import com.example.offlinebrowser.data.local.FeedDao
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedConfig
import com.example.offlinebrowser.data.model.FeedType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class FeedDeduplicationTest {

    private class FakeFeedDao(initialFeeds: List<Feed> = emptyList()) : FeedDao {
        private var nextId = (initialFeeds.maxOfOrNull { it.id } ?: 0) + 1
        private val feeds = MutableStateFlow(initialFeeds)

        override fun getAllFeeds(): Flow<List<Feed>> = feeds

        override suspend fun getFeedById(id: Int): Feed? =
            feeds.value.firstOrNull { it.id == id }

        override suspend fun getFeedByUrl(url: String): Feed? {
            val target = url.trim().lowercase()
            return feeds.value.firstOrNull { it.url.trim().lowercase() == target }
        }

        override suspend fun insertFeed(feed: Feed): Long {
            val current = feeds.value.toMutableList()
            val assigned = if (feed.id == 0) feed.copy(id = nextId++) else feed
            val idx = current.indexOfFirst { it.id == assigned.id }
            if (idx >= 0) {
                current[idx] = assigned
            } else {
                current.add(assigned)
            }
            feeds.value = current
            return assigned.id.toLong()
        }

        override suspend fun updateFeed(feed: Feed) {
            feeds.value = feeds.value.map { if (it.id == feed.id) feed else it }
        }

        override suspend fun deleteFeed(feed: Feed) {
            feeds.value = feeds.value.filterNot { it.id == feed.id }
        }
    }

    private fun stubArticleDao(): ArticleDao {
        return Proxy.newProxyInstance(
            ArticleDao::class.java.classLoader,
            arrayOf(ArticleDao::class.java)
        ) { _, _, _ -> null } as ArticleDao
    }

    @Test
    fun testNormalizeFeedUrl() {
        assertEquals(
            "https://example.com/rss",
            FeedRepository.normalizeFeedUrl("  HTTPS://Example.COM/rss/  ")
        )
        assertEquals(
            "https://www.mlive.com/arc/outboundfeeds/rss?outputType=xml",
            FeedRepository.normalizeFeedUrl("https://WWW.MLIVE.com/arc/outboundfeeds/rss/?outputType=xml")
        )
        assertEquals(
            "http://example.com",
            FeedRepository.normalizeFeedUrl("HTTP://EXAMPLE.COM/")
        )
        assertEquals(
            "https://example.com/Path/Feed?a=1&b=2",
            FeedRepository.normalizeFeedUrl("https://example.com/Path/Feed/?a=1&b=2")
        )
    }

    @Test
    fun testAddFeedDetectsDuplicateNormalizedUrlsAndAllowsExplicitOverride() = runBlocking {
        val feedDao = FakeFeedDao()
        val repo = FeedRepository(null, feedDao, stubArticleDao())

        val first = repo.addFeed("  HTTPS://Example.COM/rss/ ", FeedType.RSS, downloadLimit = 5)
        assertTrue(first is AddFeedResult.Success)
        val firstId = (first as AddFeedResult.Success).feedId

        val stored = feedDao.getFeedById(firstId.toInt())!!
        assertEquals("https://example.com/rss", stored.url)

        // Adding equivalent URL without trailing slash / different host case returns Duplicate
        val duplicate = repo.addFeed("https://example.com/rss", FeedType.RSS)
        assertTrue(duplicate is AddFeedResult.Duplicate)
        assertEquals(firstId.toInt(), (duplicate as AddFeedResult.Duplicate).existingFeed.id)
        assertEquals(1, feedDao.getAllFeeds().first().size)

        // Explicit allowDuplicate = true creates the second feed
        val forced = repo.addFeed("https://example.com/rss/", FeedType.RSS, allowDuplicate = true)
        assertTrue(forced is AddFeedResult.Success)
        assertEquals(2, feedDao.getAllFeeds().first().size)
    }

    @Test
    fun testConfigRestoreSkipsDuplicateFeedUrls() = runBlocking {
        val feedDao = FakeFeedDao(
            listOf(Feed(id = 10, url = "https://www.oscodaherald.com/feed/", title = "Oscoda", type = FeedType.RSS))
        )

        val imported = listOf(
            FeedConfig("https://WWW.OSCODAHERALD.com/feed", "Oscoda Dup", "RSS", 5, "Local"),
            FeedConfig("https://www.mlive.com/rss/?outputType=xml", "MLive", "RSS", 5, "News"),
            FeedConfig("https://www.mlive.com/rss?outputType=xml", "MLive Dup In Config", "RSS", 5, "News")
        )

        ConfigRepository.restoreFeeds(imported, feedDao)

        val all = feedDao.getAllFeeds().first()
        assertEquals(2, all.size)
        assertEquals(10, all[0].id)
        assertEquals("https://www.mlive.com/rss?outputType=xml", all[1].url)
    }

    @Test
    fun testFeedDaoGetFeedByUrlQueryDefinition() {
        val candidates = listOf(
            File("src/main/java/com/example/offlinebrowser/data/local/FeedDao.kt"),
            File("app/src/main/java/com/example/offlinebrowser/data/local/FeedDao.kt")
        )
        val src = candidates.firstOrNull { it.exists() }?.readText()
            ?: throw IllegalStateException("Could not find FeedDao.kt")

        assertTrue(src.contains("WHERE LOWER(TRIM(url)) = LOWER(TRIM(:url))"))
        assertTrue(src.contains("suspend fun getFeedByUrl(url: String): Feed?"))
    }
}
