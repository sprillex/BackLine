package com.example.offlinebrowser.ui.plugincreator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.ExtractionStrategy
import com.example.offlinebrowser.data.model.ScraperRecipe
import com.example.offlinebrowser.data.network.HtmlDownloader
import com.example.offlinebrowser.data.repository.ScraperPluginRepository
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.net.URL

class PluginCreatorActivity : AppCompatActivity() {

    private lateinit var etDomainPattern: TextInputEditText
    private lateinit var etSourceName: TextInputEditText
    private lateinit var etTitleSelector: TextInputEditText
    private lateinit var etContentSelector: TextInputEditText
    private lateinit var etRemoveSelectors: TextInputEditText
    private lateinit var cbInjectRssImage: CheckBox

    private lateinit var webViewPreview: WebView
    private lateinit var previewContainer: View
    private lateinit var rvElements: RecyclerView

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var scraperPluginRepository: ScraperPluginRepository
    private val htmlDownloader = HtmlDownloader()

    private var testUrl: String = ""
    private var htmlCache: String? = null

    private val discoveredElements = mutableListOf<DiscoveredElement>()
    private lateinit var elementAdapter: DiscoveredElementAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugin_creator)

        scraperPluginRepository = ScraperPluginRepository(this)

        etDomainPattern = findViewById(R.id.etDomainPattern)
        etSourceName = findViewById(R.id.etSourceName)
        etTitleSelector = findViewById(R.id.etTitleSelector)
        etContentSelector = findViewById(R.id.etContentSelector)
        etRemoveSelectors = findViewById(R.id.etRemoveSelectors)
        cbInjectRssImage = findViewById(R.id.cbInjectRssImage)

        webViewPreview = findViewById(R.id.webViewPreview)
        previewContainer = findViewById(R.id.previewContainer)
        rvElements = findViewById(R.id.rvElements)

        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnShare = findViewById<Button>(R.id.btnShare)
        val btnClosePreview = findViewById<Button>(R.id.btnClosePreview)

        testUrl = intent.getStringExtra("ARTICLE_URL") ?: ""

        // Auto-fill domain pattern
        if (testUrl.isNotEmpty()) {
            try {
                val host = URL(testUrl).host
                val domain = if (host.startsWith("www.")) host.substring(4) else host
                etDomainPattern.setText(domain.replace(".", "\\."))
                etSourceName.setText(domain.substringBeforeLast(".").replaceFirstChar { it.uppercase() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Check if editing an existing recipe
        val existingJson = intent.getStringExtra("EXISTING_RECIPE_JSON")
        if (existingJson != null) {
            try {
                val recipe = gson.fromJson(existingJson, ScraperRecipe::class.java)
                etDomainPattern.setText(recipe.domainPattern)
                etSourceName.setText(recipe.sourceName ?: "")
                etTitleSelector.setText(recipe.titlePath ?: "")
                etContentSelector.setText(recipe.contentPath)
                etRemoveSelectors.setText(recipe.removeSelectors?.joinToString(", ") ?: "")
                cbInjectRssImage.isChecked = recipe.injectRssImage
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        elementAdapter = DiscoveredElementAdapter(discoveredElements) { element ->
            // On click, set it as the content selector
            etContentSelector.setText(element.selector)
            Toast.makeText(this, "Set as Content Selector", Toast.LENGTH_SHORT).show()
        }
        rvElements.layoutManager = LinearLayoutManager(this)
        rvElements.adapter = elementAdapter

        loadAndParseHtml()

        btnTest.setOnClickListener {
            runTest()
        }

        btnSave.setOnClickListener {
            savePlugin()
        }

        btnShare.setOnClickListener {
            sharePlugin()
        }

        btnClosePreview.setOnClickListener {
            previewContainer.visibility = View.GONE
        }
    }

    private fun loadAndParseHtml() {
        if (testUrl.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(testUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                htmlCache = doc.outerHtml()

                val elements = findLikelyContainers(doc)

                withContext(Dispatchers.Main) {
                    discoveredElements.clear()
                    discoveredElements.addAll(elements)
                    elementAdapter.notifyDataSetChanged()

                    // If we found a likely title, set it
                    if (etTitleSelector.text.isNullOrEmpty()) {
                        val h1 = doc.select("h1").first()
                        if (h1 != null) {
                            val classes = h1.classNames().joinToString(".")
                            if (classes.isNotEmpty()) {
                                etTitleSelector.setText("h1.$classes")
                            } else {
                                etTitleSelector.setText("h1")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun findLikelyContainers(doc: org.jsoup.nodes.Document): List<DiscoveredElement> {
        val list = mutableListOf<DiscoveredElement>()

        // Find elements with a lot of text (likely the article body)
        // Usually article, main, div with specific classes
        val candidates = doc.select("article, div[class*=article], div[class*=content], div[class*=body], div[class*=story]")

        for (element in candidates) {
            val text = element.text()
            if (text.length > 500) { // arbitrary threshold for "a lot of text"
                val selector = buildCssSelector(element)
                if (selector.isNotEmpty()) {
                    list.add(DiscoveredElement(selector, text.take(150) + "..."))
                }
            }
        }

        // Sort by length of text (descending)
        return list.sortedByDescending { it.previewText.length }.distinctBy { it.selector }
    }

    private fun buildCssSelector(element: Element): String {
        val tag = element.tagName()
        val classes = element.classNames().joinToString(".")
        val id = element.id()

        return when {
            id.isNotEmpty() -> "$tag#$id"
            classes.isNotEmpty() -> "$tag.$classes"
            else -> tag
        }
    }

    private fun buildRecipe(): ScraperRecipe? {
        val domain = etDomainPattern.text.toString().trim()
        val contentSelector = etContentSelector.text.toString().trim()

        if (domain.isEmpty() || contentSelector.isEmpty()) {
            Toast.makeText(this, "Domain pattern and Content Selector are required", Toast.LENGTH_SHORT).show()
            return null
        }

        val titleSelector = etTitleSelector.text.toString().trim().takeIf { it.isNotEmpty() }
        val sourceName = etSourceName.text.toString().trim().takeIf { it.isNotEmpty() }
        val removeSelectorsStr = etRemoveSelectors.text.toString().trim()
        val removeSelectors = if (removeSelectorsStr.isNotEmpty()) removeSelectorsStr.split(",").map { it.trim() } else null

        return ScraperRecipe(
            domainPattern = domain,
            strategy = ExtractionStrategy.CSS_SELECTOR,
            targetIdentifier = "", // Not used for CSS_SELECTOR but required by data class
            contentPath = contentSelector,
            titlePath = titleSelector,
            injectRssImage = cbInjectRssImage.isChecked,
            removeSelectors = removeSelectors,
            sourceName = sourceName
        )
    }

    private fun runTest() {
        val recipe = buildRecipe() ?: return
        if (testUrl.isEmpty() || htmlCache == null) {
            Toast.makeText(this, "No test URL provided or HTML not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        previewContainer.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // We need to use ScraperEngine to test it
                val testEngine = com.example.offlinebrowser.data.network.ScraperEngine()
                testEngine.loadRecipes(listOf(recipe))

                val resultHtml = testEngine.process(testUrl, htmlCache!!, null)

                withContext(Dispatchers.Main) {
                    if (resultHtml != null) {
                        webViewPreview.loadDataWithBaseURL(null, resultHtml, "text/html", "UTF-8", null)
                    } else {
                        webViewPreview.loadDataWithBaseURL(null, "<html><body><h3>Failed to extract content</h3></body></html>", "text/html", "UTF-8", null)
                        Toast.makeText(this@PluginCreatorActivity, "Extraction failed. Check your selectors.", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PluginCreatorActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun savePlugin() {
        val recipe = buildRecipe() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = gson.toJson(recipe)
                val domainName = recipe.domainPattern.replace("\\.", ".").replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val fileName = "${domainName}.json"
                val file = File(filesDir, "plugins/$fileName")
                file.parentFile?.mkdirs()
                file.writeText(json)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PluginCreatorActivity, "Plugin saved to plugins/$fileName", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PluginCreatorActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sharePlugin() {
        val recipe = buildRecipe() ?: return

        try {
            val json = gson.toJson(recipe)
            val domainName = recipe.domainPattern.replace("\\.", ".").replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val fileName = "${domainName}.json"

            // Write to a temporary file in cache to share
            val tempFile = File(cacheDir, fileName)
            tempFile.writeText(json)

            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", tempFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Plugin"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

data class DiscoveredElement(val selector: String, val previewText: String)

class DiscoveredElementAdapter(
    private val elements: List<DiscoveredElement>,
    private val onClick: (DiscoveredElement) -> Unit
) : RecyclerView.Adapter<DiscoveredElementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSelector: TextView = view.findViewById(R.id.tvSelector)
        val tvPreviewText: TextView = view.findViewById(R.id.tvPreviewText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_discovered_element, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = elements[position]
        holder.tvSelector.text = item.selector
        holder.tvPreviewText.text = item.previewText
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = elements.size
}
