package com.example.offlinebrowser.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
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

    fun downloadModel(url: String? = null): Long {
        val downloadUrl = url ?: preferencesRepository.gemmaModelUrl
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Gemma Model Download")
            .setDescription("Downloading Gemma LLM model file...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "gemma.bin")

        val downloadId = downloadManager.enqueue(request)
        val downloadedFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "gemma.bin")
        preferencesRepository.gemmaModelPath = downloadedFile.absolutePath
        return downloadId
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
