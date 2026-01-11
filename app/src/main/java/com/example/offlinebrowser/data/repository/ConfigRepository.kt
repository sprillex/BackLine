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
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ConfigRepository(private val context: Context) {
    private val database = OfflineDatabase.getDatabase(context)
    private val preferencesRepository = PreferencesRepository(context)
    private val feedDao = database.feedDao()
    private val weatherDao = database.weatherDao()
    private val gson = Gson()
    private val pluginsDir = File(context.filesDir, "plugins")

    suspend fun exportConfig(outputStream: OutputStream) {
        val config = createAppConfig()
        withContext(Dispatchers.IO) {
            outputStream.write(gson.toJson(config).toByteArray())
        }
    }

    suspend fun importConfig(inputStream: InputStream) {
        val json = withContext(Dispatchers.IO) {
            inputStream.bufferedReader().use { it.readText() }
        }
        val config = gson.fromJson(json, AppConfig::class.java)
        restoreAppConfig(config)
    }

    suspend fun exportFullBackup(outputStream: OutputStream) {
        val config = createAppConfig()
        val configJson = gson.toJson(config)

        withContext(Dispatchers.IO) {
            ZipOutputStream(outputStream).use { zipOut ->
                // Add config.json
                val configEntry = ZipEntry("config.json")
                zipOut.putNextEntry(configEntry)
                zipOut.write(configJson.toByteArray())
                zipOut.closeEntry()

                // Add plugins
                if (pluginsDir.exists() && pluginsDir.isDirectory) {
                    pluginsDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".json")) {
                            val entry = ZipEntry("plugins/${file.name}")
                            zipOut.putNextEntry(entry)
                            file.inputStream().use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        }
    }

    suspend fun importFullBackup(inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "config.json") {
                        val json = zipIn.bufferedReader().readText() // Note: reading fully from ZipInputStream directly via Reader might close it? No, bufferedReader wraps it.
                        // Wait, bufferedReader().readText() reads until end of stream. For ZipInputStream, that's until current entry ends?
                        // Actually, ZipInputStream behaves like a stream for the current entry.
                        // However, strictly speaking, we should be careful. `readText` closes the reader, which closes the underlying stream.
                        // We must NOT close the ZipInputStream here.

                        // Safer approach: read bytes for this entry
                        val bytes = zipIn.readBytes() // This reads until current entry EOF.
                        val jsonContent = String(bytes)
                        val config = gson.fromJson(jsonContent, AppConfig::class.java)
                        restoreAppConfig(config)
                    } else if (entry.name.startsWith("plugins/") && entry.name.endsWith(".json")) {
                        // Extract plugin
                        if (!pluginsDir.exists()) {
                            pluginsDir.mkdirs()
                        }
                        val fileName = File(entry.name).name // Get just filename, ignore path components if any
                        val outFile = File(pluginsDir, fileName)
                        outFile.outputStream().use { output ->
                            zipIn.copyTo(output)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
    }

    private suspend fun createAppConfig(): AppConfig {
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

        return AppConfig(
            settings = settings,
            feeds = feeds,
            weatherLocations = weatherLocations
        )
    }

    private suspend fun restoreAppConfig(config: AppConfig) {
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
            )
            feedDao.insertFeed(feed)
        }

        // Restore Weather
        val existingWeather = weatherDao.getAllWeather().first().toMutableList()
        config.weatherLocations.forEach { weatherConfig ->
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
                dataJson = "",
                lastUpdated = 0
            )
            weatherDao.insertWeather(weather)
        }
    }
}
