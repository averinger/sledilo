package com.violetbit.app

import android.content.Context

data class AppSettings(
    val thresholdHours: Int = 5,
    val customPhrases: List<String> = emptyList(),
    val useCustomPhrases: Boolean = false,
    val noProfanity: Boolean = false
)

class SettingsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("sledilo_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val hours = prefs.getInt("threshold_hours", 5)
        val useCustom = prefs.getBoolean("use_custom", false)
        val noProf = prefs.getBoolean("no_profanity", false)
        val phrasesStr = prefs.getString("custom_phrases", "") ?: ""
        val phrases = if (phrasesStr.isBlank()) emptyList() else phrasesStr.split("|||")
        return AppSettings(hours, phrases, useCustom, noProf)
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putInt("threshold_hours", s.thresholdHours)
            .putBoolean("use_custom", s.useCustomPhrases)
            .putBoolean("no_profanity", s.noProfanity)
            .putString("custom_phrases", s.customPhrases.joinToString("|||"))
            .apply()
    }
}
