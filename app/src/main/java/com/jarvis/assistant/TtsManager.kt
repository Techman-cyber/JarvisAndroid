package com.jarvis.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class TtsManager(context: Context, private val onDone: (() -> Unit)? = null) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var wantFemale = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                applyVoicePreference()
                pendingText?.let { speak(it) }
                pendingText = null
            }
        }
    }

    fun setFemale(female: Boolean) {
        wantFemale = female
        if (ready) applyVoicePreference()
    }

    private fun applyVoicePreference() {
        val voices = tts?.voices ?: return
        val match = voices.firstOrNull {
            it.locale == Locale.US &&
                (if (wantFemale) it.name.contains("female", true) else it.name.contains("male", true))
        }
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
