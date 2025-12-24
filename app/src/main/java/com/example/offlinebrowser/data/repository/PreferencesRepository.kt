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
}
