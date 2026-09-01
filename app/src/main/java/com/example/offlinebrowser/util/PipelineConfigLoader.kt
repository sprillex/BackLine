package com.example.offlinebrowser.util

import android.content.Context
import com.example.offlinebrowser.data.model.PipelineConfig
import com.example.offlinebrowser.data.model.StepConfig
import com.google.gson.Gson
import java.io.File

class PipelineConfigLoader(private val context: Context? = null) {

    fun loadConfig(overrideFile: File? = null): PipelineConfig {
        if (overrideFile != null && overrideFile.exists()) {
            try {
                val json = overrideFile.readText()
                val config = Gson().fromJson(json, PipelineConfig::class.java)
                if (config != null && config.steps.isNotEmpty()) return config
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (context != null) {
            val userFile = File(context.filesDir, "summary_pipeline_config.json")
            if (userFile.exists()) {
                try {
                    val json = userFile.readText()
                    val config = Gson().fromJson(json, PipelineConfig::class.java)
                    if (config != null && config.steps.isNotEmpty()) return config
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                val json = context.assets.open("summary_pipeline_config.json").bufferedReader().use { it.readText() }
                val config = Gson().fromJson(json, PipelineConfig::class.java)
                if (config != null && config.steps.isNotEmpty()) return config
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return getDefaultFallbackConfig()
    }

    companion object {
        fun getDefaultFallbackConfig(): PipelineConfig {
            return PipelineConfig(
                version = 1,
                steps = listOf(
                    StepConfig(
                        stepId = "step_1_anchor",
                        promptTemplate = "<start_of_turn>user\nIdentify the main subject and core message of this text in one sentence. Ignore cast lists, navigation items, and ratings.\n\nText:\n\"\"\"\n{{CLEANED_ARTICLE_TEXT}}\n\"\"\"<end_of_turn>\n<start_of_turn>model\n",
                        temperature = 0.1f,
                        maxTokens = 60,
                        repeatPenalty = 1.0f,
                        stopSequences = listOf("<end_of_turn>")
                    ),
                    StepConfig(
                        stepId = "step_2_claims",
                        promptTemplate = "<start_of_turn>user\nIdentify the main subject and core message of this text in one sentence. Ignore cast lists, navigation items, and ratings.\n\nText:\n\"\"\"\n{{CLEANED_ARTICLE_TEXT}}\n\"\"\"<end_of_turn>\n<start_of_turn>model\n{{STEP_1_OUTPUT}}<end_of_turn>\n<start_of_turn>user\nBased on that subject, extract 3 factual claims made by the author. Do not include credits, ratings, or cast names.<end_of_turn>\n<start_of_turn>model\n",
                        temperature = 0.1f,
                        maxTokens = 150,
                        repeatPenalty = 1.15f,
                        stopSequences = listOf("<end_of_turn>")
                    ),
                    StepConfig(
                        stepId = "step_3_synthesis",
                        promptTemplate = "<start_of_turn>user\nConvert these raw points into a clean, concise 2-to-3 bullet point summary for an executive reader:\n\n{{INPUT}}<end_of_turn>\n<start_of_turn>model\n",
                        temperature = 0.3f,
                        maxTokens = 120,
                        repeatPenalty = 1.0f,
                        stopSequences = listOf("<end_of_turn>")
                    )
                )
            )
        }
    }
}
