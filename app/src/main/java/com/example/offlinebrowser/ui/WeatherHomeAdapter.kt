package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.Weather
import com.example.offlinebrowser.data.model.WeatherResponse
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.google.gson.Gson

class WeatherHomeAdapter(
    private val preferencesRepository: PreferencesRepository,
    private val onLongClick: () -> Unit
) : ListAdapter<Weather, WeatherHomeAdapter.WeatherViewHolder>(WeatherHomeDiffCallback()) {

    private val gson = Gson()

    inner class WeatherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTemp: TextView? = view.findViewById(R.id.tv_temp)
        val tvCondition: TextView? = view.findViewById(R.id.tv_condition)
        val tvCity: TextView? = view.findViewById(R.id.tv_city)
        val tvHighLow: TextView? = view.findViewById(R.id.tv_high_low)

        init {
            view.setOnLongClickListener {
                onLongClick()
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        try {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_weather_home, parent, false)
            return WeatherViewHolder(view)
        } catch (e: Throwable) {
            e.printStackTrace()
            // Return a dummy view to avoid crash if inflation fails, though this is catastrophic
            val dummyView = View(parent.context)
            return WeatherViewHolder(dummyView)
        }
    }

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        try {
            val weather = getItem(position)
            holder.tvCity?.text = weather.locationName

            // If data is empty or invalid, show placeholder
            val json = weather.dataJson ?: ""
            val response = if (json.isNotEmpty()) gson.fromJson(json, WeatherResponse::class.java) else null

            if (response?.currentWeather != null) {
                val temp = response.currentWeather.temperature
                val unit = if (preferencesRepository.weatherUnits == "imperial") "°F" else "°C"

                holder.tvTemp?.text = "$temp$unit"

                holder.tvCondition?.text = when(response.currentWeather.weathercode) {
                    0 -> "Clear"
                    1, 2, 3 -> "Partly Cloudy"
                    45, 48 -> "Fog"
                    51, 53, 55 -> "Drizzle"
                    61, 63, 65 -> "Rain"
                    71, 73, 75 -> "Snow"
                    else -> "Unknown"
                }
            } else {
                holder.tvTemp?.text = "--"
                holder.tvCondition?.text = "N/A"
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            holder.tvTemp?.text = "--"
            holder.tvCondition?.text = "Error"
        }
    }
}

class WeatherHomeDiffCallback : DiffUtil.ItemCallback<Weather>() {
    override fun areItemsTheSame(oldItem: Weather, newItem: Weather): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Weather, newItem: Weather): Boolean = oldItem == newItem
}
