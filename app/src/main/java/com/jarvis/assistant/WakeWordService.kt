package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps SpeechRecognizer restarting in a loop to
 * simulate always-on listening, watches for the wake word, and dispatches
 * whatever comes after it to CommandProcessor.
 */
class WakeWordService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var awake = false
    private lateinit var tts: TtsManager
    private lateinit var processor: CommandProcessor
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification("Listening for \"${Prefs.getWakeWord(this)}\"…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }

        tts = TtsManager(this) { restartListening() }
        tts.setFemale(Prefs.isFemaleVoice(this))

        processor = CommandProcessor(this, tts, object : JarvisCallback {
            override fun onUserText(text: String) {
                MemoryStore.appendTranscript(this@WakeWordService, "user", text)
                broadcastLine("user", text)
            }
            override fun onJarvisSpoken(text: String) {
                MemoryStore.appendTranscript(this@WakeWordService, "jarvis", text)
                broadcastLine("jarvis", text)
            }
            override fun onJarvisDetail(text: String) {
                MemoryStore.appendTranscript(this@WakeWordService, "detail", text)
                broadcastLine("detail", text)
            }
            override fun onImageReady(filePath: String) = broadcastImage(filePath)
            override fun onModeChanged(serious: Boolean) = broadcastMode(serious)
        })

        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("manual_text")?.let { processor.handle(it) }
        return START_STICKY
    }

    private fun startListening() {
        if (listening || !SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                broadcastRms(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                restartListening()
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                handleHeard(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (text.isNotBlank()) broadcastHeard(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        recognizer?.startListening(intent)
    }

    private fun restartListening() {
        recognizer?.destroy()
        recognizer = null
        handler.postDelayed({ startListening() }, 400)
    }

    private fun handleHeard(text: String) {
        val wake = Prefs.getWakeWord(this)
        val lower = text.lowercase()

        if (!awake) {
            if (lower.contains(wake)) {
                val after = lower.substringAfter(wake).trim()
                if (after.length > 2) {
                    processor.handle(after)
                    return
                } else {
                    awake = true
                    tts.speak("Yes ${Prefs.getHonorific(this)}?")
                    return
                }
            }
        } else {
            awake = false
            processor.handle(text.trim())
            return
        }
        restartListening()
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "jarvis_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun broadcastLine(kind: String, text: String) {
        val i = Intent("com.jarvis.assistant.LOG")
        i.putExtra("kind", kind)
        i.putExtra("text", text)
        sendBroadcast(i)
    }

    private fun broadcastHeard(text: String) {
        val i = Intent("com.jarvis.assistant.HEARD")
        i.putExtra("text", text)
        sendBroadcast(i)
    }

    private fun broadcastImage(filePath: String) {
        val i = Intent("com.jarvis.assistant.IMAGE")
        i.putExtra("path", filePath)
        sendBroadcast(i)
    }

    private fun broadcastRms(rms: Float) {
        val i = Intent("com.jarvis.assistant.RMS")
        i.putExtra("rms", rms)
        sendBroadcast(i)
    }

    private fun broadcastMode(serious: Boolean) {
        val i = Intent("com.jarvis.assistant.MODE")
        i.putExtra("serious", serious)
        sendBroadcast(i)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
