package com.example.offlinebrowser.util

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    var logFile: File? = null

    fun log(message: String) {
        val file = logFile ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logMessage = "[$timestamp] $message\n"

        try {
            val writer = FileWriter(file, true)
            writer.append(logMessage)
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
