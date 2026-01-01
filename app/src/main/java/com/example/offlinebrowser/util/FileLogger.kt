package com.example.offlinebrowser.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogger(private val context: Context) {
    private val logFile: File
        get() = File(context.filesDir, "debug_log.txt")

    fun log(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logMessage = "[$timestamp] $message\n"
        try {
            FileWriter(logFile, true).use { it.append(logMessage) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLogFile(): File = logFile

    fun clear() {
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}
