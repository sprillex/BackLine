package com.example.offlinebrowser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.util.AiSkillManager
import com.example.offlinebrowser.util.GemmaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSettingsActivity : AppCompatActivity() {

    private lateinit var preferencesRepository: PreferencesRepository

    private val selectGemmaModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val path = it.toString()
            findViewById<EditText>(R.id.etGemmaModelPath).setText(path)
            preferencesRepository.gemmaModelPath = path
            Toast.makeText(this, "Gemma model path updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_settings)

        preferencesRepository = PreferencesRepository(this)

        val etGemmaModelPath = findViewById<EditText>(R.id.etGemmaModelPath)
        val etGemmaModelUrl = findViewById<EditText>(R.id.etGemmaModelUrl)
        val btnSelectGemmaModel = findViewById<Button>(R.id.btnSelectGemmaModel)
        val btnDownloadGemmaModel = findViewById<Button>(R.id.btnDownloadGemmaModel)
        val btnCheckGemmaStatus = findViewById<Button>(R.id.btnCheckGemmaStatus)
        val btnUpdateAiSkills = findViewById<Button>(R.id.btnUpdateAiSkills)
        val btnSaveAiSettings = findViewById<Button>(R.id.btnSaveAiSettings)

        etGemmaModelPath.setText(preferencesRepository.gemmaModelPath ?: "")
        etGemmaModelUrl.setText(preferencesRepository.gemmaModelUrl)

        btnSelectGemmaModel.setOnClickListener {
            selectGemmaModelLauncher.launch(arrayOf("*/*"))
        }

        btnDownloadGemmaModel.setOnClickListener {
            val url = etGemmaModelUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                preferencesRepository.gemmaModelUrl = url
                Toast.makeText(this, "Downloading model...", Toast.LENGTH_SHORT).show()
                val gemmaManager = GemmaManager(this)
                gemmaManager.downloadModel(url)

                CoroutineScope(Dispatchers.Main).launch {
                    val success = gemmaManager.downloadModelDirect(url) { status ->
                        Toast.makeText(this@AiSettingsActivity, status, Toast.LENGTH_SHORT).show()
                    }
                    if (success) {
                        etGemmaModelPath.setText(preferencesRepository.gemmaModelPath ?: "")
                    }
                }
            } else {
                Toast.makeText(this, "Please enter a download URL", Toast.LENGTH_SHORT).show()
            }
        }

        btnCheckGemmaStatus.setOnClickListener {
            val gemmaManager = GemmaManager(this)
            val isAvailable = gemmaManager.isModelAvailable()
            val statusMsg = if (isAvailable) {
                "Gemma Model Ready: ${gemmaManager.getModelFile()?.name}"
            } else {
                "No Gemma model file found. Using internal summary engine."
            }
            Toast.makeText(this, statusMsg, Toast.LENGTH_LONG).show()
        }

        btnUpdateAiSkills.setOnClickListener {
            Toast.makeText(this, "Updating AI Skills from GitHub...", Toast.LENGTH_SHORT).show()
            val skillManager = AiSkillManager(this)
            CoroutineScope(Dispatchers.IO).launch {
                val result = skillManager.updateSkillsFromGitHub()
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { count ->
                            Toast.makeText(this@AiSettingsActivity, "Successfully updated $count AI Skills from GitHub!", Toast.LENGTH_LONG).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(this@AiSettingsActivity, "Failed to update AI Skills: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }

        btnSaveAiSettings.setOnClickListener {
            preferencesRepository.gemmaModelPath = etGemmaModelPath.text.toString().ifBlank { null }
            preferencesRepository.gemmaModelUrl = etGemmaModelUrl.text.toString().ifBlank { preferencesRepository.gemmaModelUrl }
            Toast.makeText(this, "AI Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
