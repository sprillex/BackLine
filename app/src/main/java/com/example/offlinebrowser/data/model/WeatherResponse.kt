package com.example.offlinebrowser.data.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeather?
)

data class CurrentWeather(
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("windspeed") val windspeed: Double,
    @SerializedName("winddirection") val winddirection: Double,
    @SerializedName("weathercode") val weathercode: Int,
    @SerializedName("time") val time: String
)
