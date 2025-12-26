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
import com.example.offlinebrowser.data.model.Weather
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var statusDot: View
    private lateinit var ssidText: TextView
    private lateinit var weatherPager: ViewPager2
    private lateinit var tvWeatherEmpty: TextView
    private lateinit var statusContainer: LinearLayout

    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val wifiManager by lazy { applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
             updateWifiStatus()
        }

    // A simple adapter for the weather ViewPager
    inner class WeatherHomeAdapter : RecyclerView.Adapter<WeatherHomeAdapter.WeatherViewHolder>() {
        var locations: List<Weather> = emptyList()

        inner class WeatherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTemp: TextView = view.findViewById(R.id.tv_temp)
            val tvCondition: TextView = view.findViewById(R.id.tv_condition)
            val tvCity: TextView = view.findViewById(R.id.tv_city)
            val tvHighLow: TextView = view.findViewById(R.id.tv_high_low)

            init {
                view.setOnLongClickListener {
                    startActivity(Intent(this@HomeActivity, WeatherActivity::class.java))
                    true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_weather_home, parent, false)
            return WeatherViewHolder(view)
        }

        override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
            val weather = locations[position]
            holder.tvCity.text = weather.name
            // Basic data binding - in real app we'd parse current conditions
            // Assuming default text for now if data is simple
        }

        override fun getItemCount(): Int = locations.size
    }

    private val weatherAdapter = WeatherHomeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        statusDot = findViewById(R.id.status_dot)
        ssidText = findViewById(R.id.ssid_text)
        weatherPager = findViewById(R.id.weather_pager)
        tvWeatherEmpty = findViewById(R.id.tv_weather_empty)
        statusContainer = findViewById(R.id.status_container)

        weatherPager.adapter = weatherAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherLocations.collect { weatherList ->
                    weatherAdapter.locations = weatherList
                    weatherAdapter.notifyDataSetChanged()
                    updateWeatherVisibility()
                }
            }
        }

        // Bottom Nav
        findViewById<View>(R.id.nav_home).setOnClickListener {
            // Already here
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
