package com.example.offlinebrowser.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.offlinebrowser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileImportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val sourceUriString = inputData.getString("source_uri") ?: return Result.failure()
        val fileName = inputData.getString("file_name") ?: "imported_file.zim"
        val sourceUri = Uri.parse(sourceUriString)

        createNotificationChannel()
        setForeground(createForegroundInfo(fileName, 0))

        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver

                // Get content size if possible
                val cursor = contentResolver.query(sourceUri, null, null, null, null)
                val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor?.moveToFirst()
                val contentLength = if (sizeIndex != null && sizeIndex != -1) cursor.getLong(sizeIndex) else -1L
                cursor?.close()

                val inputStream = contentResolver.openInputStream(sourceUri) ?: return@withContext Result.failure()

                // Saving to app-specific files directory
                val destFile = File(applicationContext.getExternalFilesDir(null), fileName)
                val outputStream = FileOutputStream(destFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        if (progress > lastProgress) {
                            setForeground(createForegroundInfo(fileName, progress))
                            setProgress(workDataOf("progress" to progress, "title" to "Importing $fileName"))
                            lastProgress = progress
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                showCompletionNotification("Import Complete", "Successfully imported $fileName")
                Result.success()

            } catch (e: Exception) {
                e.printStackTrace()
                showCompletionNotification("Import Failed", "Error: ${e.message}")
                Result.failure(workDataOf("error" to e.message))
            }
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Importing $title")
            .setContentText("$progress%")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Imports",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "import_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
