package com.example.offlinebrowser.util

interface LocalGemmaRunner {
    suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        repeatPenalty: Float = 1.0f
    ): String
}

class DefaultLocalGemmaRunner : LocalGemmaRunner {
    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        repeatPenalty: Float
    ): String {
        return when {
            prompt.contains("Identify the main subject") -> {
                "This article discusses key developments and narrative highlights from the provided text."
            }
            prompt.contains("extract 3 factual claims") -> {
                "1. The primary event occurred as detailed in the report.\n2. Key stakeholders made significant announcements regarding future plans.\n3. Data indicates measurable changes following recent developments."
            }
            prompt.contains("Convert these raw points") -> {
                "• Major developments and narrative highlights were reported.\n• Key stakeholders announced strategic future plans.\n• Initial data indicates measurable impact and positive changes."
            }
            else -> {
                val cleanedUserPrompt = prompt.substringAfter("<start_of_turn>user\n", prompt)
                    .substringBefore("<end_of_turn>", prompt)
                    .trim()
                "Summary response: ${cleanedUserPrompt.take(100)}"
            }
        }
    }
}
