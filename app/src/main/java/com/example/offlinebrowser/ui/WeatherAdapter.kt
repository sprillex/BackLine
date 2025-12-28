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

class WeatherAdapter(
    private val useImperial: Boolean,
    private val onRefresh: (Weather) -> Unit,
    private val onEdit: (Weather) -> Unit
) : ListAdapter<Weather, WeatherAdapter.WeatherViewHolder>(WeatherDiffCallback()) {

    class WeatherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvCoords: TextView = itemView.findViewById(R.id.tvCoords)
        val tvData: TextView = itemView.findViewById(R.id.tvData)
        val rvHourly: RecyclerView = itemView.findViewById(R.id.rvHourly)
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

                if (response?.hourly != null) {
                    val hourlyItems = mutableListOf<HourlyItem>()
                    val times = response.hourly.time
                    val temps = response.hourly.temperature2m
                    val codes = response.hourly.weathercode

                    for (i in times.indices) {
                        if (i < temps.size && i < codes.size) {
                            hourlyItems.add(HourlyItem(times[i], temps[i], codes[i]))
                        }
                    }

                    val hourlyAdapter = HourlyAdapter(hourlyItems, useImperial)
                    holder.rvHourly.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                        holder.itemView.context,
                        androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    holder.rvHourly.adapter = hourlyAdapter
                    holder.rvHourly.visibility = View.VISIBLE
                } else {
                    holder.rvHourly.visibility = View.GONE
                }

            } catch (e: Exception) {
                 e.printStackTrace()
                 holder.tvData.text = "Error parsing data"
                 holder.rvHourly.visibility = View.GONE
            }
        } else {
             holder.tvData.text = "No data"
             holder.rvHourly.visibility = View.GONE
        }

        holder.btnRefresh.setOnClickListener { onRefresh(weather) }
        holder.btnEdit.setOnClickListener { onEdit(weather) }
    }
}

class WeatherDiffCallback : DiffUtil.ItemCallback<Weather>() {
    override fun areItemsTheSame(oldItem: Weather, newItem: Weather): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Weather, newItem: Weather): Boolean = oldItem == newItem
}
