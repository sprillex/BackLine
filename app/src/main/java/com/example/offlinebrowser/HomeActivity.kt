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
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.viewmodel.MainViewModel
import com.example.offlinebrowser.ui.ArticleAdapter
import com.example.offlinebrowser.ui.CategoryAdapter
import com.example.offlinebrowser.ui.WeatherHomeAdapter
import com.example.offlinebrowser.data.model.Weather
import com.example.offlinebrowser.data.model.ArticleListItem
import com.example.offlinebrowser.ui.HourlyAdapter
import com.example.offlinebrowser.ui.HourlyItem
import com.example.offlinebrowser.ui.ForecastAdapter
import com.example.offlinebrowser.ui.ForecastItem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val preferencesRepository by lazy { PreferencesRepository(this) }

    private lateinit var statusDot: View
    private lateinit var ssidText: TextView
    private lateinit var weatherPager: ViewPager2
    private lateinit var tvWeatherEmpty: TextView
    private lateinit var weatherSection: LinearLayout
    private lateinit var statusContainer: LinearLayout
    private lateinit var rvCategories: RecyclerView
    private lateinit var rvArticles: RecyclerView
    private lateinit var dashboardScrollView: NestedScrollView
    private lateinit var swipeRefreshArticles: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var swipeRefreshDashboard: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    // New Views for Embedded Layout
    private lateinit var homeStandardView: LinearLayout
    private lateinit var weatherDetailView: LinearLayout
    private lateinit var pillContainer: LinearLayout

    // Detailed Weather Views
    private lateinit var tvDetailLocation: TextView
    private lateinit var tvDetailTemp: TextView
    private lateinit var tvDetailCondition: TextView
    private lateinit var tvDetailHighLow: TextView
    private lateinit var rvForecast: RecyclerView

    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val wifiManager by lazy { applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    private var currentWeatherCount = 0
    private var currentCategories: List<String> = emptyList()

    // State
    private enum class ViewState {
        DASHBOARD,
        WEATHER_DETAIL,
        ARTICLE_LIST
    }
    // Set initial state to ARTICLE_LIST (All Articles) as it is the new Home
    private var currentViewState: ViewState = ViewState.ARTICLE_LIST
    private var activeCategory: String? = null
    private var activeFeedId: Int = -1
    private lateinit var articleAdapter: ArticleAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
             updateWifiStatus()
        }

    private val weatherAdapter by lazy {
        WeatherHomeAdapter(preferencesRepository) { weather ->
            updateWeatherDetailView(weather)
            setViewState(ViewState.WEATHER_DETAIL)
        }
    }

    private val categoryAdapter by lazy {
        CategoryAdapter { category ->
            showArticles(category = category)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()
        setupAdapters()
        setupListeners()
        setupObservers()
        registerNetworkCallback()

        // Ensure UI is in correct state (weather visibility etc) for ARTICLE_LIST
        setViewState(ViewState.ARTICLE_LIST)

        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If in detailed views, go back to article list (which is the new home)
                if (currentViewState == ViewState.WEATHER_DETAIL || (currentViewState == ViewState.ARTICLE_LIST && (activeCategory != null || activeFeedId != -1))) {
                    showArticles(null)
                } else {
                    finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val category = intent.getStringExtra("EXTRA_CATEGORY")
        val feedId = intent.getIntExtra("FEED_ID", -1)
        val showWeather = intent.getBooleanExtra("OPEN_WEATHER", false)
        val openContent = intent.getBooleanExtra("OPEN_CONTENT", false)

        if (category != null || feedId != -1) {
            showArticles(category, feedId)
        } else if (openContent) {
            showArticles(null)
        } else if (showWeather && currentWeatherCount > 0) {
             if (weatherAdapter.itemCount > 0) {
                 val currentPos = weatherPager.currentItem
                 if (currentPos >= 0 && currentPos < weatherAdapter.currentList.size) {
                     updateWeatherDetailView(weatherAdapter.currentList[currentPos])
                     setViewState(ViewState.WEATHER_DETAIL)
                 }
             }
        }
    }

    private fun initViews() {
        statusDot = findViewById(R.id.status_dot)
        ssidText = findViewById(R.id.ssid_text)
        weatherPager = findViewById(R.id.weather_pager)
        tvWeatherEmpty = findViewById(R.id.tv_weather_empty)
        weatherSection = findViewById(R.id.weather_section)
        statusContainer = findViewById(R.id.status_container)
        rvCategories = findViewById(R.id.rv_categories)
        rvArticles = findViewById(R.id.rv_articles)
        dashboardScrollView = findViewById(R.id.dashboard_scroll_view)
        swipeRefreshArticles = findViewById(R.id.swipe_refresh_articles)
        swipeRefreshDashboard = findViewById(R.id.swipe_refresh_dashboard)

        homeStandardView = findViewById(R.id.home_standard_view)
        weatherDetailView = findViewById(R.id.weather_detail_view)
        pillContainer = findViewById(R.id.pill_container)

        tvDetailLocation = findViewById(R.id.tv_detail_location)
        tvDetailTemp = findViewById(R.id.tv_detail_temp)
        tvDetailCondition = findViewById(R.id.tv_detail_condition)
        tvDetailHighLow = findViewById(R.id.tv_detail_high_low)
        rvForecast = findViewById(R.id.rv_forecast)
    }

    private fun setupAdapters() {
        weatherPager.adapter = weatherAdapter
        rvCategories.layoutManager = LinearLayoutManager(this)
        rvCategories.adapter = categoryAdapter

        articleAdapter = ArticleAdapter(
            onArticleClick = { article ->
                // Mark as read when opening
                if (!article.isRead) {
                    viewModel.toggleArticleRead(article)
                }
                val intent = Intent(this, ArticleViewerActivity::class.java)
                intent.putExtra("ARTICLE_ID", article.id)
                startActivity(intent)
            },
            onDownloadClick = { article -> viewModel.downloadArticle(article) },
            onArticleLongClick = { article, view ->
                showArticleOptions(article, view)
            }
        )
        rvArticles.layoutManager = LinearLayoutManager(this)
        rvArticles.adapter = articleAdapter
    }

    private fun setupListeners() {
        // Bottom Nav
        findViewById<View>(R.id.nav_home).setOnClickListener {
            showArticles(null)
        }
        findViewById<View>(R.id.nav_content).setOnClickListener {
            showArticles(null)
        }
        findViewById<View>(R.id.nav_settings).setOnClickListener {
             startActivity(Intent(this, FeedSettingsActivity::class.java))
        }

        // Status Long Press
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

        // Swipe Refresh Listeners
        swipeRefreshArticles.setOnRefreshListener {
            lifecycleScope.launch {
                // Refresh local data only (no network sync)
                // The UI is already reactive to DB changes.
                // We just simulate a refresh delay to acknowledge the action.
                delay(500)
                swipeRefreshArticles.isRefreshing = false
            }
        }

        swipeRefreshDashboard.setOnRefreshListener {
            lifecycleScope.launch {
                // Refresh local data only (no network sync)
                // The UI is already reactive to DB changes.
                delay(500)
                swipeRefreshDashboard.isRefreshing = false
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.weatherLocations.collect { weatherList ->
                        weatherAdapter.submitList(weatherList)
                        currentWeatherCount = weatherList.size
                        updateWeatherVisibility()
                        updatePills()
                    }
                }
                launch {
                    viewModel.categories.collect { categoryList ->
                        categoryAdapter.submitList(categoryList)
                        currentCategories = categoryList
                        updatePills()
                    }
                }
                launch {
                    viewModel.currentArticles.collect { articles ->
                        // Save current scroll state to prevent jumping when items are reordered (e.g. marked read)
                        val layoutManager = rvArticles.layoutManager as? LinearLayoutManager
                        val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                        val offset = if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                            layoutManager?.findViewByPosition(firstVisiblePosition)?.top ?: 0
                        } else {
                            0
                        }

                        articleAdapter.submitList(articles) {
                            // Restore scroll position to keep the viewport stable
                            // This prevents the view from scrolling to the bottom if the top item moves there
                            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                                layoutManager?.scrollToPositionWithOffset(firstVisiblePosition, offset)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showArticles(category: String? = null, feedId: Int = -1) {
        activeCategory = category
        activeFeedId = feedId

        if (category != null) {
            viewModel.filterArticlesByCategory(category)
        } else if (feedId != -1) {
            viewModel.filterArticlesByFeed(feedId)
        } else {
            viewModel.filterArticlesByAll()
        }

        setViewState(ViewState.ARTICLE_LIST)
    }

    private fun setViewState(state: ViewState) {
        currentViewState = state
        when (state) {
            ViewState.DASHBOARD -> {
                swipeRefreshDashboard.visibility = View.VISIBLE
                dashboardScrollView.visibility = View.VISIBLE
                weatherSection.visibility = View.VISIBLE
                homeStandardView.visibility = View.VISIBLE
                weatherDetailView.visibility = View.GONE
                swipeRefreshArticles.visibility = View.GONE
                rvArticles.visibility = View.GONE
                activeCategory = null
                activeFeedId = -1
            }
            ViewState.WEATHER_DETAIL -> {
                swipeRefreshDashboard.visibility = View.VISIBLE
                dashboardScrollView.visibility = View.VISIBLE
                weatherSection.visibility = View.GONE
                homeStandardView.visibility = View.GONE
                weatherDetailView.visibility = View.VISIBLE
                swipeRefreshArticles.visibility = View.GONE
                rvArticles.visibility = View.GONE
                activeCategory = null
                activeFeedId = -1
            }
            ViewState.ARTICLE_LIST -> {
                swipeRefreshDashboard.visibility = View.GONE
                dashboardScrollView.visibility = View.GONE
                weatherSection.visibility = View.VISIBLE
                swipeRefreshArticles.visibility = View.VISIBLE
                rvArticles.visibility = View.VISIBLE
            }
        }
        updatePills()
    }

    private fun updateWeatherDetailView(weather: Weather) {
        tvDetailLocation.text = weather.locationName

        try {
            val jsonObject = com.google.gson.JsonParser().parse(weather.dataJson ?: "").asJsonObject
            val current = jsonObject.getAsJsonObject("current_weather")
            val hourly = jsonObject.getAsJsonObject("hourly")
            val daily = if (jsonObject.has("daily")) jsonObject.getAsJsonObject("daily") else null

            val useImperial = preferencesRepository.weatherUnits == "imperial"
            val temp = current.get("temperature").asDouble
            val code = current.get("weathercode").asInt

            if (useImperial) {
                val f = (temp * 9 / 5) + 32
                tvDetailTemp.text = String.format("%.1f°F", f)
            } else {
                tvDetailTemp.text = "$temp°C"
            }

            tvDetailCondition.text = getWeatherCondition(code)

            if (daily != null) {
                val maxArr = daily.getAsJsonArray("temperature_2m_max")
                val minArr = daily.getAsJsonArray("temperature_2m_min")
                if (maxArr.size() > 0 && minArr.size() > 0) {
                    val max = maxArr[0].asDouble
                    val min = minArr[0].asDouble
                    if (useImperial) {
                         val maxF = (max * 9 / 5) + 32
                         val minF = (min * 9 / 5) + 32
                         tvDetailHighLow.text = String.format("H: %.0f° L: %.0f°", maxF, minF)
                    } else {
                         tvDetailHighLow.text = "H: $max° L: $min°"
                    }
                }
            } else {
                tvDetailHighLow.text = ""
            }

            if (daily != null && hourly != null) {
                val forecastItems = mutableListOf<ForecastItem>()
                val dailyTimes = daily.getAsJsonArray("time")
                val maxTemps = daily.getAsJsonArray("temperature_2m_max")
                val minTemps = daily.getAsJsonArray("temperature_2m_min")
                val dailyCodes = daily.getAsJsonArray("weathercode")
                val hourlyTimes = hourly.getAsJsonArray("time")
                val hourlyTemps = hourly.getAsJsonArray("temperature_2m")
                val hourlyCodes = hourly.getAsJsonArray("weathercode")

                for (i in 0 until dailyTimes.size()) {
                    if (i < maxTemps.size() && i < minTemps.size() && i < dailyCodes.size()) {
                        val dateStr = dailyTimes[i].asString
                        val dayName = try {
                            val date = LocalDate.parse(dateStr)
                            date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                        } catch (e: Exception) {
                            dateStr
                        }
                        val condition = getWeatherCondition(dailyCodes[i].asInt)
                        val dayHourlyItems = mutableListOf<HourlyItem>()
                        val startIndex = i * 24
                        val endIndex = startIndex + 24
                        for (h in startIndex until endIndex) {
                            if (h < hourlyTimes.size() && h < hourlyTemps.size() && h < hourlyCodes.size()) {
                                dayHourlyItems.add(HourlyItem(
                                    hourlyTimes[h].asString,
                                    hourlyTemps[h].asDouble,
                                    hourlyCodes[h].asInt
                                ))
                            }
                        }
                        forecastItems.add(ForecastItem(
                            day = dayName,
                            condition = condition,
                            maxTemp = maxTemps[i].asDouble,
                            minTemp = minTemps[i].asDouble,
                            code = dailyCodes[i].asInt,
                            hourlyItems = dayHourlyItems
                        ))
                    }
                }
                val forecastAdapter = ForecastAdapter(forecastItems, useImperial)
                rvForecast.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                rvForecast.adapter = forecastAdapter
                rvForecast.visibility = View.VISIBLE
            } else {
                rvForecast.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tvDetailCondition.text = "Error parsing weather data"
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when(code) {
            0 -> "Sunny"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }

    private fun updatePills() {
        pillContainer.removeAllViews()

        // "All" Pill
        val isAllActive = currentViewState == ViewState.ARTICLE_LIST && activeCategory == null && activeFeedId == -1
        addPill("All", isAllActive) {
            showArticles(null)
        }

        // "Weather" Pill
        if (currentWeatherCount > 0) {
            addPill("Weather", currentViewState == ViewState.WEATHER_DETAIL) {
                 if (weatherAdapter.itemCount > 0) {
                     val currentPos = weatherPager.currentItem
                     if (currentPos >= 0 && currentPos < weatherAdapter.currentList.size) {
                         updateWeatherDetailView(weatherAdapter.currentList[currentPos])
                         setViewState(ViewState.WEATHER_DETAIL)
                     }
                 }
            }
        }

        // Category Pills
        currentCategories.forEach { category ->
            addPill(category, currentViewState == ViewState.ARTICLE_LIST && activeCategory == category) {
                 showArticles(category = category)
            }
        }

        // Debug Pill
        if (preferencesRepository.detailedDebuggingEnabled) {
            addPill("Debug", false) {
                showDebugLog()
            }
        }
    }

    private fun showDebugLog() {
        lifecycleScope.launch {
            val logFile = java.io.File(filesDir, "debug_log.txt")
            val content = withContext(Dispatchers.IO) {
                if (logFile.exists()) {
                    logFile.readText()
                } else {
                    "No logs found."
                }
            }

            val textView = TextView(this@HomeActivity).apply {
                text = content
                setPadding(32, 32, 32, 32)
                setTextIsSelectable(true)
            }

            val scrollView = NestedScrollView(this@HomeActivity).apply {
                addView(textView)
            }

            AlertDialog.Builder(this@HomeActivity)
                .setTitle("Debug Logs")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .setNeutralButton("Clear") { _, _ ->
                     if (logFile.exists()) {
                         logFile.delete()
                         Toast.makeText(this@HomeActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
                     }
                }
                .show()
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

    override fun onResume() {
        super.onResume()
        updateWifiStatus()
    }

    private fun updateWeatherVisibility() {
        if (weatherAdapter.itemCount > 0) {
            weatherPager.visibility = View.VISIBLE
            tvWeatherEmpty.visibility = View.GONE
        } else {
            weatherPager.visibility = View.GONE
            tvWeatherEmpty.visibility = View.VISIBLE
        }
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

    private fun showArticleOptions(article: ArticleListItem, view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)

        // Add options based on current state
        if (article.isFavorite) {
            popup.menu.add(0, 1, 0, "Unfavorite")
        } else {
            popup.menu.add(0, 1, 0, "Favorite")
        }

        if (article.isRead) {
            popup.menu.add(0, 2, 0, "Mark as Unread")
        } else {
            popup.menu.add(0, 2, 0, "Mark as Read")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { // Toggle Favorite
                    viewModel.toggleArticleFavorite(article)
                    true
                }
                2 -> { // Toggle Read
                    viewModel.toggleArticleRead(article)
                    true
                }
                else -> false
            }
        }
        popup.show()
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
