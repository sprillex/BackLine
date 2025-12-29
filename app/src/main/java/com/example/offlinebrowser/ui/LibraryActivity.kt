package com.example.offlinebrowser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.repository.LinkedFileRepository
import com.example.offlinebrowser.data.repository.LinkedZimFile
import com.example.offlinebrowser.workers.FileImportWorker
import java.io.File

class LibraryActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var rvDownloads: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var workManager: WorkManager
    private lateinit var btnImportFile: android.widget.Button
    private lateinit var btnLinkFile: android.widget.Button
    private lateinit var linkedFileRepository: LinkedFileRepository

    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = getFileName(it) ?: "imported_${System.currentTimeMillis()}.zim"

            val importRequest = OneTimeWorkRequestBuilder<FileImportWorker>()
                .setInputData(workDataOf(
                    "source_uri" to it.toString(),
                    "file_name" to fileName
                ))
                .addTag("download") // Reusing "download" tag to show in the existing list
                .build()

            workManager.enqueue(importRequest)
            Toast.makeText(this, "Import started...", Toast.LENGTH_SHORT).show()
        }
    }

    private val linkFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val fileName = getFileName(it) ?: "linked_file.zim"
                linkedFileRepository.addLinkedFile(LinkedZimFile(fileName, it.toString()))
                Toast.makeText(this, "File linked", Toast.LENGTH_SHORT).show()
                loadFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to link file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        rvFiles = findViewById(R.id.rvFiles)
        rvDownloads = findViewById(R.id.rvDownloads)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnImportFile = findViewById(R.id.btnImportFile)
        btnLinkFile = findViewById(R.id.btnLinkFile)
        workManager = WorkManager.getInstance(this)
        linkedFileRepository = LinkedFileRepository(this)

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvDownloads.layoutManager = LinearLayoutManager(this)

        btnImportFile.setOnClickListener {
            importFileLauncher.launch(arrayOf("application/octet-stream", "application/zim", "*/*"))
        }

        btnLinkFile.setOnClickListener {
            linkFileLauncher.launch(arrayOf("application/octet-stream", "application/zim", "*/*"))
        }

        loadFiles()
        observeDownloads()
    }

    private fun observeDownloads() {
        workManager.getWorkInfosByTagLiveData("download").observe(this) { workInfos ->
            val activeDownloads = workInfos?.filter {
                !it.state.isFinished
            } ?: emptyList()

            if (activeDownloads.isNotEmpty()) {
                rvDownloads.visibility = View.VISIBLE
                rvDownloads.adapter = DownloadAdapter(activeDownloads)
            } else {
                rvDownloads.visibility = View.GONE
            }

            // Refresh file list if any download finished
            if (workInfos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                loadFiles()
            }
        }
    }

    sealed class LibraryItem {
        data class LocalFile(val file: File) : LibraryItem()
        data class LinkedFile(val file: LinkedZimFile) : LibraryItem()
    }

    private fun loadFiles() {
        val items = mutableListOf<LibraryItem>()

        // Load local files
        val dir = getExternalFilesDir(null)
        val localFiles = dir?.listFiles { file ->
            file.isFile && (file.name.endsWith(".zim") || file.name.endsWith(".iso"))
        }?.sortedBy { it.name } ?: emptyList()
        items.addAll(localFiles.map { LibraryItem.LocalFile(it) })

        // Load linked files
        val linkedFiles = linkedFileRepository.getLinkedFiles()
        items.addAll(linkedFiles.map { LibraryItem.LinkedFile(it) })

        if (items.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
            rvFiles.adapter = FileAdapter(items,
                onClick = { item -> openItem(item) },
                onDelete = { item -> deleteItem(item) }
            )
        }
    }

    private fun deleteItem(item: LibraryItem) {
        val name = when (item) {
            is LibraryItem.LocalFile -> item.file.name
            is LibraryItem.LinkedFile -> item.file.name
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete/Remove File")
            .setMessage("Are you sure you want to remove $name?")
            .setPositiveButton("Yes") { _, _ ->
                when (item) {
                    is LibraryItem.LocalFile -> {
                        if (item.file.delete()) {
                            Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
                            loadFiles()
                        } else {
                            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is LibraryItem.LinkedFile -> {
                        linkedFileRepository.removeLinkedFile(item.file)
                        Toast.makeText(this, "Link removed", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openItem(item: LibraryItem) {
        try {
            val intent = Intent(this, ZimViewerActivity::class.java)
            when (item) {
                is LibraryItem.LocalFile -> {
                    intent.putExtra("ZIM_FILE_PATH", item.file.absolutePath)
                }
                is LibraryItem.LinkedFile -> {
                    intent.putExtra("ZIM_FILE_URI", item.file.uriString)
                    // Grant temporary read permission to the viewer activity
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    class FileAdapter(
        private val items: List<LibraryItem>,
        private val onClick: (LibraryItem) -> Unit,
        private val onDelete: (LibraryItem) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_library_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            when (item) {
                is LibraryItem.LocalFile -> {
                    holder.tvFileName.text = item.file.name
                    holder.tvFileSize.text = formatSize(item.file.length())
                }
                is LibraryItem.LinkedFile -> {
                    holder.tvFileName.text = "${item.file.name} (Linked)"
                    holder.tvFileSize.text = "External"
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount() = items.size

        private fun formatSize(bytes: Long): String {
            val kb = bytes / 1024
            val mb = kb / 1024
            val gb = mb / 1024
            return if (gb > 0) "$gb GB" else if (mb > 0) "$mb MB" else "$kb KB"
        }
    }

    class DownloadAdapter(
        private val workInfos: List<WorkInfo>
    ) : RecyclerView.Adapter<DownloadAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDownloadTitle: TextView = view.findViewById(R.id.tvDownloadTitle)
            val pbDownload: ProgressBar = view.findViewById(R.id.pbDownload)
            val tvDownloadStatus: TextView = view.findViewById(R.id.tvDownloadStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_download, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workInfo = workInfos[position]
            val progress = workInfo.progress
            val title = progress.getString("title") ?: "Downloading..."
            val progressValue = progress.getInt("progress", 0)

            holder.tvDownloadTitle.text = title
            holder.tvDownloadStatus.text = workInfo.state.name
            holder.pbDownload.progress = progressValue
            holder.pbDownload.isIndeterminate = progressValue == 0
        }

        override fun getItemCount() = workInfos.size
    }
}
