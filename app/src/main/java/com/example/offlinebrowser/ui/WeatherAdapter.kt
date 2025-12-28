package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.Weather
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherAdapter(
    private val useImperial: Boolean,
    private val onRefresh: (Weather) -> Unit,
    private val onEdit: (Weather) -> Unit
) : ListAdapter<Weather, WeatherAdapter.WeatherViewHolder>(WeatherDiffCallback()) {

    class WeatherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvCoords: TextView = itemView.findViewById(R.id.tvCoords)
        val tvData: TextView = itemView.findViewById(R.id.tvData)
        val rvForecast: RecyclerView = itemView.findViewById(R.id.rvForecast)
        val btnRefresh: Button = itemView.findViewById(R.id.btnRefresh)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_weather, parent, false)
        return WeatherViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        val weather = getItem(position)
        holder.tvName.text = weather.locationName
        holder.tvCoords.text = "${weather.latitude}, ${weather.longitude}"

        if (weather.dataJson.isNotEmpty()) {
            try {
                val gson = com.google.gson.Gson()
                val response = gson.fromJson(weather.dataJson, com.example.offlinebrowser.data.model.WeatherResponse::class.java)

                // Current Weather Display
                if (response?.currentWeather != null) {
                    val rawTemp = response.currentWeather.temperature
                    if (useImperial) {
                        val fahrenheit = (rawTemp * 9 / 5) + 32
                        holder.tvData.text = String.format("Temp: %.1f°F", fahrenheit)
                    } else {
                        holder.tvData.text = "Temp: $rawTemp°C"
                    }
                } else {
                    holder.tvData.text = "Data cached (No current weather)"
                }

                // Forecast Display
                if (response?.daily != null && response.hourly != null) {
                    val forecastItems = mutableListOf<ForecastItem>()
                    val dailyTimes = response.daily.time
                    val maxTemps = response.daily.temperature2mMax
                    val minTemps = response.daily.temperature2mMin
                    val dailyCodes = response.daily.weathercode

                    val hourlyTimes = response.hourly.time
                    val hourlyTemps = response.hourly.temperature2m
                    val hourlyCodes = response.hourly.weathercode

                    for (i in dailyTimes.indices) {
                        if (i < maxTemps.size && i < minTemps.size && i < dailyCodes.size) {
                            val dateStr = dailyTimes[i]
                            val dayName = try {
                                val date = LocalDate.parse(dateStr)
                                date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                            } catch (e: Exception) {
                                dateStr
                            }

                            val condition = getWeatherCondition(dailyCodes[i])

                            // Slice hourly data for this day (24 hours)
                            val dayHourlyItems = mutableListOf<HourlyItem>()
                            val startIndex = i * 24
                            val endIndex = startIndex + 24

                            for (h in startIndex until endIndex) {
                                if (h < hourlyTimes.size && h < hourlyTemps.size && h < hourlyCodes.size) {
                                    dayHourlyItems.add(HourlyItem(hourlyTimes[h], hourlyTemps[h], hourlyCodes[h]))
                                }
                            }

                            forecastItems.add(ForecastItem(
                                day = dayName,
                                condition = condition,
                                maxTemp = maxTemps[i],
                                minTemp = minTemps[i],
                                code = dailyCodes[i],
                                hourlyItems = dayHourlyItems
                            ))
                        }
                    }

                    val forecastAdapter = ForecastAdapter(forecastItems, useImperial)
                    holder.rvForecast.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                        holder.itemView.context,
                        androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
                        false
                    )
                    holder.rvForecast.adapter = forecastAdapter
                    holder.rvForecast.visibility = View.VISIBLE
                } else {
                    holder.rvForecast.visibility = View.GONE
                }

            } catch (e: Exception) {
                 e.printStackTrace()
                 holder.tvData.text = "Error parsing data: ${e.message}"
                 holder.rvForecast.visibility = View.GONE
            }
        } else {
             holder.tvData.text = "No data"
             holder.rvForecast.visibility = View.GONE
        }

        holder.btnRefresh.setOnClickListener { onRefresh(weather) }
        holder.btnEdit.setOnClickListener { onEdit(weather) }
    }

    private fun getWeatherCondition(code: Int): String {
        return when(code) {
            0 -> "Sunny" // Clear sky
            1, 2, 3 -> "Partly Cloudy" // Mainly clear, partly cloudy, and overcast
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }
}

class WeatherDiffCallback : DiffUtil.ItemCallback<Weather>() {
    override fun areItemsTheSame(oldItem: Weather, newItem: Weather): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Weather, newItem: Weather): Boolean = oldItem == newItem
}
