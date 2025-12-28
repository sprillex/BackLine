package com.example.offlinebrowser.data.model

import com.google.gson.annotations.SerializedName

data class AppConfig(
    @SerializedName("settings")
    val settings: SettingsConfig,
    @SerializedName("feeds")
    val feeds: List<FeedConfig>,
    @SerializedName("weather_locations")
    val weatherLocations: List<WeatherConfig>
)

data class SettingsConfig(
    @SerializedName("wifi_only")
    val wifiOnly: Boolean,
    @SerializedName("refresh_interval")
    val refreshInterval: Long,
    @SerializedName("feed_limit_count")
    val feedLimitCount: Int,
    @SerializedName("feed_limit_days")
    val feedLimitDays: Int,
    @SerializedName("weather_units")
    val weatherUnits: String
)

data class FeedConfig(
    @SerializedName("url")
    val url: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("type")
    val type: String, // FeedType enum name
    @SerializedName("download_limit")
    val downloadLimit: Int,
    @SerializedName("category")
    val category: String?
)

data class WeatherConfig(
    @SerializedName("location_name")
    val locationName: String,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double
)
