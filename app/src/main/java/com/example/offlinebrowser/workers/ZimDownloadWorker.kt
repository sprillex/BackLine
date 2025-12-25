package com.example.offlinebrowser.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.network.SafeClientFactory
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ZimDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown File"
        val fileName = "${title.replace(" ", "_")}.zim"

        createNotificationChannel()
        setForeground(createForegroundInfo(title, 0))

        try {
            val client = SafeClientFactory.create(applicationContext)
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(workDataOf("error" to "Network error ${response.code}"))
            }

            val body = response.body ?: return Result.failure()
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()

            // Saving to app-specific files directory for simplicity and permission avoidance
            // /storage/emulated/0/Android/data/com.example.offlinebrowser/files/
            val file = File(applicationContext.getExternalFilesDir(null), fileName)
            val outputStream = FileOutputStream(file)

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
                        setForeground(createForegroundInfo(title, progress))
                        lastProgress = progress
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            showCompletionNotification(title, "Download complete")
            return Result.success()

        } catch (e: Exception) {
            showCompletionNotification(title, "Download failed: ${e.message}")
            return Result.failure(workDataOf("error" to e.message))
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading $title")
            .setContentText("$progress%")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

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
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
