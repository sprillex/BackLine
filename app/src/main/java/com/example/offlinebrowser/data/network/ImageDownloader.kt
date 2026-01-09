package com.example.offlinebrowser.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ImageDownloader(private val context: Context) {

    suspend fun downloadImage(imageUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val input = connection.inputStream
                val fileName = hashString(imageUrl) + ".jpg" // Simple hash-based filename
                val imagesDir = File(context.filesDir, "images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val file = File(imagesDir, fileName)

                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }

                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun hashString(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
