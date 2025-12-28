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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.ui.WeatherActivity
import com.example.offlinebrowser.viewmodel.MainViewModel
import com.example.offlinebrowser.ui.CategoryAdapter
import com.example.offlinebrowser.ui.WeatherHomeAdapter
import com.example.offlinebrowser.data.model.Weather
import com.example.offlinebrowser.ui.HourlyAdapter
import com.example.offlinebrowser.ui.HourlyItem
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val preferencesRepository by lazy { PreferencesRepository(this) }

    private lateinit var statusDot: View
    private lateinit var ssidText: TextView
    private lateinit var weatherPager: ViewPager2
    private lateinit var tvWeatherEmpty: TextView
    private lateinit var statusContainer: LinearLayout
    private lateinit var rvCategories: RecyclerView

    // New Views for Embedded Layout
    private lateinit var homeStandardView: LinearLayout
    private lateinit var weatherDetailView: LinearLayout
    private lateinit var pillAll: TextView
    private lateinit var pillWeather: TextView

    // Detailed Weather Views
    private lateinit var tvDetailLocation: TextView
    private lateinit var tvDetailTemp: TextView
    private lateinit var tvDetailCondition: TextView
    private lateinit var tvDetailHighLow: TextView
    private lateinit var rvHourly: RecyclerView

    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val wifiManager by lazy { applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
             updateWifiStatus()
        }

    private val weatherAdapter by lazy {
        WeatherHomeAdapter(preferencesRepository) { weather ->
            // On long press, toggle to the detail view
            updateWeatherDetailView(weather)
            toggleWeatherDetail(true)
        }
    }

    private fun updateWeatherDetailView(weather: Weather) {
        tvDetailLocation.text = weather.locationName

        try {
            val jsonObject = com.google.gson.JsonParser().parse(weather.dataJson ?: "").asJsonObject
            val current = jsonObject.getAsJsonObject("current_weather")
            val hourly = jsonObject.getAsJsonObject("hourly")

            val useImperial = preferencesRepository.weatherUnits == "imperial"
            val temp = current.get("temperature").asDouble
            val code = current.get("weathercode").asInt

            if (useImperial) {
                val f = (temp * 9 / 5) + 32
                tvDetailTemp.text = String.format("%.1f°F", f)
            } else {
                tvDetailTemp.text = "$temp°C"
            }

            tvDetailCondition.text = when(code) {
                0 -> "Clear"
                1, 2, 3 -> "Partly Cloudy"
                45, 48 -> "Fog"
                51, 53, 55 -> "Drizzle"
                61, 63, 65 -> "Rain"
                71, 73, 75 -> "Snow"
                else -> "Unknown"
            }

            // High/Low if daily available
            if (jsonObject.has("daily")) {
                val daily = jsonObject.getAsJsonObject("daily")
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

            // Hourly
            val hourlyItems = mutableListOf<HourlyItem>()
            if (hourly != null) {
                val timeArr = hourly.getAsJsonArray("time")
                val tempArr = hourly.getAsJsonArray("temperature_2m")
                val codeArr = hourly.getAsJsonArray("weathercode")

                // Limit to next 24 hours or so
                val count = minOf(timeArr.size(), 24)
                for (i in 0 until count) {
                    hourlyItems.add(HourlyItem(
                        timeArr[i].asString,
                        tempArr[i].asDouble,
                        codeArr[i].asInt
                    ))
                }
            }

            rvHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            rvHourly.adapter = HourlyAdapter(hourlyItems, useImperial)

        } catch (e: Exception) {
            e.printStackTrace()
            tvDetailCondition.text = "Error parsing weather data"
        }
    }

    private fun toggleWeatherDetail(showDetail: Boolean) {
        if (showDetail) {
            homeStandardView.visibility = View.GONE
            weatherDetailView.visibility = View.VISIBLE

            // Update Pill Styles
            pillAll.setBackgroundResource(R.drawable.bg_pill)
            pillAll.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            pillWeather.setBackgroundResource(R.drawable.bg_pill_active)
            pillWeather.setTextColor(ContextCompat.getColor(this, R.color.bg_color))
        } else {
            homeStandardView.visibility = View.VISIBLE
            weatherDetailView.visibility = View.GONE

            // Update Pill Styles
            pillAll.setBackgroundResource(R.drawable.bg_pill_active)
            pillAll.setTextColor(ContextCompat.getColor(this, R.color.bg_color))

            pillWeather.setBackgroundResource(R.drawable.bg_pill)
            pillWeather.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private val categoryAdapter by lazy {
        CategoryAdapter { category ->
            val intent = Intent(this, ArticleListActivity::class.java)
            intent.putExtra("EXTRA_CATEGORY", category)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        statusDot = findViewById(R.id.status_dot)
        ssidText = findViewById(R.id.ssid_text)
        weatherPager = findViewById(R.id.weather_pager)
        tvWeatherEmpty = findViewById(R.id.tv_weather_empty)
        statusContainer = findViewById(R.id.status_container)
        rvCategories = findViewById(R.id.rv_categories)

        homeStandardView = findViewById(R.id.home_standard_view)
        weatherDetailView = findViewById(R.id.weather_detail_view)
        pillAll = findViewById(R.id.pill_all)
        pillWeather = findViewById(R.id.pill_weather)

        tvDetailLocation = findViewById(R.id.tv_detail_location)
        tvDetailTemp = findViewById(R.id.tv_detail_temp)
        tvDetailCondition = findViewById(R.id.tv_detail_condition)
        tvDetailHighLow = findViewById(R.id.tv_detail_high_low)
        rvHourly = findViewById(R.id.rv_hourly)

        weatherPager.adapter = weatherAdapter
        rvCategories.layoutManager = LinearLayoutManager(this)
        rvCategories.adapter = categoryAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.weatherLocations.collect { weatherList ->
                        weatherAdapter.submitList(weatherList)
                        updateWeatherVisibility()
                    }
                }
                launch {
                    viewModel.categories.collect { categoryList ->
                        categoryAdapter.submitList(categoryList)
                    }
                }
            }
        }

        // Bottom Nav
        findViewById<View>(R.id.nav_home).setOnClickListener {
            // If in detailed view, go back to standard
            if (weatherDetailView.visibility == View.VISIBLE) {
                toggleWeatherDetail(false)
            }
        }
        findViewById<View>(R.id.nav_content).setOnClickListener {
             startActivity(Intent(this, ArticleListActivity::class.java))
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

        // Pill Listeners
        pillAll.setOnClickListener {
            toggleWeatherDetail(false)
        }

        pillWeather.setOnClickListener {
            // If we have weather data, show the first available or current one
            if (weatherAdapter.itemCount > 0) {
                 // We can get the current item from ViewModel if we had access to the list synchronously or if we cached it.
                 // weatherAdapter.currentList gives us the list
                 if (weatherAdapter.currentList.isNotEmpty()) {
                     // Try to get the one currently visible in ViewPager if possible,
                     // but ViewPager state is UI. Let's default to the first one for now or the last one clicked.
                     // A better approach is to track the last viewed weather or just the first one.
                     val currentPos = weatherPager.currentItem
                     if (currentPos >= 0 && currentPos < weatherAdapter.currentList.size) {
                         updateWeatherDetailView(weatherAdapter.currentList[currentPos])
                         toggleWeatherDetail(true)
                     }
                 }
            } else {
                 Toast.makeText(this, "No weather data available", Toast.LENGTH_SHORT).show()
            }
        }

        registerNetworkCallback()
    }

    override fun onResume() {
        super.onResume()
        updateWifiStatus()

        // Check permission if needed for SSID
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
             requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
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

    @SuppressLint("MissingPermission")
    private fun updateWifiStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isWifi) {
            statusDot.setBackgroundResource(R.drawable.status_dot_green)

            // Get SSID
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
