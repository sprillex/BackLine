package com.example.offlinebrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.workers.SyncWorker
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesRepository: PreferencesRepository

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
             val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
             if (!fineLocation) {
                 Toast.makeText(this, "Location permission required for SSID filtering", Toast.LENGTH_LONG).show()
             }
        }

    private fun scheduleSync(interval: Long, wifiOnly: Boolean) {
        val constraintsBuilder = Constraints.Builder()
        if (wifiOnly) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraintsBuilder.build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferencesRepository = PreferencesRepository(this)

        val cbWifiOnly = findViewById<CheckBox>(R.id.cbWifiOnly)
        val etSsids = findViewById<EditText>(R.id.etSsids)
        val etInterval = findViewById<EditText>(R.id.etInterval)
        val etLimitCount = findViewById<EditText>(R.id.etLimitCount)
        val etLimitDays = findViewById<EditText>(R.id.etLimitDays)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnRequestPerm = findViewById<Button>(R.id.btnRequestPerm)

        cbWifiOnly.isChecked = preferencesRepository.wifiOnly
        etSsids.setText(preferencesRepository.allowedWifiSsids.joinToString(","))
        etInterval.setText(preferencesRepository.refreshIntervalMinutes.toString())
        etLimitCount.setText(preferencesRepository.feedLimitCount.toString())
        etLimitDays.setText(preferencesRepository.feedLimitDays.toString())

        btnSave.setOnClickListener {
            preferencesRepository.wifiOnly = cbWifiOnly.isChecked

            val ssids = etSsids.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            preferencesRepository.allowedWifiSsids = ssids

            val interval = etInterval.text.toString().toLongOrNull() ?: 60
            preferencesRepository.refreshIntervalMinutes = interval

            val limitCount = etLimitCount.text.toString().toIntOrNull() ?: 50
            preferencesRepository.feedLimitCount = limitCount

            val limitDays = etLimitDays.text.toString().toIntOrNull() ?: 30
            preferencesRepository.feedLimitDays = limitDays

            // Re-schedule sync with new settings
            scheduleSync(interval, cbWifiOnly.isChecked)

            finish()
        }

        btnRequestPerm.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            } else {
                 Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
