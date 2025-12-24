package com.example.offlinebrowser.data.network

import retrofit2.http.GET
import retrofit2.http.Query

// Using Open-Meteo API as it is free and requires no key
interface WeatherService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): String // Returning raw JSON string for simplicity in storage
}
