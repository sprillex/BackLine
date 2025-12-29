package com.example.offlinebrowser.data.repository

import android.content.Context
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.model.AppConfig
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.data.model.FeedConfig
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.data.model.SettingsConfig
import com.example.offlinebrowser.data.model.Weather
import com.example.offlinebrowser.data.model.WeatherConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class ConfigRepository(private val context: Context) {
    private val database = OfflineDatabase.getDatabase(context)
    private val preferencesRepository = PreferencesRepository(context)
    private val feedDao = database.feedDao()
    private val weatherDao = database.weatherDao()
    private val gson = Gson()

    suspend fun exportConfig(outputStream: OutputStream) {
        val feeds = feedDao.getAllFeeds().first().map {
            FeedConfig(
                url = it.url,
                title = it.title,
                type = it.type.name,
                downloadLimit = it.downloadLimit,
                category = it.category
            )
        }

        val weatherLocations = weatherDao.getAllWeather().first().map {
            WeatherConfig(
                locationName = it.locationName,
                latitude = it.latitude,
                longitude = it.longitude
            )
        }

        val settings = SettingsConfig(
            wifiOnly = preferencesRepository.wifiOnly,
            refreshInterval = preferencesRepository.refreshIntervalMinutes,
            feedLimitCount = preferencesRepository.feedLimitCount,
            feedLimitDays = preferencesRepository.feedLimitDays,
            weatherUnits = preferencesRepository.weatherUnits
        )

        val config = AppConfig(
            settings = settings,
            feeds = feeds,
            weatherLocations = weatherLocations
        )

        withContext(Dispatchers.IO) {
            outputStream.write(gson.toJson(config).toByteArray())
        }
    }

    suspend fun importConfig(inputStream: InputStream) {
        val json = withContext(Dispatchers.IO) {
            inputStream.bufferedReader().use { it.readText() }
        }
        val config = gson.fromJson(json, AppConfig::class.java)

        // Restore settings
        preferencesRepository.wifiOnly = config.settings.wifiOnly
        preferencesRepository.refreshIntervalMinutes = config.settings.refreshInterval
        preferencesRepository.feedLimitCount = config.settings.feedLimitCount
        preferencesRepository.feedLimitDays = config.settings.feedLimitDays
        preferencesRepository.weatherUnits = config.settings.weatherUnits

        // Restore Feeds
        val existingFeeds = feedDao.getAllFeeds().first().toMutableList()

        config.feeds.forEach { feedConfig ->
            // Check if exists to preserve ID
            // We consume the existing list to prevent multiple config entries from updating the same DB row
            val existing = existingFeeds.find { it.url == feedConfig.url }

            if (existing != null) {
                existingFeeds.remove(existing)
            }

            val feed = Feed(
                id = existing?.id ?: 0,
                url = feedConfig.url,
                title = feedConfig.title,
                type = try { FeedType.valueOf(feedConfig.type) } catch (e: Exception) { FeedType.RSS },
                downloadLimit = feedConfig.downloadLimit,
                category = feedConfig.category
                // lastUpdated defaults to 0, which is good for forcing update
            )
            feedDao.insertFeed(feed)
        }

        val existingWeather = weatherDao.getAllWeather().first().toMutableList()
        config.weatherLocations.forEach { weatherConfig ->
            // Match by name AND location to handle cases where user has multiple locations with same coords
            // but different names (or simply to correctly map specific config entries to specific DB rows).
            val existing = existingWeather.find {
                it.locationName == weatherConfig.locationName &&
                it.latitude == weatherConfig.latitude &&
                it.longitude == weatherConfig.longitude
            }

            if (existing != null) {
                existingWeather.remove(existing)
            }

            val weather = Weather(
                id = existing?.id ?: 0,
                locationName = weatherConfig.locationName,
                latitude = weatherConfig.latitude,
                longitude = weatherConfig.longitude,
                dataJson = "", // Clear data to force update
                lastUpdated = 0
            )
            weatherDao.insertWeather(weather)
        }
    }
}
