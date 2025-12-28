package com.example.offlinebrowser.data.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("current_weather") val currentWeather: CurrentWeather?,
    @SerializedName("hourly") val hourly: Hourly?
)

data class CurrentWeather(
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("windspeed") val windspeed: Double,
    @SerializedName("winddirection") val winddirection: Double,
    @SerializedName("weathercode") val weathercode: Int,
    @SerializedName("time") val time: String
)

data class Hourly(
    @SerializedName("time") val time: List<String>,
    @SerializedName("temperature_2m") val temperature2m: List<Double>,
    @SerializedName("weathercode") val weathercode: List<Int>
)
