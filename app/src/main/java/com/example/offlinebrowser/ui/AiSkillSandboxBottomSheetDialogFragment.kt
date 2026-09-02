package com.example.offlinebrowser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.offlinebrowser.R
import com.example.offlinebrowser.data.model.AiSkill
import com.example.offlinebrowser.data.repository.PreferencesRepository
import com.example.offlinebrowser.util.AiSkillManager
import com.example.offlinebrowser.util.GeminiSkillOptimizer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSkillSandboxBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var articleText: String = ""
    private var articleId: Int = -1

    private lateinit var skillManager: AiSkillManager
    private lateinit var preferencesRepository: PreferencesRepository
    private val geminiOptimizer = GeminiSkillOptimizer()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private lateinit var spinnerSkills: Spinner
    private lateinit var btnRunSkill: Button
    private lateinit var btnResetOverrides: Button
    private lateinit var progressBarSandbox: ProgressBar
    private lateinit var tvSkillOutput: TextView
    private lateinit var etUserCritique: EditText
    private lateinit var btnOptimizeWithGemini: Button

    private lateinit var chipCutOff: Button
    private lateinit var chipGeneric: Button
    private lateinit var chipRepeats: Button

    private var currentSkillsList: List<AiSkill> = emptyList()
    private var lastExecutionOutput: String = ""

    companion object {
        private const val ARG_ARTICLE_TEXT = "ARG_ARTICLE_TEXT"
        private const val ARG_ARTICLE_ID = "ARG_ARTICLE_ID"

        fun newInstance(articleText: String, articleId: Int): AiSkillSandboxBottomSheetDialogFragment {
            val fragment = AiSkillSandboxBottomSheetDialogFragment()
            val args = Bundle().apply {
                putString(ARG_ARTICLE_TEXT, articleText)
                putInt(ARG_ARTICLE_ID, articleId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        articleText = arguments?.getString(ARG_ARTICLE_TEXT) ?: ""
        articleId = arguments?.getInt(ARG_ARTICLE_ID, -1) ?: -1
        context?.let {
            skillManager = AiSkillManager(it)
            preferencesRepository = PreferencesRepository(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_ai_skill_sandbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerSkills = view.findViewById(R.id.spinnerSkills)
        btnRunSkill = view.findViewById(R.id.btnRunSkill)
        btnResetOverrides = view.findViewById(R.id.btnResetOverrides)
        progressBarSandbox = view.findViewById(R.id.progressBarSandbox)
        tvSkillOutput = view.findViewById(R.id.tvSkillOutput)
        etUserCritique = view.findViewById(R.id.etUserCritique)
        btnOptimizeWithGemini = view.findViewById(R.id.btnOptimizeWithGemini)

        chipCutOff = view.findViewById(R.id.chipCutOff)
        chipGeneric = view.findViewById(R.id.chipGeneric)
        chipRepeats = view.findViewById(R.id.chipRepeats)

        chipCutOff.setOnClickListener { etUserCritique.setText("Output truncated mid-sentence.") }
        chipGeneric.setOnClickListener { etUserCritique.setText("Output contains generic corporate filler statements.") }
        chipRepeats.setOnClickListener { etUserCritique.setText("Output verbatim repeats article sentences.") }

        populateSkillsSpinner()

        btnRunSkill.setOnClickListener {
            runSelectedSkill()
        }

        btnResetOverrides.setOnClickListener {
            skillManager.resetOverrides()
            populateSkillsSpinner()
            Toast.makeText(requireContext(), "Overrides reset to asset defaults", Toast.LENGTH_SHORT).show()
        }

        btnOptimizeWithGemini.setOnClickListener {
            val selectedSkill = getSelectedSkill()
            if (selectedSkill == null) {
                Toast.makeText(requireContext(), "Please select a valid skill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiKey = preferencesRepository.geminiApiKey
            if (apiKey.isNullOrBlank()) {
                promptForApiKey { key ->
                    runOptimization(key, selectedSkill)
                }
            } else {
                runOptimization(apiKey, selectedSkill)
            }
        }
    }

    private fun populateSkillsSpinner() {
        currentSkillsList = skillManager.getAllSkills()
        val displayNames = currentSkillsList.map { it.displayName.ifBlank { it.id } }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSkills.adapter = adapter
    }

    private fun getSelectedSkill(): AiSkill? {
        val selectedIndex = spinnerSkills.selectedItemPosition
        if (selectedIndex in currentSkillsList.indices) {
            return currentSkillsList[selectedIndex]
        }
        return null
    }

    private fun runSelectedSkill(onComplete: (() -> Unit)? = null) {
        val skill = getSelectedSkill() ?: return

        progressBarSandbox.visibility = View.VISIBLE
        btnRunSkill.isEnabled = false
        tvSkillOutput.text = "Executing skill '${skill.displayName}'..."

        lifecycleScope.launch(Dispatchers.IO) {
            val output = skillManager.executeSkill(
                skill = skill,
                initialInput = articleText
            )
            lastExecutionOutput = output

            withContext(Dispatchers.Main) {
                progressBarSandbox.visibility = View.GONE
                btnRunSkill.isEnabled = true
                tvSkillOutput.text = output.ifBlank { "Executed with empty response." }
                onComplete?.invoke()
            }
        }
    }

    private fun promptForApiKey(onKeySaved: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "Enter Gemini API Key"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Gemini API Key Required")
            .setMessage("Please enter your Gemini API Key to enable auto-tuning.")
            .setView(input)
            .setPositiveButton("Save & Continue") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    preferencesRepository.geminiApiKey = key
                    onKeySaved(key)
                } else {
                    Toast.makeText(requireContext(), "API Key cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runOptimization(apiKey: String, currentSkill: AiSkill) {
        val critique = etUserCritique.text.toString().trim()

        progressBarSandbox.visibility = View.VISIBLE
        btnOptimizeWithGemini.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val result = geminiOptimizer.optimizeSkill(
                apiKey = apiKey,
                articleText = articleText,
                currentSkill = currentSkill,
                gemmaOutput = lastExecutionOutput,
                userCritique = critique.ifBlank { null }
            )

            withContext(Dispatchers.Main) {
                progressBarSandbox.visibility = View.GONE
                btnOptimizeWithGemini.isEnabled = true

                result.fold(
                    onSuccess = { optimizedSkill ->
                        showDiffDialog(currentSkill, optimizedSkill)
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), "Optimization failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun showDiffDialog(oldSkill: AiSkill, newSkill: AiSkill) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_skill_diff, null)
        val containerStepDiffs = dialogView.findViewById<LinearLayout>(R.id.containerStepDiffs)
        val btnToggleRawJson = dialogView.findViewById<Button>(R.id.btnToggleRawJson)
        val tvRawJsonDiff = dialogView.findViewById<TextView>(R.id.tvRawJsonDiff)
        val btnDismissDiff = dialogView.findViewById<Button>(R.id.btnDismissDiff)
        val btnAcceptAndRerun = dialogView.findViewById<Button>(R.id.btnAcceptAndRerun)

        // Populate step-by-step diffs
        val maxSteps = maxOf(oldSkill.steps.size, newSkill.steps.size)
        for (i in 0 until maxSteps) {
            val oldStep = oldSkill.steps.getOrNull(i)
            val newStep = newSkill.steps.getOrNull(i)

            val stepView = TextView(requireContext()).apply {
                val stepTitle = "--- Step ${i + 1} [${newStep?.stepId ?: oldStep?.stepId}] ---"
                val oldPrompt = oldStep?.promptTemplate ?: "(None)"
                val newPrompt = newStep?.promptTemplate ?: "(None)"

                val oldParams = oldStep?.let { "temp: ${it.temperature}, maxTokens: ${it.maxTokens}, penalty: ${it.repeatPenalty}" } ?: ""
                val newParams = newStep?.let { "temp: ${it.temperature}, maxTokens: ${it.maxTokens}, penalty: ${it.repeatPenalty}" } ?: ""

                text = """
                    $stepTitle

                    BEFORE Prompt:
                    $oldPrompt

                    AFTER Prompt:
                    $newPrompt

                    BEFORE Params: $oldParams
                    AFTER Params:  $newParams
                """.trimIndent()
                setPadding(0, 8, 0, 16)
                textSize = 12f
                setTextColor(android.graphics.Color.DKGRAY)
            }
            containerStepDiffs.addView(stepView)
        }

        val rawJsonText = """
            === BEFORE ===
            ${gson.toJson(oldSkill)}

            === AFTER ===
            ${gson.toJson(newSkill)}
        """.trimIndent()
        tvRawJsonDiff.text = rawJsonText

        btnToggleRawJson.setOnClickListener {
            if (tvRawJsonDiff.visibility == View.VISIBLE) {
                tvRawJsonDiff.visibility = View.GONE
                btnToggleRawJson.text = "Show Raw JSON Diff"
            } else {
                tvRawJsonDiff.visibility = View.VISIBLE
                btnToggleRawJson.text = "Hide Raw JSON Diff"
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnDismissDiff.setOnClickListener {
            dialog.dismiss()
        }

        btnAcceptAndRerun.setOnClickListener {
            dialog.dismiss()
            skillManager.saveSkillOverride(newSkill)
            populateSkillsSpinner()

            // Select the updated skill in spinner
            val newIndex = currentSkillsList.indexOfFirst { it.id.equals(newSkill.id, ignoreCase = true) }
            if (newIndex >= 0) {
                spinnerSkills.setSelection(newIndex)
            }

            Toast.makeText(requireContext(), "Saved override! Re-executing...", Toast.LENGTH_SHORT).show()
            runSelectedSkill {
                Toast.makeText(requireContext(), "Skill re-run completed!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
