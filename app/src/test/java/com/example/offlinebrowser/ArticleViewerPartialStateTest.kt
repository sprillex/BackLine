package com.example.offlinebrowser

import com.example.offlinebrowser.data.model.Article
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleViewerPartialStateTest {

    private fun makeArticle(isCached: Boolean, content: String): Article {
        return Article(
            id = 1,
            feedId = 1,
            title = "Test Article",
            url = "https://www.mlive.com/news/2026/11/story.html?utm_campaign=RSS|NSNS",
            content = content,
            publishedDate = 1700000000000L,
            isCached = isCached
        )
    }

    @Test
    fun testShowsPartialBannerWhenArticleIsNotCached() {
        val uncachedTeaser = makeArticle(
            isCached = false,
            content = "<p>Short RSS teaser preview.</p>"
        )
        assertTrue(ArticleViewerActivity.shouldShowPartialBanner(uncachedTeaser))

        val uncachedEmpty = makeArticle(
            isCached = false,
            content = ""
        )
        assertTrue(ArticleViewerActivity.shouldShowPartialBanner(uncachedEmpty))
    }

    @Test
    fun testShowsPartialBannerWhenCachedButContentIsBlank() {
        val blankCached = makeArticle(isCached = true, content = "   ")
        assertTrue(ArticleViewerActivity.shouldShowPartialBanner(blankCached))

        val emptyHtmlCached = makeArticle(isCached = true, content = "<html><head></head><body></body></html>")
        assertTrue(ArticleViewerActivity.shouldShowPartialBanner(emptyHtmlCached))
    }

    @Test
    fun testHidesPartialBannerWhenArticleIsCachedWithContent() {
        val fullCached = makeArticle(
            isCached = true,
            content = "<html><body><h1>Full Story</h1><p>Complete offline cached article text.</p></body></html>"
        )
        assertFalse(ArticleViewerActivity.shouldShowPartialBanner(fullCached))
    }

    @Test
    fun testInjectPartialFooterHtmlRendersDarkBannerWithBrowserAndRetryLinks() {
        val teaser = "<p>Only a teaser was included in the RSS feed.</p>"
        val url = "https://www.mlive.com/news/2026/11/story.html?utm_campaign=RSS|NSNS"

        val rendered = ArticleViewerActivity.injectPartialFooterHtml(teaser, url)

        assertTrue(rendered.contains("Only a teaser was included in the RSS feed."))
        assertTrue(rendered.contains("Partial Preview Cached"))
        assertTrue(rendered.contains("Open full article in browser"))
        assertTrue(rendered.contains("Retry Download"))
        assertTrue(rendered.contains("https://www.mlive.com/news/2026/11/story.html?utm_campaign=RSS%7CNSNS"))
        assertTrue(rendered.contains(ArticleViewerActivity.RETRY_DOWNLOAD_URI))
        assertTrue(rendered.contains("#1E1E1E"))
    }

    @Test
    fun testArticleViewerLayoutAndActivityWirePartialBannerAndBrowserIntent() {
        val layoutCandidates = listOf(
            File("src/main/res/layout/activity_article_viewer.xml"),
            File("app/src/main/res/layout/activity_article_viewer.xml")
        )
        val layoutXml = layoutCandidates.first { it.exists() }.readText()
        assertTrue(layoutXml.contains("partial_article_banner"))
        assertTrue(layoutXml.contains("btn_open_in_browser"))
        assertTrue(layoutXml.contains("btn_retry_download"))

        val activityCandidates = listOf(
            File("src/main/java/com/example/offlinebrowser/ArticleViewerActivity.kt"),
            File("app/src/main/java/com/example/offlinebrowser/ArticleViewerActivity.kt")
        )
        val activitySrc = activityCandidates.first { it.exists() }.readText()
        assertTrue(activitySrc.contains("Intent.ACTION_VIEW"))
        assertTrue(activitySrc.contains("shouldShowPartialBanner"))
        assertTrue(activitySrc.contains("View.VISIBLE"))
        assertTrue(activitySrc.contains("View.GONE"))
        assertTrue(activitySrc.contains("retryArticleDownload"))
    }
}
