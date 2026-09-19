package com.jarvis.assistant

import android.content.Context

/**
 * Two kinds of memory:
 *  - "facts": things the user explicitly asked Jarvis to remember, kept
 *    forever until forgotten, and injected into every Gemini call.
 *  - "transcript": a rolling log of what was said, used both to restore the
 *    on-screen conversation after the app restarts, and to give Gemini
 *    short-term conversational context (the last several turns).
 * Stored as plain SharedPreferences strings — no database needed for this size.
 */
object MemoryStore {
    private const val NAME = "jarvis_memory"
    private const val MAX_FACTS = 200
    private const val MAX_TRANSCRIPT_LINES = 300

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private fun sanitize(s: String) = s.replace("\n", " ").replace("|", "/").trim()

    fun getFacts(ctx: Context): List<String> {
        val raw = prefs(ctx).getString("facts", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun addFact(ctx: Context, fact: String) {
        val current = getFacts(ctx).toMutableList()
        current.add(sanitize(fact))
        val capped = if (current.size > MAX_FACTS) current.takeLast(MAX_FACTS) else current
        prefs(ctx).edit().putString("facts", capped.joinToString("\n")).apply()
    }

    /** Returns true if something matching was actually removed. */
    fun removeFactContaining(ctx: Context, keyword: String): Boolean {
        val current = getFacts(ctx)
        val filtered = current.filterNot { it.contains(keyword, ignoreCase = true) }
        if (filtered.size == current.size) return false
        prefs(ctx).edit().putString("facts", filtered.joinToString("\n")).apply()
        return true
    }

    fun clearFacts(ctx: Context) {
        prefs(ctx).edit().remove("facts").apply()
    }

    fun appendTranscript(ctx: Context, kind: String, text: String) {
        val current = prefs(ctx).getString("transcript", "") ?: ""
        val line = "$kind|${sanitize(text)}"
        val lines = (if (current.isBlank()) emptyList() else current.split("\n")) + line
        val capped = if (lines.size > MAX_TRANSCRIPT_LINES) lines.takeLast(MAX_TRANSCRIPT_LINES) else lines
        prefs(ctx).edit().putString("transcript", capped.joinToString("\n")).apply()
    }

    /** List of (kind, text) pairs, oldest first. */
    fun getTranscript(ctx: Context): List<Pair<String, String>> {
        val raw = prefs(ctx).getString("transcript", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull {
            val idx = it.indexOf("|")
            if (idx < 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }
    }

    fun clearTranscript(ctx: Context) {
        prefs(ctx).edit().remove("transcript").apply()
    }

    fun clearEverything(ctx: Context) {
        clearFacts(ctx)
        clearTranscript(ctx)
    }
}
