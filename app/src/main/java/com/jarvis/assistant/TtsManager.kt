package com.jarvis.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

class TtsManager(context: Context, private val onDone: (() -> Unit)? = null) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var preferredVoiceName: String? = null
    private var onReady: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                applyVoicePreference()
                pendingText?.let { speak(it) }
                pendingText = null
                onReady?.invoke()
            }
        }
    }

    /** Runs immediately if the engine is already initialized, else once it is. */
    fun whenReady(block: () -> Unit) {
        if (ready) block() else onReady = block
    }

    /**
     * Real Android TTS voices almost never encode gender in their name
     * (e.g. "en-us-x-sfg-local"), so a "female"/"male" name-substring search
     * silently matches nothing on most devices and quietly keeps whatever
     * the engine defaults to — which is why a broken male toggle looked like
     * "only female works". Exposing the actual voice list sidesteps that
     * entirely: the person picks a voice that is verified to exist.
     */
    fun listVoices(): List<Voice> {
        val voices = tts?.voices ?: return emptyList()
        // Deliberately not filtering out network-required voices: on the
        // Google TTS engine, the distinct-sounding accented voices (Indian,
        // British, Australian English, etc.) are frequently network voices —
        // excluding them would remove exactly the accent variety being asked for.
        return voices
            .filter { it.locale.language == "en" }
            .sortedWith(compareBy({ it.locale.toString() }, { it.name }))
    }

    fun setPreferredVoiceName(name: String?) {
        preferredVoiceName = name
        if (ready) applyVoicePreference()
    }

    private fun applyVoicePreference() {
        val name = preferredVoiceName ?: return
        val voices = tts?.voices ?: return
        val match = voices.firstOrNull { it.name == name }
        if (match != null) tts?.voice = match
    }

    fun speak(text: String) {
        if (!ready) { pendingText = text; return }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?) { onDone?.invoke() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
