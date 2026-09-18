package com.jarvis.assistant

import android.content.Context

object Prefs {
    private const val NAME = "jarvis_prefs"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getApiKey(ctx: Context): String {
        val saved = prefs(ctx).getString("api_key", "") ?: ""
        return saved.ifBlank { BuildConfig.GEMINI_API_KEY }
    }
    fun getApiKeyOverride(ctx: Context): String = prefs(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, v: String) = prefs(ctx).edit().putString("api_key", v).apply()

    fun getWakeWord(ctx: Context): String = prefs(ctx).getString("wake_word", "jarvis") ?: "jarvis"
    fun setWakeWord(ctx: Context, v: String) = prefs(ctx).edit().putString("wake_word", v.lowercase()).apply()

    fun getHonorific(ctx: Context): String = prefs(ctx).getString("honorific", "sir") ?: "sir"
    fun setHonorific(ctx: Context, v: String) = prefs(ctx).edit().putString("honorific", v).apply()

    fun isFemaleVoice(ctx: Context): Boolean = prefs(ctx).getBoolean("female_voice", false)
    fun setFemaleVoice(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("female_voice", v).apply()

    fun isSeriousMode(ctx: Context): Boolean = prefs(ctx).getBoolean("serious_mode", false)
    fun setSeriousMode(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("serious_mode", v).apply()

    fun getTextModel(ctx: Context): String =
        prefs(ctx).getString("text_model", "gemini-3.8-flash") ?: "gemini-3.8-flash"

fun getImageModel(ctx: Context): String =
    prefs(ctx).getString("image_model", "gemini-3.5-flash-image-preview")
        ?: "gemini-3.5-flash-image-preview"

    fun getAliases(ctx: Context): Map<String, String> {
        val raw = getAliasesRaw(ctx)
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull {
            val parts = it.split("=")
            if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
        }.toMap()
    }
    fun setAliasesRaw(ctx: Context, v: String) = prefs(ctx).edit().putString("aliases", v).apply()
    fun getAliasesRaw(ctx: Context): String = prefs(ctx).getString("aliases", "") ?: ""
}
