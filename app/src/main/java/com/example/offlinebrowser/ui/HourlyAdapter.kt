package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R

data class HourlyItem(val time: String, val temp: Double, val code: Int)

class HourlyAdapter(private val items: List<HourlyItem>, private val useImperial: Boolean) : RecyclerView.Adapter<HourlyAdapter.HourlyViewHolder>() {

    class HourlyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHour: TextView = view.findViewById(R.id.tv_hour)
        val tvTemp: TextView = view.findViewById(R.id.tv_hourly_temp)
        val tvCondition: TextView = view.findViewById(R.id.tv_hourly_condition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourlyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_weather_hourly, parent, false)
        return HourlyViewHolder(view)
    }

    override fun onBindViewHolder(holder: HourlyViewHolder, position: Int) {
        val item = items[position]

        // Simple time parsing (assuming ISO 2025-12-28T00:00)
        // Extract hour part "T00:00" -> "00:00"
        val timeParts = item.time.split("T")
        val hour = if (timeParts.size > 1) timeParts[1] else item.time

        holder.tvHour.text = hour

        if (useImperial) {
             val fahrenheit = (item.temp * 9 / 5) + 32
             holder.tvTemp.text = String.format("%.0f°", fahrenheit)
        } else {
             holder.tvTemp.text = "${item.temp}°"
        }

        holder.tvCondition.text = getWeatherCondition(item.code)
    }

    override fun getItemCount(): Int = items.size

    private fun getWeatherCondition(code: Int): String {
        return when(code) {
            0 -> "Clear"
            1, 2, 3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            else -> ""
        }
    }
}
