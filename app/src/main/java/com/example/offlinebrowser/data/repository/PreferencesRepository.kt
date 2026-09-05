package com.example.offlinebrowser.data.repository

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("offline_browser_prefs", Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", false)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    var allowedWifiSsids: Set<String>
        get() = prefs.getStringSet("allowed_wifi_ssids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("allowed_wifi_ssids", value).apply()

    var refreshIntervalMinutes: Long
        get() = prefs.getLong("refresh_interval_minutes", 60)
        set(value) = prefs.edit().putLong("refresh_interval_minutes", value).apply()

    var feedLimitCount: Int
        get() = prefs.getInt("feed_limit_count", 50)
        set(value) = prefs.edit().putInt("feed_limit_count", value).apply()

    var feedLimitDays: Int
        get() = prefs.getInt("feed_limit_days", 30)
        set(value) = prefs.edit().putInt("feed_limit_days", value).apply()

    var weatherUnits: String
        get() = prefs.getString("weather_units", "metric") ?: "metric"
        set(value) = prefs.edit().putString("weather_units", value).apply()

    var weatherRefreshIntervalMinutes: Long
        get() = prefs.getLong("weather_refresh_interval_minutes", 60)
        set(value) = prefs.edit().putLong("weather_refresh_interval_minutes", value.coerceAtLeast(15)).apply()

    var weatherForecastDays: Int
        get() = prefs.getInt("weather_forecast_days", 7)
        set(value) = prefs.edit().putInt("weather_forecast_days", value.coerceIn(1, 16)).apply()

    var detailedDebuggingEnabled: Boolean
        get() = prefs.getBoolean("detailed_debugging_enabled", false)
        set(value) = prefs.edit().putBoolean("detailed_debugging_enabled", value).apply()

    var showArticleThumbnails: Boolean
        get() = prefs.getBoolean("show_article_thumbnails", true)
        set(value) = prefs.edit().putBoolean("show_article_thumbnails", value).apply()

    var showImagesInArticleView: Boolean
        get() = prefs.getBoolean("show_images_in_article_view", true)
        set(value) = prefs.edit().putBoolean("show_images_in_article_view", value).apply()

    var gemmaModelPath: String?
        get() = prefs.getString("gemma_model_path", null)
        set(value) = prefs.edit().putString("gemma_model_path", value).apply()

    var gemmaModelUrl: String
        get() = prefs.getString("gemma_model_url", "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf") ?: "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf"
        set(value) = prefs.edit().putString("gemma_model_url", value).apply()

    var geminiApiKey: String?
        get() = prefs.getString("gemini_api_key", null)
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var enabledSkillIds: Set<String>?
        get() = prefs.getStringSet("enabled_skill_ids", null)
        set(value) = prefs.edit().putStringSet("enabled_skill_ids", value).apply()

    fun isSkillEnabled(skillId: String): Boolean {
        val enabledSet = enabledSkillIds
        return enabledSet == null || enabledSet.contains(skillId)
    }

    fun setSkillEnabled(skillId: String, enabled: Boolean, allSkillIds: List<String> = emptyList()) {
        val currentSet = (enabledSkillIds ?: allSkillIds.toSet()).toMutableSet()
        if (enabled) {
            currentSet.add(skillId)
        } else {
            currentSet.remove(skillId)
        }
        enabledSkillIds = currentSet
    }
}
