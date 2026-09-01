package com.example.offlinebrowser.data.model

data class StepConfig(
    val stepId: String,
    val promptTemplate: String,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 150,
    val repeatPenalty: Float = 1.15f,
    val stopSequences: List<String> = listOf("<end_of_turn>")
)

data class PipelineConfig(
    val version: Int = 1,
    val steps: List<StepConfig>
)
