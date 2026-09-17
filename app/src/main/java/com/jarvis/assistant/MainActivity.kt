package com.jarvis.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serviceRunning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.jarvis.assistant.LOG" -> {
                    val kind = intent.getStringExtra("kind") ?: "sys"
                    val text = intent.getStringExtra("text") ?: return
                    appendLine(kind, text)
                }
                "com.jarvis.assistant.HEARD" -> {
                    binding.hearingText.text = intent.getStringExtra("text") ?: ""
                }
                "com.jarvis.assistant.IMAGE" -> {
                    val path = intent.getStringExtra("path") ?: return
                    val bmp = BitmapFactory.decodeFile(path)
                    if (bmp != null) {
                        binding.generatedImage.setImageBitmap(bmp)
                        binding.generatedImage.visibility = View.VISIBLE
                    }
                }
                "com.jarvis.assistant.RMS" -> {
                    val rms = intent.getFloatExtra("rms", 0f)
                    // SpeechRecognizer's dB scale is roughly 0..10; normalize to 0..1.
                    binding.sphereView.setEnergy((rms / 8f).coerceIn(0f, 1f))
                }
                "com.jarvis.assistant.MODE" -> {
                    val serious = intent.getBooleanExtra("serious", false)
                    applyModeUi(serious)
                }
            }
        }
    }

    private val requiredPermissions: Array<String> by lazy {
        val base = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) base.add(Manifest.permission.POST_NOTIFICATIONS)
        base.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyModeUi(Prefs.isSeriousMode(this))

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.modePill.setOnClickListener {
            val newSerious = !Prefs.isSeriousMode(this)
            Prefs.setSeriousMode(this, newSerious)
            applyModeUi(newSerious)
        }
        binding.toggleButton.setOnClickListener { toggleService() }
        binding.sendButton.setOnClickListener {
            val text = binding.chatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.chatInput.setText("")
                if (!serviceRunning) toggleService()
                val i = Intent(this, WakeWordService::class.java)
                i.putExtra("manual_text", text)
                ContextCompat.startForegroundService(this, i)
            }
        }

        requestPermissionsIfNeeded()
    }

    private fun applyModeUi(serious: Boolean) {
        binding.sphereView.setSerious(serious)
        binding.modePill.text = if (serious) "SERIOUS" else "NORMAL"
        binding.modePill.setBackgroundResource(if (serious) R.drawable.bg_pill_red else R.drawable.bg_pill_blue)
        binding.modePill.setTextColor(
            resources.getColor(if (serious) R.color.danger else R.color.accent, theme)
        )
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun toggleService() {
        val intent = Intent(this, WakeWordService::class.java)
        if (!serviceRunning) {
            ContextCompat.startForegroundService(this, intent)
            serviceRunning = true
            binding.toggleButton.text = "Stop Jarvis"
            binding.toggleButton.setBackgroundResource(R.drawable.bg_toggle_on)
            binding.statusText.text = "Say \"${Prefs.getWakeWord(this)}\" to begin"
        } else {
            stopService(intent)
            serviceRunning = false
            binding.toggleButton.text = "Start Jarvis"
            binding.toggleButton.setBackgroundResource(R.drawable.bg_toggle_off)
            binding.statusText.text = "Off"
            binding.sphereView.setEnergy(0.08f)
        }
    }

    private fun appendLine(kind: String, text: String) {
        val prefix = when (kind) {
            "user" -> "\u203a "
            "jarvis" -> "Jarvis: "
            else -> ""
        }
        binding.logText.append("\n$prefix$text")
        binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction("com.jarvis.assistant.LOG")
            addAction("com.jarvis.assistant.HEARD")
            addAction("com.jarvis.assistant.IMAGE")
            addAction("com.jarvis.assistant.RMS")
            addAction("com.jarvis.assistant.MODE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }
}
