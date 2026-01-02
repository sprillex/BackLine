package com.example.offlinebrowser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinebrowser.data.model.Feed
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class EditFeedActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var feedId: Int = -1
    private var currentFeed: Feed? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_feed)

        feedId = intent.getIntExtra("FEED_ID", -1)
        if (feedId == -1) {
            finish()
            return
        }

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etDownloadLimit = findViewById<EditText>(R.id.etDownloadLimit)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        lifecycleScope.launch {
            currentFeed = viewModel.getFeedById(feedId)

            if (currentFeed != null) {
                etTitle.setText(currentFeed!!.title)
                etDownloadLimit.setText(currentFeed!!.downloadLimit.toString())
            } else {
                Toast.makeText(this@EditFeedActivity, "Feed not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnSave.setOnClickListener {
            val newTitle = etTitle.text.toString()
            val newLimitStr = etDownloadLimit.text.toString()
            val newLimit = newLimitStr.toIntOrNull() ?: 5

            if (currentFeed != null) {
                val updatedFeed = currentFeed!!.copy(
                    title = newTitle,
                    downloadLimit = newLimit
                )
                // Use lifecycleScope to wait for the job to complete
                lifecycleScope.launch {
                    viewModel.updateFeed(updatedFeed).join()

                    // Trigger sync if download limit changed
                    if (currentFeed!!.downloadLimit != updatedFeed.downloadLimit) {
                         viewModel.syncFeed(updatedFeed)
                    }

                    finish()
                }
            }
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }
}
