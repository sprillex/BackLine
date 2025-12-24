package com.example.offlinebrowser.workers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.offlinebrowser.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.firstOrNull

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val preferencesRepository = PreferencesRepository(appContext)

    override suspend fun doWork(): Result {
        if (!checkNetworkConstraints()) {
            return Result.retry()
        }

        println("Syncing started...")
        val database = com.example.offlinebrowser.data.local.OfflineDatabase.getDatabase(applicationContext)
        val feedRepository = com.example.offlinebrowser.data.repository.FeedRepository(
            applicationContext,
            database.feedDao(),
            database.articleDao(),
            com.example.offlinebrowser.data.network.RssParser()
        )
        val weatherRepository = com.example.offlinebrowser.data.repository.WeatherRepository(database.weatherDao())

        // Sync Feeds
        val feeds = database.feedDao().getAllFeeds()
        // We need to collect from Flow, or just get list.
        // Flow is tricky in Worker. Let's add a suspend getFeedsList to Dao for simplicity,
        // or just collect first emission.

        try {
            // Note: In a real app we might want to parallelize this or handle partial failures better
            feeds.firstOrNull()?.forEach { feed ->
                try {
                    feedRepository.syncFeed(feed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Sync Weather
            weatherRepository.allWeather.firstOrNull()?.forEach { weather ->
                try {
                    weatherRepository.updateWeather(weather)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        return Result.success()
    }

    private fun checkNetworkConstraints(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        if (preferencesRepository.wifiOnly) {
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return false
            }
        }

        val allowedSsids = preferencesRepository.allowedWifiSsids
        if (allowedSsids.isNotEmpty()) {
             if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return false
            }
            // Note: Getting SSID requires ACCESS_FINE_LOCATION permissions on newer Android
            // and location services to be enabled.
            // For this implementation, we will try to get it via WifiManager
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            // SSID is usually wrapped in quotes
            val currentSsid = info.ssid.replace("\"", "")

            if (currentSsid !in allowedSsids) {
                return false
            }
        }

        return true
    }
}
