package com.example.offlinebrowser.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.KiwixBook
import com.example.offlinebrowser.data.repository.KiwixRepository
import com.example.offlinebrowser.workers.ZimDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KiwixSearchActivity : AppCompatActivity() {

    private lateinit var etSearchTerm: EditText
    private lateinit var btnSearch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var rvKiwixResults: RecyclerView
    private lateinit var adapter: KiwixAdapter
    private val repository = KiwixRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kiwix_search)

        etSearchTerm = findViewById(R.id.etSearchTerm)
        btnSearch = findViewById(R.id.btnSearch)
        progressBar = findViewById(R.id.progressBar)
        rvKiwixResults = findViewById(R.id.rvKiwixResults)

        rvKiwixResults.layoutManager = LinearLayoutManager(this)
        adapter = KiwixAdapter(onDownloadClick = { book -> downloadBook(book) })
        rvKiwixResults.adapter = adapter

        btnSearch.setOnClickListener {
            performSearch()
        }

        etSearchTerm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val query = etSearchTerm.text.toString()
        if (query.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        rvKiwixResults.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val result = repository.search(query)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                rvKiwixResults.visibility = View.VISIBLE

                result.fold(
                    onSuccess = { books ->
                        adapter.submitList(books)
                        if (books.isEmpty()) {
                            Toast.makeText(this@KiwixSearchActivity, "No results found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(this@KiwixSearchActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                        adapter.submitList(emptyList())
                    }
                )
            }
        }
    }

    private fun downloadBook(book: KiwixBook) {
        val data = Data.Builder()
            .putString("url", book.downloadUrl)
            .putString("title", book.title)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<ZimDownloadWorker>()
            .setInputData(data)
            .addTag("download")
            .build()

        WorkManager.getInstance(this).enqueue(downloadWork)
        Toast.makeText(this, "Download queued...", Toast.LENGTH_SHORT).show()
    }
}
