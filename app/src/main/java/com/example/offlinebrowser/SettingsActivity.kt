package com.example.offlinebrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.offlinebrowser.data.repository.ConfigRepository
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.workers.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var configRepository: ConfigRepository

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val outputStream = contentResolver.openOutputStream(it)
                    outputStream?.use { stream ->
                        configRepository.exportConfig(stream)
                    }
                    Toast.makeText(this@SettingsActivity, "Configuration exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    inputStream?.use { stream ->
                        configRepository.importConfig(stream)
                    }
                    Toast.makeText(this@SettingsActivity, "Configuration imported. Updating content...", Toast.LENGTH_SHORT).show()

                    // Trigger Sync
                    val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    WorkManager.getInstance(this@SettingsActivity).enqueue(request)

                    // Refresh UI
                    recreate()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@SettingsActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
        configRepository = ConfigRepository(this)

        val cbWifiOnly = findViewById<CheckBox>(R.id.cbWifiOnly)
        val etSsids = findViewById<EditText>(R.id.etSsids)

        val ssidToAdd = intent.getStringExtra("EXTRA_ADD_SSID")
        if (ssidToAdd != null) {
             androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add SSID")
                .setMessage("Add $ssidToAdd to allowed list?")
                .setPositiveButton("Yes") { _, _ ->
                     val current = etSsids.text.toString()
                     if (current.isEmpty()) {
                         etSsids.setText(ssidToAdd)
                     } else if (!current.contains(ssidToAdd)) {
                         etSsids.setText("$current, $ssidToAdd")
                     }
                }
                .show()
        }
        val etInterval = findViewById<EditText>(R.id.etInterval)
        val etLimitCount = findViewById<EditText>(R.id.etLimitCount)
        val etLimitDays = findViewById<EditText>(R.id.etLimitDays)
        val rgWeatherUnits = findViewById<RadioGroup>(R.id.rgWeatherUnits)
        val rbMetric = findViewById<RadioButton>(R.id.rbMetric)
        val rbImperial = findViewById<RadioButton>(R.id.rbImperial)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnRequestPerm = findViewById<Button>(R.id.btnRequestPerm)
        val btnExport = findViewById<Button>(R.id.btnExport)
        val btnImport = findViewById<Button>(R.id.btnImport)

        cbWifiOnly.isChecked = preferencesRepository.wifiOnly
        etSsids.setText(preferencesRepository.allowedWifiSsids.joinToString(","))
        etInterval.setText(preferencesRepository.refreshIntervalMinutes.toString())
        etLimitCount.setText(preferencesRepository.feedLimitCount.toString())
        etLimitDays.setText(preferencesRepository.feedLimitDays.toString())

        val currentUnits = preferencesRepository.weatherUnits
        if (currentUnits == "imperial") {
            rbImperial.isChecked = true
        } else {
            rbMetric.isChecked = true
        }

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

            val selectedUnits = if (rbImperial.isChecked) "imperial" else "metric"
            preferencesRepository.weatherUnits = selectedUnits

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

        btnExport.setOnClickListener {
            createDocumentLauncher.launch("offline_browser_config.json")
        }

        btnImport.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json"))
        }
    }
}
