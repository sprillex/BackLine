package com.example.offlinebrowser

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.workers.SyncWorker
import java.util.concurrent.TimeUnit

class OfflineBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleSync()
    }

    private fun scheduleSync() {
        val prefs = PreferencesRepository(this)
        val interval = prefs.refreshIntervalMinutes

        // Basic constraints
        val constraintsBuilder = Constraints.Builder()
        if (prefs.wifiOnly) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraintsBuilder.build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }
}
