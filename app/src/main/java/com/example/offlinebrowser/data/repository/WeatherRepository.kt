package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.local.WeatherDao
import com.example.offlinebrowser.data.model.Weather
import com.example.offlinebrowser.data.network.WeatherService
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

class WeatherRepository(private val weatherDao: WeatherDao) {

    private val weatherService: WeatherService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        weatherService = retrofit.create(WeatherService::class.java)
    }

    val allWeather: Flow<List<Weather>> = weatherDao.getAllWeather()

    suspend fun updateWeather(weather: Weather, forecastDays: Int = 7) {
        try {
            val json = weatherService.getWeather(
                latitude = weather.latitude,
                longitude = weather.longitude,
                forecastDays = forecastDays
            )
            val updatedWeather = weather.copy(
                dataJson = json,
                lastUpdated = System.currentTimeMillis()
            )
            weatherDao.insertWeather(updatedWeather)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addLocation(name: String, lat: Double, lon: Double) {
        val weather = Weather(
            locationName = name,
            latitude = lat,
            longitude = lon,
            dataJson = "",
            lastUpdated = 0
        )
        val id = weatherDao.insertWeather(weather)
        // Immediately update
        updateWeather(weather.copy(id = id.toInt()))
    }
}
