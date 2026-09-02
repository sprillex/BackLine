package com.example.offlinebrowser.util

import com.example.offlinebrowser.data.model.PipelineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ArticleSummarizationPipeline(
    private val modelRunner: LocalGemmaRunner = DefaultLocalGemmaRunner(),
    private val pipelineConfig: PipelineConfig = PipelineConfigLoader.getDefaultFallbackConfig()
) {
    suspend fun summarize(rawArticleText: String): String = withContext(Dispatchers.Default) {
        val sanitizedText = sanitizeInput(rawArticleText)
        if (sanitizedText.isBlank()) {
            return@withContext "No article text available to summarize."
        }

        if (pipelineConfig.steps.isEmpty()) {
            return@withContext "No pipeline steps configured."
        }

        val contextMap = mutableMapOf<String, String>()
        contextMap["CLEANED_ARTICLE_TEXT"] = sanitizedText
        contextMap["INPUT"] = sanitizedText

        var lastStepOutput = ""

        for ((index, stepConfig) in pipelineConfig.steps.withIndex()) {
            val stepNumber = index + 1
            contextMap["INPUT"] = if (index == 0) sanitizedText else lastStepOutput

            var renderedPrompt = stepConfig.promptTemplate
            for ((key, value) in contextMap) {
                renderedPrompt = renderedPrompt.replace("{{$key}}", value)
            }

            lastStepOutput = modelRunner.generate(
                prompt = renderedPrompt,
                maxTokens = stepConfig.maxTokens,
                temperature = stepConfig.temperature,
                repeatPenalty = stepConfig.repeatPenalty,
                stopSequences = stepConfig.stopSequences
            )

            contextMap["STEP_${stepNumber}_OUTPUT"] = lastStepOutput
            contextMap[stepConfig.stepId.uppercase()] = lastStepOutput
        }

        return@withContext lastStepOutput
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
