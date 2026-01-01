package com.example.offlinebrowser

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.data.repository.SuggestedFeedRepository
import com.example.offlinebrowser.workers.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OfflineBrowserApp : Application() {

    lateinit var suggestedFeedRepository: SuggestedFeedRepository

    override fun onCreate() {
        super.onCreate()

        val database = OfflineDatabase.getDatabase(this)
        suggestedFeedRepository = SuggestedFeedRepository(database.suggestedFeedDao())

        // Initialize data
        // CoroutineScope(Dispatchers.IO).launch {
        //     suggestedFeedRepository.initializeData()
        // }

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
