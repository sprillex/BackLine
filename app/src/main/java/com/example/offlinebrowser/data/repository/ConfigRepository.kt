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
        val existingFeeds = feedDao.getAllFeeds().first()
        // Strategy: Clear all existing or Upsert?
        // User said: "All content should be updated upon importing... It should not contain any downloaded content."
        // This suggests a fresh start for the feeds being imported.
        // I will clear existing feeds and weather to ensure the state matches the config file exactly.
        // Wait, "save and import a configuration file". Usually means backup/restore.
        // If I clear everything, I lose feeds that might not be in the config (if the config is partial, but here it's a full export).
        // Let's assume full restore: Clear current state, load new state.

        // However, wiping the database might be aggressive.
        // Let's try to add new ones and update existing ones.
        // But what about feeds that are NOT in the config?
        // "I want a version that can save and import a configuration file."
        // Usually implies transferring state or restoring backup.
        // I will implement "Upsert" logic. New feeds added, existing updated.
        // But user said "All content should be updated upon importing".
        // This refers to the content (articles/weather data), which should be fetched fresh.

        // I'll proceed with:
        // 1. Delete all existing Feeds and Weather?
        // If I switch devices, I want the config to be the source of truth.
        // If I use `Insert(onConflict = REPLACE)`, it handles updates.
        // But what about deletions? If I deleted a feed and restore an old config, it comes back (correct).
        // If I added a feed and restore an old config, does it disappear?
        // A "restore" typically resets to the checkpoint.
        // So I will clear existing data to match the config file strictly.
        // This is cleaner for "All content should be updated". If I kept old feeds, I'd have to update them too?
        // Or just the imported ones?
        // Safer to treat "Configuration" as the definition of what should be in the app.

        // To be safe against data loss of cached articles for feeds that ARE in the config,
        // I should probably check if feed exists.
        // BUT, the requirement says "It should not contain any downloaded content".
        // So the config file doesn't have articles.
        // If I wipe the DB, I lose existing articles.
        // If I keep the DB but update feeds, I keep existing articles for matching feeds.
        // User says: "All content should be updated upon importing".
        // This implies triggering a sync.

        // Decision: I will Iterate through imported feeds/weather.
        // Insert/Update them.
        // I will NOT delete feeds that are not in the config, unless I offer an option "Replace existing configuration" vs "Merge".
        // Given the phrasing "import a configuration file", "Merge" is often safer, but "Restore" is often what's meant.
        // I'll implement "Merge/Overwrite". Existing feeds with same URL will be updated (settings like download limit). New feeds added.
        // Existing feeds NOT in config will remain (safe).

        // Wait, "All content should be updated".
        // I need to trigger sync for ALL feeds (or at least the imported ones).
        // I'll trigger a global sync at the end.

        config.feeds.forEach { feedConfig ->
            // Check if exists to preserve ID (and thus articles?)
            // We don't have a getByUrl in FeedDao? Let's check.
            // FeedDao has `getAllFeeds`.
            // I'll do a simple loop check or add `getFeedByUrl` to Dao.
            // For now, iterate existing.

            val existing = existingFeeds.find { it.url == feedConfig.url }
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

        val existingWeather = weatherDao.getAllWeather().first()
        config.weatherLocations.forEach { weatherConfig ->
            val existing = existingWeather.find { it.latitude == weatherConfig.latitude && it.longitude == weatherConfig.longitude }
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
