package com.example.offlinebrowser

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.repository.ScraperPluginRepository
import com.example.offlinebrowser.ui.PluginAdapter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PluginsActivity : AppCompatActivity() {

    private lateinit var scraperPluginRepository: ScraperPluginRepository
    private lateinit var pluginAdapter: PluginAdapter
    private val gson = Gson()

    private val importPluginLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val json = inputStream?.bufferedReader().use { reader -> reader?.readText() }

                    if (json != null) {
                        // Validate JSON by parsing
                        val recipe = gson.fromJson(json, ScraperRecipe::class.java)

                        // Save to plugins directory
                        val fileName = "plugin_${System.currentTimeMillis()}.json"
                        val file = File(filesDir, "plugins/$fileName")
                        file.parentFile?.mkdirs()
                        file.writeText(json)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PluginsActivity, "Plugin imported successfully", Toast.LENGTH_SHORT).show()
                            loadPlugins()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PluginsActivity, "Failed to import plugin: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugins)

        scraperPluginRepository = ScraperPluginRepository(this)

        val btnImport = findViewById<Button>(R.id.btnImportPlugin)
        val rvPlugins = findViewById<RecyclerView>(R.id.rvPlugins)

        pluginAdapter = PluginAdapter(emptyList())
        rvPlugins.layoutManager = LinearLayoutManager(this)
        rvPlugins.adapter = pluginAdapter

        btnImport.setOnClickListener {
            importPluginLauncher.launch("application/json")
        }

        loadPlugins()
    }

    private fun loadPlugins() {
        lifecycleScope.launch {
            val plugins = scraperPluginRepository.loadAllRecipes()
            pluginAdapter.updateList(plugins)
        }
    }
}
