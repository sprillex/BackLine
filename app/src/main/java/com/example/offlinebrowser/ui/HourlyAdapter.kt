package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R

data class HourlyItem(val time: String, val temp: Double, val code: Int, val isCurrentHour: Boolean = false)

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

        // Parse ISO 2025-12-28T14:00 to 2 PM
        val formattedTime = try {
            val timeParts = item.time.split("T")
            if (timeParts.size > 1) {
                val hourMinute = timeParts[1]
                val hourInt = hourMinute.split(":")[0].toInt()

                val amPm = if (hourInt < 12) "AM" else "PM"
                val hour12 = when {
                    hourInt == 0 -> 12
                    hourInt > 12 -> hourInt - 12
                    else -> hourInt
                }
                "$hour12 $amPm"
            } else {
                item.time
            }
        } catch (e: Exception) {
            item.time
        }

        holder.tvHour.text = formattedTime

        if (useImperial) {
             val fahrenheit = (item.temp * 9 / 5) + 32
             holder.tvTemp.text = String.format("%.0f°", fahrenheit)
        } else {
             holder.tvTemp.text = "${item.temp}°"
        }

        holder.tvCondition.text = getWeatherCondition(item.code)

        if (item.isCurrentHour) {
            holder.tvHour.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.tvTemp.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.tvCondition.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.bg_pill_active_transparent))
        } else {
            holder.tvHour.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvTemp.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvCondition.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
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
