package com.example.offlinebrowser.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSummarizationPipelineTest {

    private class TestGemmaRunner : LocalGemmaRunner {
        data class CallRecord(
            val prompt: String,
            val maxTokens: Int,
            val temperature: Float,
            val repeatPenalty: Float
        )

        val calls = mutableListOf<CallRecord>()

        override suspend fun generate(
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            repeatPenalty: Float
        ): String {
            calls.add(CallRecord(prompt, maxTokens, temperature, repeatPenalty))
            return when (calls.size) {
                1 -> "Main subject: Innovations in mobile offline AI architecture."
                2 -> "1. Local models process prompts without cloud connectivity.\n2. Context resets prevent attention pollution.\n3. Input sanitization removes website boilerplate."
                3 -> "• Local AI operates fully offline.\n• Context reset prevents attention pollution.\n• Input sanitization filters out navigation UI."
                else -> "Default response"
            }
        }
    }

    @Test
    fun testSanitizeInputStripsBoilerplateAndShortLines() {
        val pipeline = ArticleSummarizationPipeline()
        val htmlInput = """
            <!DOCTYPE html>
            <html>
            <head><title>Test Article</title></head>
            <body>
                <header><nav><a href="/home">Home</a><a href="/about">About Us</a></nav></header>
                <script>console.log("ad script");</script>
                <style>.ad { color: red; }</style>
                <h1>Main Heading</h1>
                <div class="rating">Rating: 4.5/5 stars</div>
                <p>Share this article</p>
                <p>This is a comprehensive paragraph about offline artificial intelligence models running directly on mobile hardware without requiring internet servers.</p>
                <p>Another detailed paragraph discussing how sequential multi-turn prompt engineering improves summary density and accuracy for executive summaries.</p>
                <p>A third long paragraph explaining that context resets prevent attention pollution from accumulating previous raw text turns during final synthesis.</p>
                <aside><div class="related">Related content and recommended links.</div></aside>
                <footer>Copyright 2026. All rights reserved.</footer>
            </body>
            </html>
        """.trimIndent()

        val sanitized = pipeline.sanitizeInput(htmlInput)

        // Assert boilerplate tags are stripped
        assertFalse("Should not contain Home", sanitized.contains("Home"))
        assertFalse("Should not contain About Us", sanitized.contains("About Us"))
        assertFalse("Should not contain ad script", sanitized.contains("ad script"))
        assertFalse("Should not contain Copyright", sanitized.contains("Copyright 2026"))

        // Assert short standalone UI lines (< 40 chars) are stripped
        assertFalse("Should not contain rating", sanitized.contains("Rating: 4.5/5 stars"))
        assertFalse("Should not contain share", sanitized.contains("Share this article"))

        // Assert long paragraphs are retained
        assertTrue("Should contain paragraph 1", sanitized.contains("offline artificial intelligence models"))
        assertTrue("Should contain paragraph 2", sanitized.contains("sequential multi-turn prompt engineering"))
        assertTrue("Should contain paragraph 3", sanitized.contains("context resets prevent attention pollution"))
    }

    @Test
    fun testSanitizeInputFallbackToShortLinesWhenFewerThanThreeLongLines() {
        val pipeline = ArticleSummarizationPipeline()
        val htmlInput = """
            <html>
            <body>
                <p>Short line 1 text here.</p>
                <p>Short line 2 text here.</p>
            </body>
            </html>
        """.trimIndent()

        val sanitized = pipeline.sanitizeInput(htmlInput)
        // Since there are < 3 lines of length >= 40, fallback keeps lines >= 20 chars
        assertTrue(sanitized.contains("Short line 1 text here."))
        assertTrue(sanitized.contains("Short line 2 text here."))
    }

    @Test
    fun testSummarizationPipelineSequenceAndContextReset() = runBlocking {
        val testRunner = TestGemmaRunner()
        val pipeline = ArticleSummarizationPipeline(testRunner)

        val sampleArticle = """
            <html>
            <body>
                <p>This is paragraph one containing deep details about mobile edge computing and local Gemma LLM inference capabilities.</p>
                <p>This is paragraph two detailing performance optimizations for reduced memory footprints on ARM chipsets.</p>
                <p>This is paragraph three covering multi-turn summarization pipeline engineering to minimize raw metadata regurgitation.</p>
            </body>
            </html>
        """.trimIndent()

        val finalSummary = pipeline.summarize(sampleArticle)

        assertEquals(3, testRunner.calls.size)

        // Step 1 check
        val step1 = testRunner.calls[0]
        assertEquals(60, step1.maxTokens)
        assertEquals(0.1f, step1.temperature, 0.001f)
        assertTrue(step1.prompt.contains("Identify the main subject and core message"))
        assertTrue(step1.prompt.contains("mobile edge computing"))

        // Step 2 check
        val step2 = testRunner.calls[1]
        assertEquals(150, step2.maxTokens)
        assertEquals(0.1f, step2.temperature, 0.001f)
        assertEquals(1.15f, step2.repeatPenalty, 0.001f)
        assertTrue(step2.prompt.contains(step1.prompt))
        assertTrue(step2.prompt.contains("Main subject: Innovations in mobile offline AI architecture."))
        assertTrue(step2.prompt.contains("extract 3 factual claims"))

        // Step 3 check (CONTEXT RESET)
        val step3 = testRunner.calls[2]
        assertEquals(120, step3.maxTokens)
        assertEquals(0.3f, step3.temperature, 0.001f)
        assertTrue(step3.prompt.contains("Convert these raw points into a clean, concise 2-to-3 bullet point summary"))
        assertTrue(step3.prompt.contains("1. Local models process prompts without cloud connectivity."))

        // Critical test: Step 3 MUST NOT contain raw article text or Step 1 prompt
        assertFalse(step3.prompt.contains("mobile edge computing"))
        assertFalse(step3.prompt.contains("Identify the main subject"))

        assertEquals("• Local AI operates fully offline.\n• Context reset prevents attention pollution.\n• Input sanitization filters out navigation UI.", finalSummary)
    }
}
