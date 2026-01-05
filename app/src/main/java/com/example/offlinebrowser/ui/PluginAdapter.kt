package com.example.offlinebrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.ScraperRecipe

class PluginAdapter(private var plugins: List<ScraperRecipe>) : RecyclerView.Adapter<PluginAdapter.PluginViewHolder>() {

    class PluginViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDomain: TextView = view.findViewById(R.id.tvDomain)
        val tvStrategy: TextView = view.findViewById(R.id.tvStrategy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plugin, parent, false)
        return PluginViewHolder(view)
    }

    override fun onBindViewHolder(holder: PluginViewHolder, position: Int) {
        val plugin = plugins[position]
        holder.tvDomain.text = "Domain: ${plugin.domainPattern}"
        holder.tvStrategy.text = "Strategy: ${plugin.strategy}"
    }

    override fun getItemCount() = plugins.size

    fun updateList(newPlugins: List<ScraperRecipe>) {
        plugins = newPlugins
        notifyDataSetChanged()
    }
}
