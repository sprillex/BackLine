package com.example.offlinebrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.ui.ArticleAdapter
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class ArticleListActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var articleAdapter: ArticleAdapter
    private var feedId: Int = -1
    private var category: String? = null

    // UI Elements
    private lateinit var statusDot: View
    private lateinit var ssidText: TextView
    private lateinit var pillContainer: LinearLayout
    private lateinit var statusContainer: LinearLayout

    private var currentCategories: List<String> = emptyList()
    private var currentWeatherCount = 0

    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val wifiManager by lazy { applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
             updateWifiStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_list)

        feedId = intent.getIntExtra("FEED_ID", -1)
        category = intent.getStringExtra("EXTRA_CATEGORY")

        val rvArticles = findViewById<RecyclerView>(R.id.rvArticles)
        statusDot = findViewById(R.id.status_dot)
        ssidText = findViewById(R.id.ssid_text)
        pillContainer = findViewById(R.id.pill_container)
        statusContainer = findViewById(R.id.status_container)

        articleAdapter = ArticleAdapter(
            onArticleClick = { article ->
                if (article.content.isNotEmpty()) {
                    val intent = Intent(this, ArticleViewerActivity::class.java)
                    intent.putExtra("ARTICLE_ID", article.id)
                    startActivity(intent)
                }
            },
            onDownloadClick = { article -> viewModel.downloadArticle(article) }
        )

        rvArticles.layoutManager = LinearLayoutManager(this)
        rvArticles.adapter = articleAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    if (category != null) {
                        viewModel.getArticlesByCategory(category!!).collect { articles ->
                            articleAdapter.submitList(articles)
                        }
                    } else {
                        viewModel.getArticlesForFeed(feedId).collect { articles ->
                            articleAdapter.submitList(articles)
                        }
                    }
                }
                launch {
                    viewModel.weatherLocations.collect { weatherList ->
                        currentWeatherCount = weatherList.size
                        updatePills()
                    }
                }
                launch {
                    viewModel.categories.collect { categoryList ->
                        currentCategories = categoryList
                        updatePills()
                    }
                }
            }
        }

        setupBottomNav()
        setupStatus()
        registerNetworkCallback()
    }

    override fun onResume() {
        super.onResume()
        updateWifiStatus()
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        // Current activity is content
        findViewById<View>(R.id.nav_settings).setOnClickListener {
             startActivity(Intent(this, FeedSettingsActivity::class.java))
        }
    }

    private fun setupStatus() {
        statusContainer.setOnLongClickListener {
            val ssid = ssidText.text.toString()
            if (ssid != "Local" && ssid != "<unknown ssid>" && ssid != "WiFi") {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("EXTRA_ADD_SSID", ssid)
                startActivity(intent)
            } else {
                 startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
    }

    private fun updatePills() {
        pillContainer.removeAllViews()
        val activePill = category ?: "All" // If viewing a category, that's active. If viewing a feed ID, maybe "All"? Or just nothing?
        // Actually, if we are in ArticleListActivity via "Content" tab, we might want to default to "All" articles view logic if implemented.
        // But currently ArticleListActivity requires FeedID or Category.
        // If opened from Nav -> Content, we need to handle "All articles".
        // Let's assume for now we just show pills for navigation purposes.

        // "All" Pill
        addPill("All", category == null && feedId == -1) {
            // Logic to clear filters? Or go to Home?
            // "All" usually means Home or All Content.
            // If user clicks "All", maybe we should clear category filter if we support "All Articles" mode.
            // For now, let's redirect to Home as "All" implies the dashboard state in the mockups.
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // "Weather" Pill
        if (currentWeatherCount > 0) {
            addPill("Weather", false) {
                 // Go to Home and expand Weather?
                 val intent = Intent(this, HomeActivity::class.java)
                 intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                 // We might need an extra to tell Home to open Weather
                 // intent.putExtra("OPEN_WEATHER", true) // Not implemented in Home yet, but good idea.
                 startActivity(intent)
                 finish()
            }
        }

        // Category Pills
        currentCategories.forEach { cat ->
            addPill(cat, category == cat) {
                 if (category != cat) {
                     val intent = Intent(this, ArticleListActivity::class.java)
                     intent.putExtra("EXTRA_CATEGORY", cat)
                     startActivity(intent)
                     // Don't finish, stack it? Or finish to avoid depth?
                     // Standard pattern: clear top if same activity type
                     finish()
                 }
            }
        }
    }

    private fun addPill(text: String, isActive: Boolean, onClick: () -> Unit) {
        val textView = TextView(this)
        textView.text = text
        textView.textSize = 13f

        val paddingHorizontal = (16 * resources.displayMetrics.density).toInt()
        val paddingVertical = (8 * resources.displayMetrics.density).toInt()
        val marginEnd = (10 * resources.displayMetrics.density).toInt()

        textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = marginEnd
        textView.layoutParams = params

        if (isActive) {
            textView.setTextColor(ContextCompat.getColor(this, R.color.bg_color))
            textView.setBackgroundResource(R.drawable.bg_pill_active)
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            textView.setBackgroundResource(R.drawable.bg_pill)
            textView.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        textView.setOnClickListener { onClick() }
        pillContainer.addView(textView)
    }

    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { updateWifiStatus() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { updateWifiStatus() }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                runOnUiThread { updateWifiStatus() }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun updateWifiStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isWifi) {
            statusDot.setBackgroundResource(R.drawable.status_dot_green)
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                 val info = wifiManager.connectionInfo
                 val ssid = info.ssid.trim('"')
                 ssidText.text = if (ssid == "<unknown ssid>") "WiFi" else ssid
             } else {
                 ssidText.text = "WiFi"
             }
        } else {
            statusDot.setBackgroundResource(R.drawable.status_dot_red)
            ssidText.text = "Local"
        }
    }
}
