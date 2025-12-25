package com.example.offlinebrowser.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.R
import com.example.offlinebrowser.viewmodel.MainViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

class WeatherActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var weatherAdapter: WeatherAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val etLat = findViewById<EditText>(R.id.etLat)
        val etLon = findViewById<EditText>(R.id.etLon)
        val etName = findViewById<EditText>(R.id.etName)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnCurrentLocation = findViewById<Button>(R.id.btnCurrentLocation)
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

        btnCurrentLocation.setOnClickListener {
            checkLocationPermission()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherLocations.collect { weatherList ->
                    weatherAdapter.submitList(weatherList)
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
            ) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.addWeatherLocation("Current Location", location.latitude, location.longitude)
                } else {
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
