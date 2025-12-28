package com.example.offlinebrowser.data.network

import retrofit2.http.GET
import retrofit2.http.Query

// Using Open-Meteo API as it is free and requires no key
interface WeatherService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weathercode",
        @Query("hourly") hourly: String = "temperature_2m,weathercode",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto"
    ): String // Returning raw JSON string for simplicity in storage
}
