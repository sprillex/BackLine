package com.example.offlinebrowser.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class WeatherActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var weatherAdapter: WeatherAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        val etLat = findViewById<EditText>(R.id.etLat)
        val etLon = findViewById<EditText>(R.id.etLon)
        val etName = findViewById<EditText>(R.id.etName)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val rvWeather = findViewById<RecyclerView>(R.id.rvWeather)

        weatherAdapter = WeatherAdapter(
            onRefresh = { weather -> viewModel.updateWeather(weather) }
        )

        rvWeather.layoutManager = LinearLayoutManager(this)
        rvWeather.adapter = weatherAdapter

        btnAdd.setOnClickListener {
            val lat = etLat.text.toString().toDoubleOrNull()
            val lon = etLon.text.toString().toDoubleOrNull()
            val name = etName.text.toString()

            if (lat != null && lon != null && name.isNotEmpty()) {
                viewModel.addWeatherLocation(name, lat, lon)
                etLat.text.clear()
                etLon.text.clear()
                etName.text.clear()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherLocations.collect { weatherList ->
                    weatherAdapter.submitList(weatherList)
                }
            }
        }
    }
}
