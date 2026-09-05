package com.example.offlinebrowser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.offlinebrowser.data.model.AiSkill
import com.example.offlinebrowser.data.model.AiSkillRegistry
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.ui.AiSkillAdapter
import com.example.offlinebrowser.util.AiSkillManager
import com.example.offlinebrowser.util.GemmaManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSettingsActivity : AppCompatActivity() {

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var skillManager: AiSkillManager
    private lateinit var skillAdapter: AiSkillAdapter
    private val gson = GsonBuilder().setPrettyPrinting().create()

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
        skillManager = AiSkillManager(this)

        val etGeminiApiKey = findViewById<EditText>(R.id.etGeminiApiKey)
        val etGemmaModelPath = findViewById<EditText>(R.id.etGemmaModelPath)
        val etGemmaModelUrl = findViewById<EditText>(R.id.etGemmaModelUrl)
        val btnSelectGemmaModel = findViewById<Button>(R.id.btnSelectGemmaModel)
        val btnDownloadGemmaModel = findViewById<Button>(R.id.btnDownloadGemmaModel)
        val btnCheckGemmaStatus = findViewById<Button>(R.id.btnCheckGemmaStatus)
        val btnUpdateAiSkills = findViewById<Button>(R.id.btnUpdateAiSkills)
        val btnSaveAiSettings = findViewById<Button>(R.id.btnSaveAiSettings)

        val rvAiSkills = findViewById<RecyclerView>(R.id.rvAiSkills)
        val etNewSkillName = findViewById<EditText>(R.id.etNewSkillName)
        val etNewSkillJson = findViewById<EditText>(R.id.etNewSkillJson)
        val btnAddSkill = findViewById<Button>(R.id.btnAddSkill)

        etGeminiApiKey.setText(preferencesRepository.geminiApiKey ?: "")
        etGemmaModelPath.setText(preferencesRepository.gemmaModelPath ?: "")
        etGemmaModelUrl.setText(preferencesRepository.gemmaModelUrl)

        // Setup AI Skills RecyclerView
        skillAdapter = AiSkillAdapter(
            skills = skillManager.getAllSkills(),
            isSkillEnabled = { skillId -> skillManager.isSkillEnabled(skillId) },
            onToggleEnable = { skill, isChecked ->
                skillManager.setSkillEnabled(skill.id, isChecked)
                Toast.makeText(this, "${skill.displayName} ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { skill ->
                showSkillOptionsMenu(skill)
            }
        )
        rvAiSkills.layoutManager = LinearLayoutManager(this)
        rvAiSkills.adapter = skillAdapter

        btnAddSkill.setOnClickListener {
            val nameInput = etNewSkillName.text.toString().trim()
            val jsonInput = etNewSkillJson.text.toString().trim()

            if (jsonInput.isEmpty()) {
                Toast.makeText(this, "Please enter JSON for the new skill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                var skillsToAdd = mutableListOf<AiSkill>()
                if (jsonInput.contains("\"skills\"")) {
                    val registry = gson.fromJson(jsonInput, AiSkillRegistry::class.java)
                    if (registry?.skills != null) {
                        skillsToAdd.addAll(registry.skills)
                    }
                } else {
                    val singleSkill = gson.fromJson(jsonInput, AiSkill::class.java)
                    if (singleSkill != null) {
                        skillsToAdd.add(singleSkill)
                    }
                }

                if (skillsToAdd.isEmpty()) {
                    Toast.makeText(this, "Failed to parse skill JSON", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                for (s in skillsToAdd) {
                    val finalDisplayName = if (nameInput.isNotEmpty() && skillsToAdd.size == 1) nameInput else if (s.displayName.isNotBlank()) s.displayName else s.id
                    val finalId = if (s.id.isNotBlank()) s.id else finalDisplayName.lowercase().replace("\\s+".toRegex(), "_")
                    val updatedSkill = s.copy(
                        id = finalId,
                        displayName = finalDisplayName
                    )
                    skillManager.addOrUpdateSkill(updatedSkill)
                }

                refreshSkillList()
                etNewSkillName.setText("")
                etNewSkillJson.setText("")
                Toast.makeText(this, "Skill added successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid JSON: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

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
            CoroutineScope(Dispatchers.IO).launch {
                val result = skillManager.updateSkillsFromGitHub()
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { count ->
                            refreshSkillList()
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
            preferencesRepository.geminiApiKey = etGeminiApiKey.text.toString().trim().ifBlank { null }
            preferencesRepository.gemmaModelPath = etGemmaModelPath.text.toString().ifBlank { null }
            preferencesRepository.gemmaModelUrl = etGemmaModelUrl.text.toString().ifBlank { preferencesRepository.gemmaModelUrl }
            Toast.makeText(this, "AI Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun refreshSkillList() {
        skillManager = AiSkillManager(this)
        skillAdapter.updateSkills(skillManager.getAllSkills())
    }

    private fun showSkillOptionsMenu(skill: AiSkill) {
        val options = arrayOf("Edit Skill JSON", "Rename Skill", "Delete Skill")
        AlertDialog.Builder(this)
            .setTitle(skill.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditSkillDialog(skill)
                    1 -> showRenameSkillDialog(skill)
                    2 -> showDeleteSkillDialog(skill)
                }
            }
            .show()
    }

    private fun showEditSkillDialog(skill: AiSkill) {
        val input = EditText(this)
        input.setText(gson.toJson(skill))
        input.typeface = android.graphics.Typeface.MONOSPACE
        input.textSize = 12f

        AlertDialog.Builder(this)
            .setTitle("Edit Skill JSON")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val jsonStr = input.text.toString().trim()
                try {
                    val updatedSkill = gson.fromJson(jsonStr, AiSkill::class.java)
                    if (updatedSkill != null && updatedSkill.id.isNotBlank()) {
                        skillManager.addOrUpdateSkill(updatedSkill)
                        refreshSkillList()
                        Toast.makeText(this, "Skill updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid skill structure", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to parse JSON: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameSkillDialog(skill: AiSkill) {
        val input = EditText(this)
        input.setText(skill.displayName)

        AlertDialog.Builder(this)
            .setTitle("Rename Skill")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    skillManager.renameSkillDisplayName(skill.id, newName)
                    refreshSkillList()
                    Toast.makeText(this, "Skill renamed to $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteSkillDialog(skill: AiSkill) {
        AlertDialog.Builder(this)
            .setTitle("Delete Skill")
            .setMessage("Are you sure you want to delete '${skill.displayName}'?")
            .setPositiveButton("Delete") { _, _ ->
                skillManager.deleteSkill(skill.id)
                refreshSkillList()
                Toast.makeText(this, "Skill deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
