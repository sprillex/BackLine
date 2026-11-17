package com.example.offlinebrowser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class FileLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLogRotationAt256KbAndClear() {
        val logger = FileLogger(tempFolder.root)
        val payload = "A".repeat(2048)

        // Write ~300 KB total to trigger rotation past MAX_LOG_BYTES (262_144)
        repeat(150) { idx ->
            logger.log("Entry $idx $payload")
        }

        assertTrue("Active log file should exist", logger.logFile.exists())
        assertTrue("Backup log file should exist after rotation", logger.backupLogFile.exists())
        assertTrue(
            "Active log file must stay <= 256 KB, was ${logger.logFile.length()}",
            logger.logFile.length() <= FileLogger.MAX_LOG_BYTES
        )
        assertTrue(
            "Backup log file must stay <= 256 KB, was ${logger.backupLogFile.length()}",
            logger.backupLogFile.length() <= FileLogger.MAX_LOG_BYTES
        )
        assertTrue(
            "Combined log storage must stay under 512 KB",
            logger.logFile.length() + logger.backupLogFile.length() <= FileLogger.MAX_LOG_BYTES * 2L
        )

        // Export streams backup (older) followed by active (newer)
        val exportedBytes = ByteArrayOutputStream().use { out ->
            assertTrue(logger.exportTo(out))
            out.toString(Charsets.UTF_8.name())
        }
        val firstBackupPos = exportedBytes.indexOf("Entry 0 ")
        val lastActivePos = exportedBytes.indexOf("Entry 149 ")
        assertTrue(firstBackupPos >= 0)
        assertTrue(lastActivePos > firstBackupPos)

        // clear() deletes both debug_log.txt and debug_log.1.txt
        logger.clear()
        assertFalse(logger.logFile.exists())
        assertFalse(logger.backupLogFile.exists())
    }

    @Test
    fun testReadTailRespectsMaxBytesAndMaxLinesBounds() {
        val logger = FileLogger(tempFolder.root)

        // Write > 1 MB directly to logFile to verify RandomAccessFile tail bounds
        val sb = StringBuilder()
        for (i in 1..15_000) {
            sb.append("[2026-11-17 12:00:00] Log line number $i with padding text to expand file size\n")
        }
        logger.logFile.writeText(sb.toString())
        assertTrue(logger.logFile.length() > 1_000_000L)

        val tail = logger.readTail(maxBytes = 4096, maxLines = 25)
        val lines = tail.split("\n")

        assertTrue("Expected <= 25 lines, got ${lines.size}", lines.size <= 25)
        assertEquals(25, lines.size)
        assertTrue(
            "Expected <= 4096 bytes, got ${tail.toByteArray(Charsets.UTF_8).size}",
            tail.toByteArray(Charsets.UTF_8).size <= 4096
        )
        assertTrue("Tail should end with the last line", lines.last().contains("Log line number 15000"))
        assertTrue("First returned line should be complete", lines.first().startsWith("[2026-11-17"))
    }
}
