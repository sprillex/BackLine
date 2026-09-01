package com.example.offlinebrowser.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ArticleSummarizationPipeline(
    private val modelRunner: LocalGemmaRunner = DefaultLocalGemmaRunner()
) {
    suspend fun summarize(rawArticleText: String): String = withContext(Dispatchers.Default) {
        val sanitizedText = sanitizeInput(rawArticleText)
        if (sanitizedText.isBlank()) {
            return@withContext "No article text available to summarize."
        }

        // Step 1: Anchor & Topic Identification
        val step1Prompt = buildString {
            append("<start_of_turn>user\n")
            append("Identify the main subject and core message of this text in one sentence. Ignore cast lists, navigation items, and ratings.\n\n")
            append("Text:\n\"\"\"\n$sanitizedText\n\"\"\"<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        val subjectAnchor = modelRunner.generate(
            prompt = step1Prompt,
            maxTokens = 60,
            temperature = 0.1f
        )

        // Step 2: Key Claim Extraction
        val step2Prompt = buildString {
            append(step1Prompt)
            append(subjectAnchor)
            append("<end_of_turn>\n<start_of_turn>user\n")
            append("Based on that subject, extract 3 factual claims made by the author. Do not include credits, ratings, or cast names.<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        val extractedFacts = modelRunner.generate(
            prompt = step2Prompt,
            maxTokens = 150,
            temperature = 0.1f,
            repeatPenalty = 1.15f
        )

        // Step 3: Synthesis & Context Reset (CRITICAL)
        val step3Prompt = buildString {
            append("<start_of_turn>user\n")
            append("Convert these raw points into a clean, concise 2-to-3 bullet point summary for an executive reader:\n\n")
            append(extractedFacts)
            append("<end_of_turn>\n<start_of_turn>model\n")
        }

        return@withContext modelRunner.generate(
            prompt = step3Prompt,
            maxTokens = 120,
            temperature = 0.3f
        )
    }

    fun sanitizeInput(rawText: String): String {
        if (rawText.isBlank()) return ""

        val doc = Jsoup.parse(rawText)
        doc.select("nav, header, footer, aside, script, style").remove()

        doc.select("p, h1, h2, h3, h4, h5, h6, div, li, tr, dt, dd, section, article").append("\n")
        doc.select("br").append("\n")

        val extractedText = doc.body()?.wholeText() ?: doc.wholeText()
        val rawLines = extractedText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val strictLines = rawLines.filter { it.length >= 40 }
        val finalLines = if (strictLines.size >= 3) {
            strictLines
        } else {
            rawLines.filter { it.length >= 20 }
        }

        return finalLines.joinToString("\n\n")
    }
}
