package com.example.offlinebrowser.util

import android.content.Context
import com.example.offlinebrowser.data.model.AiSkill
import com.example.offlinebrowser.data.model.AiSkillRegistry
import com.example.offlinebrowser.data.model.SkillStepConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AiSkillManager(
    private val context: Context? = null,
    private val modelRunner: LocalGemmaRunner = DefaultLocalGemmaRunner(),
    customRegistry: AiSkillRegistry? = null
) {
    private val registry: AiSkillRegistry = customRegistry ?: loadRegistry()
    private val summarizationPipeline = ArticleSummarizationPipeline(modelRunner)

    fun loadRegistry(overrideFile: File? = null): AiSkillRegistry {
        if (overrideFile != null && overrideFile.exists()) {
            try {
                val json = overrideFile.readText()
                val reg = Gson().fromJson(json, AiSkillRegistry::class.java)
                if (reg != null && reg.skills.isNotEmpty()) return reg
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (context != null) {
            val userFile = File(context.filesDir, "ai_skills.json")
            if (userFile.exists()) {
                try {
                    val json = userFile.readText()
                    val reg = Gson().fromJson(json, AiSkillRegistry::class.java)
                    if (reg != null && reg.skills.isNotEmpty()) return reg
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                val json = context.assets.open("ai_skills.json").bufferedReader().use { it.readText() }
                val reg = Gson().fromJson(json, AiSkillRegistry::class.java)
                if (reg != null && reg.skills.isNotEmpty()) return reg
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return getDefaultFallbackRegistry()
    }

    fun getAllSkills(): List<AiSkill> {
        return registry.skills
    }

    fun getSkillsForScreen(screenIdentifier: String): List<AiSkill> {
        val trimmedScreen = screenIdentifier.trim()
        return registry.skills.filter { skill ->
            skill.targetScreens.any { it.trim().equals(trimmedScreen, ignoreCase = true) }
        }
    }

    fun getSkillById(skillId: String): AiSkill? {
        return registry.skills.find { it.id.equals(skillId.trim(), ignoreCase = true) }
    }

    suspend fun executeSkill(
        skill: AiSkill,
        initialInput: String,
        initialContext: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.Default) {
        if (skill.steps.isEmpty()) {
            return@withContext "No skill steps configured."
        }

        val sanitizedText = summarizationPipeline.sanitizeInput(initialInput)

        val contextMap = mutableMapOf<String, String>()
        contextMap["RAW_INPUT"] = initialInput
        contextMap["CLEANED_ARTICLE_TEXT"] = sanitizedText
        contextMap["INPUT"] = sanitizedText
        contextMap.putAll(initialContext)

        var lastStepOutput = ""

        for ((index, stepConfig) in skill.steps.withIndex()) {
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

    companion object {
        fun getDefaultFallbackRegistry(): AiSkillRegistry {
            return AiSkillRegistry(
                version = 1,
                skills = listOf(
                    AiSkill(
                        id = "article_summarizer",
                        displayName = "Executive Summary",
                        summary = "Extracts core entity actions and produces an executive 2-bullet summary.",
                        targetScreens = listOf("ArticleViewActivity", "ArticleViewerActivity", "WebReaderFragment"),
                        version = 1,
                        steps = listOf(
                            SkillStepConfig(
                                stepId = "extract_concrete_facts",
                                promptTemplate = "<start_of_turn>user\nList 2 or 3 specific actions, proposals, or decisions mentioned in the article below. Include the actual names of people, places, or technologies involved. Do not write generic statements.\n\nArticle:\n\"\"\"\n{{INPUT}}\n\"\"\"<end_of_turn>\n<start_of_turn>model\n",
                                temperature = 0.15f,
                                maxTokens = 180,
                                repeatPenalty = 1.15f,
                                stopSequences = listOf("<end_of_turn>")
                            ),
                            SkillStepConfig(
                                stepId = "synthesize_summary",
                                promptTemplate = "<start_of_turn>user\nRewrite these notes into 2 concise summary bullet points. Retain specific names, places, and tools mentioned. Do not use generic filler phrases like 'stakeholders', 'major developments', or 'strategic plans'.\n\nNotes:\n{{INPUT}}<end_of_turn>\n<start_of_turn>model\n",
                                temperature = 0.2f,
                                maxTokens = 120,
                                repeatPenalty = 1.15f,
                                stopSequences = listOf("<end_of_turn>")
                            )
                        )
                    )
                )
            )
        }
    }
}
