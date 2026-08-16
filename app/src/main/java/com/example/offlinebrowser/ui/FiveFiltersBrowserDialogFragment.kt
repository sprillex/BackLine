package com.example.offlinebrowser.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.OfflineBrowserApp
import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.GitHubContent
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.repository.ScraperPluginRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class FiveFiltersBrowserDialogFragment : DialogFragment() {

    private lateinit var adapter: RepositoryBrowserDialogFragment.BrowserAdapter
    private var currentPath = "https://api.github.com/repos/fivefilters/ftr-site-config/contents"
    private val pathStack = mutableListOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle("Browse FiveFilters Repository")
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        adapter = RepositoryBrowserDialogFragment.BrowserAdapter { item ->
            handleItemClick(item)
        }
        recyclerView.adapter = adapter

        loadDirectory(currentPath)

        return recyclerView
    }

    private fun handleItemClick(item: GitHubContent) {
        if (item.name == "..") {
            if (pathStack.isNotEmpty()) {
                currentPath = pathStack.removeAt(pathStack.size - 1)
                loadDirectory(currentPath)
            }
        } else if (item.type == "dir") {
            pathStack.add(currentPath)
            currentPath = item.url
            loadDirectory(currentPath)
        } else if (item.name.endsWith(".txt") && item.download_url != null) {
            downloadAndParsePlugin(item)
        }
    }

    private fun loadDirectory(url: String) {
        val repository = (requireActivity().application as OfflineBrowserApp).suggestedFeedRepository
        lifecycleScope.launch {
            try {
                val items = repository.fetchRemoteDirectory(url).toMutableList()
                if (pathStack.isNotEmpty()) {
                    items.add(0, GitHubContent("..", "", "", 0, "", "", "", null, "dir"))
                }
                adapter.submitList(items)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
                if (pathStack.isNotEmpty()) {
                    currentPath = pathStack.removeAt(pathStack.size - 1)
                }
            }
        }
    }

    private fun downloadAndParsePlugin(item: GitHubContent) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Downloading ${item.name}...", Toast.LENGTH_SHORT).show()
                }

                val content = URL(item.download_url).readText()
                val lines = content.split("\n")

                val bodyPaths = mutableListOf<String>()
                val titlePaths = mutableListOf<String>()
                val removeSelectors = mutableListOf<String>()

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#") || trimmed.isEmpty()) continue

                    if (trimmed.startsWith("body:")) {
                        val path = trimmed.substringAfter("body:").trim()
                        if (path.isNotEmpty()) bodyPaths.add(path)
                    } else if (trimmed.startsWith("title:")) {
                        val path = trimmed.substringAfter("title:").trim()
                        if (path.isNotEmpty()) titlePaths.add(path)
                    } else if (trimmed.startsWith("strip:")) {
                        val path = trimmed.substringAfter("strip:").trim()
                        if (path.isNotEmpty()) removeSelectors.add(path)
                    } else if (trimmed.startsWith("strip_id_or_class:")) {
                        val idOrClass = trimmed.substringAfter("strip_id_or_class:").trim()
                        if (idOrClass.isNotEmpty()) {
                            // Translate to XPath containing class or id
                            removeSelectors.add("//*[contains(@class, '$idOrClass') or @id='$idOrClass']")
                        }
                    }
                }

                if (bodyPaths.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No body selector found in ${item.name}", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val domainName = item.name.removeSuffix(".txt")
                val safeDomain = domainName.replace(".", "\\.")

                val recipe = ScraperRecipe(
                    domainPattern = ".*$safeDomain.*",
                    strategy = ExtractionStrategy.XPATH,
                    targetIdentifier = "",
                    contentPath = bodyPaths,
                    titlePath = if (titlePaths.isNotEmpty()) titlePaths else null,
                    injectRssImage = true,
                    removeSelectors = if (removeSelectors.isNotEmpty()) removeSelectors else null,
                    sourceName = domainName
                )

                val gson = Gson()
                val json = gson.toJson(recipe)

                val fileName = "plugin_${System.currentTimeMillis()}.json"
                val file = File(requireContext().filesDir, "plugins/$fileName")
                file.parentFile?.mkdirs()
                file.writeText(json)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Plugin imported successfully!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.setFragmentResult("PLUGIN_IMPORTED", Bundle())
                    dismiss()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error downloading: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
