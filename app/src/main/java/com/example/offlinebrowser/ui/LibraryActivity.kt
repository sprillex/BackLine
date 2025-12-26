package com.example.offlinebrowser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import java.io.File

class LibraryActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        rvFiles = findViewById(R.id.rvFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvFiles.layoutManager = LinearLayoutManager(this)

        loadFiles()
    }

    private fun loadFiles() {
        val dir = getExternalFilesDir(null)
        val files = dir?.listFiles { file ->
            file.isFile && (file.name.endsWith(".zim") || file.name.endsWith(".iso"))
        }?.sortedBy { it.name } ?: emptyList()

        if (files.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
            rvFiles.adapter = FileAdapter(files) { file ->
                openFile(file)
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/x-zim")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Try generic open
                val genericIntent = Intent(Intent.ACTION_VIEW)
                genericIntent.setDataAndType(uri, "*/*")
                genericIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                try {
                    startActivity(Intent.createChooser(genericIntent, "Open with..."))
                } catch (e2: Exception) {
                     Toast.makeText(this, "No app found to open this file.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    class FileAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_library_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvFileName.text = file.name
            holder.tvFileSize.text = formatSize(file.length())
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = files.size

        private fun formatSize(bytes: Long): String {
            val kb = bytes / 1024
            val mb = kb / 1024
            val gb = mb / 1024
            return if (gb > 0) "$gb GB" else if (mb > 0) "$mb MB" else "$kb KB"
        }
    }
}
