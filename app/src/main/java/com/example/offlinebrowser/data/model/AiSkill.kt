package com.example.offlinebrowser.data.model

data class SkillStepConfig(
    val stepId: String,
    val promptTemplate: String,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 150,
    val repeatPenalty: Float = 1.15f,
    val stopSequences: List<String> = listOf("<end_of_turn>")
)

data class AiSkill(
    val id: String,
    val displayName: String,
    val summary: String,
    val targetScreens: List<String>,
    val version: Int = 1,
    val steps: List<SkillStepConfig>
)

data class AiSkillRegistry(
    val version: Int = 1,
    val skills: List<AiSkill>
)
