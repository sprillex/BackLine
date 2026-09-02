package com.example.offlinebrowser.util

import com.example.offlinebrowser.data.model.AiSkill
import com.example.offlinebrowser.data.model.AiSkillRegistry
import com.example.offlinebrowser.data.model.SkillStepConfig
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiSkillManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class TestGemmaRunner : LocalGemmaRunner {
        data class CallRecord(
            val prompt: String,
            val maxTokens: Int,
            val temperature: Float,
            val repeatPenalty: Float,
            val stopSequences: List<String>
        )

        val calls = mutableListOf<CallRecord>()

        override suspend fun generate(
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            repeatPenalty: Float,
            stopSequences: List<String>
        ): String {
            calls.add(CallRecord(prompt, maxTokens, temperature, repeatPenalty, stopSequences))
            return when (calls.size) {
                1 -> "Fact 1: Google released Gemma 3 models.\nFact 2: Offline Browser handles local AI summaries."
                2 -> "• Google released Gemma 3 models.\n• Offline Browser handles local AI summaries."
                else -> "Default skill response"
            }
        }
    }

    @Test
    fun testParseAiSkillRegistryFromJson() {
        val json = """
            {
              "version": 1,
              "skills": [
                {
                  "id": "quick_extractor",
                  "displayName": "Quick Extractor",
                  "summary": "Extracts quick facts.",
                  "targetScreens": ["ReaderScreen", "ArticleViewerActivity"],
                  "version": 1,
                  "steps": [
                    {
                      "stepId": "step_extract",
                      "promptTemplate": "Extract: {{INPUT}}",
                      "temperature": 0.1,
                      "maxTokens": 100,
                      "repeatPenalty": 1.1,
                      "stopSequences": ["<end>"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val registry = Gson().fromJson(json, AiSkillRegistry::class.java)
        assertEquals(1, registry.version)
        assertEquals(1, registry.skills.size)
        val skill = registry.skills[0]
        assertEquals("quick_extractor", skill.id)
        assertEquals("Quick Extractor", skill.displayName)
        assertEquals(2, skill.targetScreens.size)
        assertEquals(1, skill.steps.size)
        assertEquals("Extract: {{INPUT}}", skill.steps[0].promptTemplate)
    }

    @Test
    fun testGetSkillsForScreenCaseInsensitiveMatching() {
        val customRegistry = AiSkillRegistry(
            version = 1,
            skills = listOf(
                AiSkill(
                    id = "skill_1",
                    displayName = "Skill 1",
                    summary = "Test summary 1",
                    targetScreens = listOf("ArticleViewActivity", "ArticleViewerActivity"),
                    version = 1,
                    steps = emptyList()
                ),
                AiSkill(
                    id = "skill_2",
                    displayName = "Skill 2",
                    summary = "Test summary 2",
                    targetScreens = listOf("SettingsActivity"),
                    version = 1,
                    steps = emptyList()
                )
            )
        )

        val manager = AiSkillManager(customRegistry = customRegistry)

        // Case-insensitive exact screen discovery
        val viewerSkills = manager.getSkillsForScreen("articlevieweractivity")
        assertEquals(1, viewerSkills.size)
        assertEquals("skill_1", viewerSkills[0].id)

        val viewSkills = manager.getSkillsForScreen("ARTICLEVIEWACTIVITY ")
        assertEquals(1, viewSkills.size)
        assertEquals("skill_1", viewSkills[0].id)

        val settingsSkills = manager.getSkillsForScreen("settingsactivity")
        assertEquals(1, settingsSkills.size)
        assertEquals("skill_2", settingsSkills[0].id)

        val unknownSkills = manager.getSkillsForScreen("UnknownActivity")
        assertTrue(unknownSkills.isEmpty())
    }

    @Test
    fun testExecuteSkillSequentialTurnExecutionAndPlaceholders() = runBlocking {
        val testRunner = TestGemmaRunner()
        val manager = AiSkillManager(modelRunner = testRunner)

        val skill = AiSkill(
            id = "test_skill",
            displayName = "Test Skill",
            summary = "Testing execution",
            targetScreens = listOf("ArticleViewerActivity"),
            version = 1,
            steps = listOf(
                SkillStepConfig(
                    stepId = "step_1_facts",
                    promptTemplate = "Raw: {{RAW_INPUT}}\nClean: {{CLEANED_ARTICLE_TEXT}}\nInput: {{INPUT}}",
                    temperature = 0.15f,
                    maxTokens = 180,
                    repeatPenalty = 1.15f,
                    stopSequences = listOf("<end_of_turn>")
                ),
                SkillStepConfig(
                    stepId = "step_2_synthesis",
                    promptTemplate = "Notes:\n{{INPUT}}\nClean text retained: {{CLEANED_ARTICLE_TEXT}}",
                    temperature = 0.2f,
                    maxTokens = 120,
                    repeatPenalty = 1.15f,
                    stopSequences = listOf("<end_of_turn>")
                )
            )
        )

        val rawHtmlInput = "<html><body><p>This is a paragraph about local AI capabilities running on Android smartphones using Gemma 3 models.</p></body></html>"

        val result = manager.executeSkill(skill, rawHtmlInput)

        assertEquals(2, testRunner.calls.size)

        // Step 1 check
        val step1 = testRunner.calls[0]
        assertEquals(180, step1.maxTokens)
        assertEquals(0.15f, step1.temperature, 0.001f)
        assertTrue(step1.prompt.contains("Raw: <html><body><p>This is a paragraph"))
        assertTrue(step1.prompt.contains("Clean: This is a paragraph about local AI capabilities"))

        // Step 2 check
        val step2 = testRunner.calls[1]
        assertEquals(120, step2.maxTokens)
        assertEquals(0.2f, step2.temperature, 0.001f)
        assertTrue(step2.prompt.contains("Notes:\nFact 1: Google released Gemma 3 models."))
        assertTrue(step2.prompt.contains("Clean text retained: This is a paragraph about local AI capabilities"))

        assertEquals("• Google released Gemma 3 models.\n• Offline Browser handles local AI summaries.", result)
    }

    @Test
    fun testLoadRegistryOverrideFile() {
        val overrideFile = temporaryFolder.newFile("override_ai_skills.json")
        overrideFile.writeText("""
            {
              "version": 2,
              "skills": [
                {
                  "id": "override_skill",
                  "displayName": "Override Skill",
                  "summary": "Override summary",
                  "targetScreens": ["ArticleViewerActivity"],
                  "version": 1,
                  "steps": []
                }
              ]
            }
        """.trimIndent())

        val manager = AiSkillManager()
        val loadedRegistry = manager.loadRegistry(overrideFile = overrideFile)
        assertEquals(2, loadedRegistry.version)
        assertEquals(1, loadedRegistry.skills.size)
        assertEquals("override_skill", loadedRegistry.skills[0].id)
    }
}
