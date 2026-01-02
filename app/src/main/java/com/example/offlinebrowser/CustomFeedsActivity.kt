package com.example.offlinebrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinebrowser.data.model.FeedType
import com.example.offlinebrowser.ui.BinderyActivity
import com.example.offlinebrowser.ui.KiwixSearchActivity
import com.example.offlinebrowser.ui.LibraryActivity
import com.example.offlinebrowser.viewmodel.MainViewModel

class CustomFeedsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_feeds)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val btnAddRss = findViewById<Button>(R.id.btnAddRss)
        val btnAddMastodon = findViewById<Button>(R.id.btnAddMastodon)
        val btnAddHtml = findViewById<Button>(R.id.btnAddHtml)
        val btnBindery = findViewById<Button>(R.id.btnBindery)
        val btnKiwix = findViewById<Button>(R.id.btnKiwix)
        val btnLibrary = findViewById<Button>(R.id.btnLibrary)

        btnAddRss.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                showAddFeedDialog(url, FeedType.RSS)
                etUrl.text.clear()
            }
        }

        btnAddMastodon.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                showAddFeedDialog(url, FeedType.MASTODON)
                etUrl.text.clear()
            }
        }

        btnAddHtml.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                showAddFeedDialog(url, FeedType.HTML)
                etUrl.text.clear()
            }
        }

        btnBindery.setOnClickListener {
            val intent = Intent(this, BinderyActivity::class.java)
            startActivity(intent)
        }

        btnKiwix.setOnClickListener {
            val intent = Intent(this, KiwixSearchActivity::class.java)
            startActivity(intent)
        }

        btnLibrary.setOnClickListener {
            val intent = Intent(this, LibraryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showAddFeedDialog(url: String, type: FeedType) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_feed, null)
        val etDownloadLimit = dialogView.findViewById<EditText>(R.id.etDownloadLimit)
        val etCategory = dialogView.findViewById<EditText>(R.id.etCategory)
        val cbSyncNow = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSyncNow)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Feed Settings")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val limit = etDownloadLimit.text.toString().toIntOrNull() ?: 0
                val category = etCategory.text.toString().takeIf { it.isNotEmpty() }
                val syncNow = cbSyncNow.isChecked
                viewModel.addFeed(url, type, limit, category, syncNow)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
