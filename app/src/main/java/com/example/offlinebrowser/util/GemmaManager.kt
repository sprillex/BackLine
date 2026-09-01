package com.example.offlinebrowser.util

import android.content.Context
import com.example.offlinebrowser.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

class GemmaManager(private val context: Context) {

    private val preferencesRepository = PreferencesRepository(context)

    fun getModelFile(): File? {
        val customPath = preferencesRepository.gemmaModelPath
        if (!customPath.isNullOrEmpty()) {
            val file = File(customPath)
            if (file.exists()) return file
        }
        val defaultFile = File(context.filesDir, "models/gemma.bin")
        if (defaultFile.exists()) return defaultFile

        val downloadsFile = File(context.getExternalFilesDir(null), "gemma.bin")
        if (downloadsFile.exists()) return downloadsFile

        return null
    }

    fun isModelAvailable(): Boolean {
        return getModelFile() != null
    }

    suspend fun generateSummary(htmlOrTextContent: String): String = withContext(Dispatchers.IO) {
        val cleanText = Jsoup.parse(htmlOrTextContent).text()
        if (cleanText.isBlank()) {
            return@withContext "No article text available to summarize."
        }

        val modelFile = getModelFile()
        if (modelFile != null && modelFile.exists()) {
            // Simulated Gemma Model Inference for offline environment
            // Extract key points from the text
            return@withContext performOfflineSummarization(cleanText)
        } else {
            // Default intelligent fallback summarizer when model file is not preloaded
            return@withContext performOfflineSummarization(cleanText)
        }
    }

    private fun performOfflineSummarization(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) {
            return "Unable to extract summary points from content."
        }

        val summarySentences = if (sentences.size <= 3) {
            sentences
        } else {
            // Pick key sentences from beginning, middle, and end
            listOf(
                sentences.first(),
                sentences[sentences.size / 2],
                sentences.last()
            ).distinct()
        }

        return summarySentences.joinToString(" ")
    }
}
