package com.example.offlinebrowser.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogger(private val logsDir: File) {
    constructor(context: Context) : this(context.filesDir)

    val logFile: File
        get() = File(logsDir, LOG_FILE_NAME)

    val backupLogFile: File
        get() = File(logsDir, BACKUP_LOG_FILE_NAME)

    fun log(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val logMessage = "[$timestamp] $message\n"
        val entryBytes = logMessage.toByteArray(Charsets.UTF_8)

        synchronized(logLock) {
            try {
                logsDir.mkdirs()
                if (logFile.exists() && logFile.length() + entryBytes.size > MAX_LOG_BYTES) {
                    rotateLocked()
                }
                FileOutputStream(logFile, true).use { it.write(entryBytes) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun readTail(maxBytes: Int = DEFAULT_TAIL_BYTES, maxLines: Int = DEFAULT_TAIL_LINES): String {
        if (maxBytes <= 0 || maxLines <= 0) return ""

        synchronized(logLock) {
            val (activeText, activeBytesRead) = readTailFromFile(logFile, maxBytes)
            val activeLines = splitNonEmptyTrailingLines(activeText)

            val allLines = if (activeLines.size < maxLines && activeBytesRead < maxBytes && backupLogFile.exists()) {
                val remainingBytes = maxBytes - activeBytesRead
                val (backupText, _) = readTailFromFile(backupLogFile, remainingBytes)
                splitNonEmptyTrailingLines(backupText) + activeLines
            } else {
                activeLines
            }

            if (allLines.isEmpty()) return ""
            return allLines.takeLast(maxLines).joinToString("\n")
        }
    }

    fun hasLogs(): Boolean {
        synchronized(logLock) {
            return (logFile.exists() && logFile.length() > 0L) ||
                (backupLogFile.exists() && backupLogFile.length() > 0L)
        }
    }

    fun exportTo(output: OutputStream): Boolean {
        synchronized(logLock) {
            val hasBackup = backupLogFile.exists()
            val hasActive = logFile.exists()
            if (!hasBackup && !hasActive) return false

            if (hasBackup) {
                backupLogFile.inputStream().use { it.copyTo(output) }
            }
            if (hasActive) {
                logFile.inputStream().use { it.copyTo(output) }
            }
            return true
        }
    }

    fun clear() {
        synchronized(logLock) {
            if (logFile.exists()) {
                logFile.delete()
            }
            if (backupLogFile.exists()) {
                backupLogFile.delete()
            }
        }
    }

    private fun rotateLocked() {
        if (backupLogFile.exists()) {
            backupLogFile.delete()
        }
        if (logFile.exists()) {
            val renamed = logFile.renameTo(backupLogFile)
            if (!renamed) {
                logFile.copyTo(backupLogFile, overwrite = true)
                logFile.delete()
            }
        }
    }

    private fun readTailFromFile(file: File, maxBytes: Int): Pair<String, Int> {
        if (!file.exists() || maxBytes <= 0) return "" to 0
        val fileLength = file.length()
        if (fileLength <= 0L) return "" to 0

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val toRead = minOf(fileLength, maxBytes.toLong()).toInt()
                val startOffset = fileLength - toRead
                raf.seek(startOffset)
                val buffer = ByteArray(toRead)
                raf.readFully(buffer)
                val raw = String(buffer, Charsets.UTF_8)
                val text = if (startOffset > 0L) {
                    val firstNewline = raw.indexOf('\n')
                    if (firstNewline >= 0) raw.substring(firstNewline + 1) else raw
                } else {
                    raw
                }
                text to toRead
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "" to 0
        }
    }

    private fun splitNonEmptyTrailingLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = if (text.endsWith("\n")) text.dropLast(1) else text
        if (normalized.isEmpty()) return emptyList()
        return normalized.split("\n")
    }

    companion object {
        const val MAX_LOG_BYTES = 262_144 // 256 KB
        const val DEFAULT_TAIL_BYTES = 65_536 // 64 KB
        const val DEFAULT_TAIL_LINES = 500
        private const val LOG_FILE_NAME = "debug_log.txt"
        private const val BACKUP_LOG_FILE_NAME = "debug_log.1.txt"
        private val logLock = Any()
    }
}
