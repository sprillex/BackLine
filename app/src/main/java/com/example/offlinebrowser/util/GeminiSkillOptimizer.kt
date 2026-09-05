package com.example.offlinebrowser.util

import com.example.offlinebrowser.data.model.AiSkill
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiSkillOptimizer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    suspend fun optimizeSkill(
        apiKey: String,
        articleText: String,
        currentSkill: AiSkill,
        gemmaOutput: String,
        userCritique: String? = null
    ): Result<AiSkill> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API key is missing."))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val currentSkillJson = gson.toJson(currentSkill)
        val systemGuidance = """
            You are an expert Prompt Engineer specializing in small on-device SLMs (specifically Gemma 2B).

            Context & Guidelines for Gemma 2B:
            1. Gemma 2B tends to suffer from context copying (verbatim repeating parts of prompt/input), corporate horoscope filler (e.g. "stakeholders aligned on strategic visions"), and mid-sentence token exhaustion (cutting off).
            2. Keep prompt templates clear, concise, and structured. Enforce entity preservation (retain specific names, places, numbers, tools).
            3. Adjust parameters in steps (`maxTokens`, `temperature`, `repeatPenalty`, `stopSequences`) as needed to resolve the issues.
            4. Preserve the `id`, `displayName`, `targetScreens`, and step structure unless adjusting step IDs or count directly improves performance.

            Your task:
            Analyze the provided article text, current skill JSON definition, flawed Gemma model output, and optional user critique. Refine the `AiSkill` JSON to fix the reported failure mode.

            Return EXCLUSIVELY strict JSON adhering to the exact `AiSkill` schema below:

            {
              "id": "string",
              "displayName": "string",
              "summary": "string",
              "targetScreens": ["string"],
              "version": 1,
              "steps": [
                {
                  "stepId": "string",
                  "promptTemplate": "string",
                  "temperature": 0.2,
                  "maxTokens": 150,
                  "repeatPenalty": 1.15,
                  "stopSequences": ["<end_of_turn>"]
                }
              ]
            }
        """.trimIndent()

        val userMessage = """
            [INPUT DATA]
            Article Text:
            \"\"\"
            ${articleText.take(3000)}
            \"\"\"

            Current AiSkill JSON:
            ```json
            $currentSkillJson
            ```

            Flawed Gemma Output:
            \"\"\"
            $gemmaOutput
            \"\"\"

            ${if (!userCritique.isNullOrBlank()) "User Critique/Feedback:\n$userCritique" else "User Critique: Output was suboptimal."}
        """.trimIndent()

        val requestPayload = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray().apply {
                val contentObj = JsonObject().apply {
                    val partsArray = com.google.gson.JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", "$systemGuidance\n\n$userMessage")
                        }
                        add(partObj)
                    }
                    add("parts", partsArray)
                }
                add(contentObj)
            }
            add("contents", contentsArray)

            val genConfig = JsonObject().apply {
                addProperty("response_mime_type", "application/json")
                addProperty("temperature", 0.3)
            }
            add("generationConfig", genConfig)
        }

        val requestBody = gson.toJson(requestPayload).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return@withContext Result.failure(
                    Exception("Gemini API call failed [HTTP ${response.code}]: ${responseBody ?: "Empty response"}")
                )
            }

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = jsonResponse.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                return@withContext Result.failure(Exception("No candidates returned from Gemini API."))
            }

            val firstCandidate = candidates.get(0).asJsonObject
            val content = firstCandidate.getAsJsonObject("content")
            val parts = content.getAsJsonArray("parts")
            val text = parts.get(0).asJsonObject.get("text").asString.trim()

            // Strip markdown block if present despite mime_type request
            var cleanText = text
            if (cleanText.startsWith("```json")) {
                cleanText = cleanText.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (cleanText.startsWith("```")) {
                cleanText = cleanText.substringAfter("```").substringBeforeLast("```").trim()
            }

            val optimizedSkill = gson.fromJson(cleanText, AiSkill::class.java)
            if (optimizedSkill == null || optimizedSkill.steps.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to parse valid AiSkill object from Gemini response."))
            }

            Result.success(optimizedSkill)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
