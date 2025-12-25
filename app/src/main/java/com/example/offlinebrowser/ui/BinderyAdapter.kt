package com.example.offlinebrowser.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R

data class BinderyModule(
    val title: String,
    val description: String,
    val downloadUrl: String
)

class BinderyAdapter(
    private val modules: List<BinderyModule>,
    private val onDownloadClick: (BinderyModule) -> Unit
) : RecyclerView.Adapter<BinderyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val btnDownload: Button = view.findViewById(R.id.btnDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bindery_module, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val module = modules[position]
        holder.tvTitle.text = module.title
        holder.tvDescription.text = module.description
        holder.btnDownload.setOnClickListener { onDownloadClick(module) }
    }

    override fun getItemCount() = modules.size
}
