package com.example.offlinebrowser.util

import com.example.offlinebrowser.data.model.ClassificationConfig
import com.example.offlinebrowser.data.model.ClassificationRule
import com.example.offlinebrowser.data.model.ClassificationSource
import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.network.ScraperEngine
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleClassifierTest {

    private val gson = Gson()

    private fun loadPluginRecipe(relativePath: String): ScraperRecipe {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find plugin file: $relativePath from ${File(".").absolutePath}")
        return gson.fromJson(file.readText(), ScraperRecipe::class.java)
    }

    @Test
    fun testV1RecipeBackwardsCompatibility() {
        val v1Json = """
            {
              "domainPattern": ".*example\\.com",
              "strategy": "CSS_SELECTOR",
              "targetIdentifier": "",
              "contentPath": ["article"],
              "titlePath": ["h1"],
              "injectRssImage": true,
              "removeSelectors": ["script"],
              "sourceName": "Example"
            }
        """.trimIndent()

        val recipe = gson.fromJson(v1Json, ScraperRecipe::class.java)
        assertEquals(".*example\\.com", recipe.domainPattern)
        assertEquals(ExtractionStrategy.CSS_SELECTOR, recipe.strategy)
        assertNull(recipe.summaryTruncateAfter)
        assertNull(recipe.summaryStripRegexes)
        assertNull(recipe.classification)

        val result = ArticleClassifier.evaluateRss(
            url = "https://example.com/post",
            title = "Hello",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = recipe,
            feedCategory = "SPORTS"
        )
        assertEquals("Sports", result.category)
        assertFalse(result.shouldHide)
    }

    @Test
    fun testMlivePluginDeserializationAndRssRules() {
        val mlive = loadPluginRecipe("plugins/en/News/mlive.json")
        assertNotNull(mlive.classification)
        assertEquals("News", mlive.classification?.defaultCategory)

        // 1. News URL
        val newsRes = ArticleClassifier.evaluateRss(
            url = "https://www.mlive.com/public-interest/2026/11/budget-bill.html",
            title = "State budget passes",
            author = "Reporter",
            contentHtml = "<p>Details inside</p>",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = mlive,
            feedCategory = "Michigan"
        )
        assertEquals("News", newsRes.category)
        assertFalse(newsRes.shouldHide)

        // 2. Highschool sports URL -> mapped to Sports and soft-hidden
        val sportsRes = ArticleClassifier.evaluateRss(
            url = "https://www.mlive.com/highschoolsports/2026/11/playoffs.html",
            title = "Playoffs recap",
            author = null,
            contentHtml = "<p>Game summary</p>",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = mlive,
            feedCategory = "Michigan"
        )
        assertEquals("Sports", sportsRes.category)
        assertTrue(sportsRes.shouldHide)

        // 3. Subcategory extraction from URL_REGEX
        val subcatRes = ArticleClassifier.evaluateRss(
            url = "https://www.mlive.com/news/ann-arbor/2026/11/city-council.html",
            title = "City council",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = mlive,
            feedCategory = "Fallback"
        )
        assertEquals("News", subcatRes.category)
        assertEquals("ann-arbor", subcatRes.subcategory)
        assertFalse(subcatRes.shouldHide)

        // 4. Affiliate link regex in content_html -> tagged affiliate and soft-hidden
        val affiliateRes = ArticleClassifier.evaluateRss(
            url = "https://www.mlive.com/news/2026/11/best-deals.html",
            title = "Best deals",
            author = null,
            contentHtml = "<p>Buy now via <a href=\"https://go.skimresources.com/?id=123\">link</a></p>",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = mlive,
            feedCategory = "Fallback"
        )
        assertEquals("News", affiliateRes.category)
        assertTrue(affiliateRes.tags.contains("affiliate"))
        assertTrue(affiliateRes.shouldHide)

        // 5. enforceFilters = false keeps category & tags but does not hide
        val unfilteredRes = ArticleClassifier.evaluateRss(
            url = "https://www.mlive.com/sports-betting/2026/11/promo.html",
            title = "Promo",
            author = null,
            contentHtml = "<p>pc_aff_link=abc</p>",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = mlive,
            feedCategory = "Fallback",
            enforceFilters = false
        )
        assertEquals("Gambling", unfilteredRes.category)
        assertTrue(unfilteredRes.tags.contains("affiliate"))
        assertFalse(unfilteredRes.shouldHide)
    }

    @Test
    fun testOscodaHeraldPluginRulesAndSummarySanitization() {
        val oscoda = loadPluginRecipe("plugins/en/News/oscodaherald.json")
        assertEquals("Login to continue reading", oscoda.summaryTruncateAfter)
        assertEquals(2, oscoda.summaryStripRegexes?.size)
        assertEquals("Local", oscoda.classification?.defaultCategory)

        // 1. RSS_CATEGORIES "News" -> mapped to "Local"
        val newsRes = ArticleClassifier.evaluateRss(
            url = "https://www.oscodaherald.com/story-1",
            title = "Township meeting",
            author = "Jane Reporter",
            contentHtml = "<p>Meeting details</p>",
            rssCategories = listOf("News"),
            mediaCount = 0,
            recipe = oscoda,
            feedCategory = "General"
        )
        assertEquals("Local", newsRes.category)
        assertFalse(newsRes.shouldHide)

        // 2. RSS_CATEGORIES "Classifieds" -> soft-hidden
        val classifiedsRes = ArticleClassifier.evaluateRss(
            url = "https://www.oscodaherald.com/classified-1",
            title = "For Sale",
            author = "staff",
            contentHtml = "",
            rssCategories = listOf("News", "Classifieds"),
            mediaCount = 0,
            recipe = oscoda,
            feedCategory = "General"
        )
        assertEquals("Classifieds", classifiedsRes.category)
        assertTrue(classifiedsRes.shouldHide)

        // 3. RSS_FIELD_EQUALS author == "posteditor" -> tag "pdf_embed" -> soft-hidden
        val pdfRes = ArticleClassifier.evaluateRss(
            url = "https://www.oscodaherald.com/epaper",
            title = "Weekly Edition",
            author = "posteditor",
            contentHtml = "",
            rssCategories = listOf("News"),
            mediaCount = 0,
            recipe = oscoda,
            feedCategory = "General"
        )
        assertTrue(pdfRes.tags.contains("pdf_embed"))
        assertTrue(pdfRes.shouldHide)

        // 4. RSS_FIELD_REGEX title matches "^Cartoon\b" -> tag "cartoon" -> soft-hidden
        val cartoonRes = ArticleClassifier.evaluateRss(
            url = "https://www.oscodaherald.com/cartoon-1",
            title = "Cartoon: Snow Day",
            author = "artist",
            contentHtml = "",
            rssCategories = listOf("News"),
            mediaCount = 0,
            recipe = oscoda,
            feedCategory = "General"
        )
        assertTrue(cartoonRes.tags.contains("cartoon"))
        assertTrue(cartoonRes.shouldHide)

        // 5. Summary rules: truncate after marker and strip email/name bylines
        val rawSnippet = "By John Doe jdoe@oscodaherald.com Local officials met Tuesday to approve road repairs for the upcoming spring season. Login to continue reading Subscribe now for $5/month."
        val cleaned = ArticleClassifier.applySummaryRules(rawSnippet, oscoda)
        assertEquals("Local officials met Tuesday to approve road repairs for the upcoming spring season.", cleaned)

        val plainByline = "By Jane Smith Commissioners voted unanimously on the new library funding measure."
        assertEquals(
            "Commissioners voted unanimously on the new library funding measure.",
            ArticleClassifier.applySummaryRules(plainByline, oscoda)
        )
    }

    @Test
    fun testOscodaSummaryRulesInSummarizationPipelineSanitizeInput() {
        val oscoda = loadPluginRecipe("plugins/en/News/oscodaherald.json")
        val pipeline = ArticleSummarizationPipeline()
        val html = """
            <html><body>
                <p>By Alice Walker awalker@oscodaherald.com The county board approved a new watershed conservation grant on Wednesday morning after public comment.</p>
                <p>Funding will support shoreline restoration across three local townships over the next two fiscal years.</p>
                <p>Engineers expect design work to begin immediately following state environmental agency review.</p>
                <p>Login to continue reading</p>
                <p>Subscribe today to unlock unlimited digital access and archival editions.</p>
            </body></html>
        """.trimIndent()

        val sanitized = pipeline.sanitizeInput(html, oscoda)
        assertFalse(sanitized.contains("awalker@oscodaherald.com"))
        assertFalse(sanitized.contains("Login to continue reading"))
        assertFalse(sanitized.contains("Subscribe today"))
        assertTrue(sanitized.contains("watershed conservation grant"))
        assertTrue(sanitized.contains("shoreline restoration"))
    }

    @Test
    fun testToledoBladePluginRssAndStage2JsVarPath() {
        val toledo = loadPluginRecipe("plugins/en/News/toledoblade.json")
        assertEquals(ExtractionStrategy.EXTRACT_FROM_JS_VAR, toledo.strategy)
        assertEquals("pgStoryZeroJSON", toledo.targetIdentifier)

        // 1. RSS_MEDIA_COUNT >= 6 -> photo_gallery -> hidden
        val galleryByMedia = ArticleClassifier.evaluateRss(
            url = "https://www.toledoblade.com/local/2026/11/17/parade/stories/20261117001",
            title = "Holiday Parade",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 6,
            recipe = toledo,
            feedCategory = "News"
        )
        assertEquals("Local", galleryByMedia.category)
        assertTrue(galleryByMedia.tags.contains("photo_gallery"))
        assertTrue(galleryByMedia.shouldHide)

        // 2. mediaCount = 5 -> NOT tagged
        val normalCount = ArticleClassifier.evaluateRss(
            url = "https://www.toledoblade.com/local/courts/2026/11/17/trial/stories/20261117002",
            title = "Court ruling",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 5,
            recipe = toledo,
            feedCategory = "News"
        )
        assertEquals("Local", normalCount.category)
        assertEquals("courts", normalCount.subcategory)
        assertFalse(normalCount.tags.contains("photo_gallery"))
        assertFalse(normalCount.shouldHide)

        // 3. RSS_FIELD_REGEX case-insensitive title "^photo gallery:"
        val galleryByTitle = ArticleClassifier.evaluateRss(
            url = "https://www.toledoblade.com/news/2026/11/17/photos/stories/20261117003",
            title = "Photo Gallery: Lake Erie Sunset",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 1,
            recipe = toledo,
            feedCategory = "News"
        )
        assertTrue(galleryByTitle.tags.contains("photo_gallery"))
        assertTrue(galleryByTitle.shouldHide)

        // 4. Stage 2 JS_VAR_PATH via ScraperEngine
        val engine = ScraperEngine()
        engine.loadRecipes(listOf(toledo))

        val sportsHtml = """
            <html><head><script>
            var pgStoryZeroJSON = {
              "articles": [{
                "title": "Mud Hens Victory",
                "section": "sports",
                "subsection": "baseball",
                "body": "<p>Final score 5-2.</p>"
              }]
            };
            </script></head><body></body></html>
        """.trimIndent()

        val scrapeSports = engine.processWithMetadata(
            url = "https://www.toledoblade.com/story/2026/11/17/mud-hens/stories/20261117004",
            html = sportsHtml
        )
        assertNotNull(scrapeSports)
        assertEquals("Sports", scrapeSports?.category)
        assertEquals("baseball", scrapeSports?.subcategory)
        assertTrue(scrapeSports?.shouldHide == true)

        val localHtml = """
            <html><head><script>
            var pgStoryZeroJSON = {
              "articles": [{
                "title": "Bridge Repair Begins",
                "section": "local",
                "subsection": "traffic",
                "body": "<p>Lane closures start Monday.</p>"
              }]
            };
            </script></head><body></body></html>
        """.trimIndent()

        val scrapeLocal = engine.processWithMetadata(
            url = "https://www.toledoblade.com/story/2026/11/17/bridge/stories/20261117005",
            html = localHtml
        )
        assertNotNull(scrapeLocal)
        assertEquals("Local", scrapeLocal?.category)
        assertEquals("traffic", scrapeLocal?.subcategory)
        assertFalse(scrapeLocal?.shouldHide == true)
        assertTrue(scrapeLocal?.html?.contains("Lane closures start Monday.") == true)
    }

    @Test
    fun testCaseInsensitiveCategoryNormalizationAndExactMapCasing() {
        assertEquals("Sports", ArticleClassifier.normalizeCategory("SPORTS"))
        assertEquals("Sports", ArticleClassifier.normalizeCategory("sports"))
        assertEquals("Sports", ArticleClassifier.normalizeCategory("Sports"))
        assertEquals("World News", ArticleClassifier.normalizeCategory("world news"))

        val map = mapOf("scifi" to "Sci-Fi", "local" to "Local")
        assertEquals("Sci-Fi", ArticleClassifier.normalizeCategory("SCIFI", map))
        assertEquals("Sci-Fi", ArticleClassifier.normalizeCategory("scifi", map))

        assertEquals("Sports", ArticleClassifier.normalizeDisplayCategory("SPORTS"))
        assertEquals("Sports", ArticleClassifier.normalizeDisplayCategory("sports"))
        assertEquals("Sci-Fi", ArticleClassifier.normalizeDisplayCategory("Sci-Fi"))
    }

    @Test
    fun testCategoryPrecedenceOrder() {
        val recipeWithRules = ScraperRecipe(
            domainPattern = ".*example\\.com",
            strategy = ExtractionStrategy.CSS_SELECTOR,
            contentPath = listOf("article"),
            classification = ClassificationConfig(
                defaultCategory = "Local",
                rssRules = listOf(
                    ClassificationRule(
                        source = ClassificationSource.URL_REGEX,
                        pattern = "^https?://[^/]+/([^/]+)/\\d{4}/",
                        categoryGroup = 1
                    )
                ),
                categoryMap = mapOf("biz" to "Business")
            )
        )

        // Precedence 1: Plugin-extracted & mapped category wins over defaultCategory and feed.category
        val p1 = ArticleClassifier.evaluateRss(
            url = "https://example.com/biz/2026/earnings",
            title = "Q3",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = recipeWithRules,
            feedCategory = "Tech"
        )
        assertEquals("Business", p1.category)

        // Precedence 2: Plugin defaultCategory wins over feed.category when no rule matched
        val p2 = ArticleClassifier.evaluateRss(
            url = "https://example.com/about-us",
            title = "About",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = recipeWithRules,
            feedCategory = "Tech"
        )
        assertEquals("Local", p2.category)

        // Precedence 3: Feed.category used when no plugin matches or plugin has no classification block
        val p3NoPlugin = ArticleClassifier.evaluateRss(
            url = "https://other.org/post",
            title = "Post",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = null,
            feedCategory = "SCIENCE"
        )
        assertEquals("Science", p3NoPlugin.category)

        val v1Recipe = recipeWithRules.copy(classification = null)
        val p3NoClassification = ArticleClassifier.evaluateRss(
            url = "https://example.com/post",
            title = "Post",
            author = null,
            contentHtml = "",
            rssCategories = emptyList(),
            mediaCount = 0,
            recipe = v1Recipe,
            feedCategory = "tech"
        )
        assertEquals("Tech", p3NoClassification.category)
    }

    @Test
    fun testOscodaHeraldStage2HtmlRegexPaywallDetectionBeforeStripping() {
        val oscoda = loadPluginRecipe("plugins/en/News/oscodaherald.json")
        assertEquals(2, oscoda.version)
        assertTrue(oscoda.classification?.dropIfTagIn?.contains("paywalled") == true)

        val engine = ScraperEngine().apply { loadRecipes(listOf(oscoda)) }

        // 1. Paywall modal removed by removeSelectors (#checkout_form_wrapper / .jquery-modal),
        //    but HTML_REGEX runs against raw downloaded HTML BEFORE modal/script stripping!
        val paywallModalHtml = """
            <html><body>
                <h1 class="entry-title">County Budget Vote</h1>
                <div class="entry-content">
                    <p>Commissioners met on Tuesday to discuss next year's budget.</p>
                </div>
                <div id="checkout_form_wrapper" class="jquery-modal">
                    <div class="cherryroad-media-paywall">
                        <p>Subscribe to continue reading this article.</p>
                    </div>
                </div>
            </body></html>
        """.trimIndent()

        val modalScrape = engine.processWithMetadata(
            url = "https://www.oscodaherald.com/county-budget-vote",
            html = paywallModalHtml,
            enforceFilters = true
        )
        assertNotNull(modalScrape)
        assertTrue(modalScrape!!.tags.contains("paywalled"))
        assertTrue(modalScrape.shouldHide)

        // 2. Case-insensitive "login to continue reading" text in raw HTML
        val loginPromptHtml = """
            <html><body>
                <h1 class="entry-title">School Board Update</h1>
                <div class="entry-content">
                    <p>Opening paragraph teaser.</p>
                    <p>LOGIN TO CONTINUE READING this story.</p>
                </div>
            </body></html>
        """.trimIndent()

        val loginScrape = engine.processWithMetadata(
            url = "https://www.oscodaherald.com/school-board-update",
            html = loginPromptHtml,
            enforceFilters = true
        )
        assertNotNull(loginScrape)
        assertTrue(loginScrape!!.tags.contains("paywalled"))
        assertTrue(loginScrape.shouldHide)

        // 3. Clean full article -> NOT tagged paywalled and NOT hidden
        val cleanHtml = """
            <html><body>
                <h1 class="entry-title">Library Reopens</h1>
                <div class="entry-content">
                    <p>The county library reopened Monday morning after renovations.</p>
                </div>
            </body></html>
        """.trimIndent()
        val cleanScrape = engine.processWithMetadata(
            url = "https://www.oscodaherald.com/library-reopens",
            html = cleanHtml,
            enforceFilters = true
        )
        assertNotNull(cleanScrape)
        assertFalse(cleanScrape!!.tags.contains("paywalled"))
        assertFalse(cleanScrape.shouldHide)

        // 4. enforceFilters = false assigns tag but does not hide
        val unfilteredScrape = engine.processWithMetadata(
            url = "https://www.oscodaherald.com/county-budget-vote",
            html = paywallModalHtml,
            enforceFilters = false
        )
        assertTrue(unfilteredScrape!!.tags.contains("paywalled"))
        assertFalse(unfilteredScrape.shouldHide)
    }

    @Test
    fun testStage2TextRegexPageRule() {
        val recipe = ScraperRecipe(
            domainPattern = ".*example\\.com",
            strategy = ExtractionStrategy.CSS_SELECTOR,
            contentPath = listOf("article"),
            classification = ClassificationConfig(
                defaultCategory = "News",
                pageRules = listOf(
                    ClassificationRule(
                        source = ClassificationSource.TEXT_REGEX,
                        pattern = "members-only content",
                        flags = "i",
                        assignTag = "paywalled"
                    )
                ),
                dropIfTagIn = listOf("paywalled")
            )
        )

        val html = "<html><body><article><p>This is <strong>Members-Only Content</strong>.</p></article></body></html>"
        val res = ArticleClassifier.evaluatePageHtml(
            rawHtml = html,
            recipe = recipe,
            enforceFilters = true
        )
        assertTrue(res.tags.contains("paywalled"))
        assertTrue(res.shouldHide)
    }
}
