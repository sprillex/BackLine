package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R

data class ForecastItem(
    val day: String,
    val condition: String,
    val maxTemp: Double,
    val minTemp: Double,
    val code: Int,
    val hourlyItems: List<HourlyItem>,
    var isExpanded: Boolean = false
)

class ForecastAdapter(
    private val items: List<ForecastItem>,
    private val useImperial: Boolean
) : RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val containerSummary: LinearLayout = view.findViewById(R.id.container_summary)
        val tvDayCondition: TextView = view.findViewById(R.id.tvDayCondition)
        val tvHighLow: TextView = view.findViewById(R.id.tvHighLow)
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val rvHourlyDaily: RecyclerView = view.findViewById(R.id.rvHourlyDaily)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_forecast, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val item = items[position]

        val displayCondition = item.condition
        holder.tvDayCondition.text = "${item.day}: $displayCondition"

        if (useImperial) {
            val maxF = (item.maxTemp * 9 / 5) + 32
            val minF = (item.minTemp * 9 / 5) + 32
            holder.tvHighLow.text = String.format("H: %.0f° L: %.0f°", maxF, minF)
        } else {
            holder.tvHighLow.text = String.format("H: %.1f° L: %.1f°", item.maxTemp, item.minTemp)
        }

        holder.tvIcon.text = getWeatherIcon(item.code)

        holder.containerSummary.setOnClickListener {
            item.isExpanded = !item.isExpanded
            notifyItemChanged(position)
        }

        if (item.isExpanded) {
            holder.rvHourlyDaily.visibility = View.VISIBLE
            val hourlyAdapter = HourlyAdapter(item.hourlyItems, useImperial)
            holder.rvHourlyDaily.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
            holder.rvHourlyDaily.adapter = hourlyAdapter
        } else {
            holder.rvHourlyDaily.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

    private fun getWeatherIcon(code: Int): String {
        return when(code) {
            0 -> "☀"
            1, 2, 3 -> "☁"
            45, 48 -> "🌫"
            51, 53, 55 -> "🌧"
            61, 63, 65 -> "🌧"
            71, 73, 75 -> "❄"
            80, 81, 82 -> "🌧"
            95, 96, 99 -> "⛈"
            else -> ""
        }
    }
}
