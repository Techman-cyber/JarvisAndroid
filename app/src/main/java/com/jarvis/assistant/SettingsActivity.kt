package com.jarvis.assistant

import android.os.Bundle
import android.speech.tts.Voice
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivitySettingsBinding
import com.jarvis.assistant.databinding.ItemAliasRowBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var tts: TtsManager
    private var voices: List<Voice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apiKeyInput.setText(Prefs.getApiKeyOverride(this))
        binding.wakeWordInput.setText(Prefs.getWakeWord(this))
        binding.textModelInput.setText(Prefs.getTextModel(this))
        binding.imageModelInput.setText(Prefs.getImageModel(this))
        binding.honorificSwitch.isChecked = Prefs.getHonorific(this) == "ma'am"

        loadAliasRows()
        binding.addAliasButton.setOnClickListener { addAliasRow("", "") }

        // A short-lived TTS instance just to enumerate voices for the picker —
        // separate from the one the background service uses.
        tts = TtsManager(this)
        binding.voiceSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("Loading voices\u2026")
        )
        tts.whenReady { populateVoiceSpinner() }
        binding.testVoiceButton.setOnClickListener { testSelectedVoice() }

        binding.saveButton.setOnClickListener {
            Prefs.setApiKey(this, binding.apiKeyInput.text.toString().trim())
            Prefs.setWakeWord(this, binding.wakeWordInput.text.toString().trim().ifBlank { "jarvis" })
            Prefs.setAliasesRaw(this, serializeAliasRows())
            Prefs.setTextModel(this, binding.textModelInput.text.toString())
            Prefs.setImageModel(this, binding.imageModelInput.text.toString())
            Prefs.setHonorific(this, if (binding.honorificSwitch.isChecked) "ma'am" else "sir")
            val selected = voices.getOrNull(binding.voiceSpinner.selectedItemPosition)
            if (selected != null) Prefs.setVoiceName(this, selected.name)
            finish()
        }

        binding.clearMemoryButton.setOnClickListener {
            MemoryStore.clearEverything(this)
            Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testSelectedVoice() {
        val selected = voices.getOrNull(binding.voiceSpinner.selectedItemPosition)
        if (selected == null) {
            Toast.makeText(this, "No voice selected yet", Toast.LENGTH_SHORT).show()
            return
        }
        // Preview immediately without needing to hit Save first.
        tts.setPreferredVoiceName(selected.name)
        val honorific = if (binding.honorificSwitch.isChecked) "ma'am" else "sir"
        tts.speak("Hello $honorific, this is what I sound like.")
    }

    private fun populateVoiceSpinner() {
        voices = tts.listVoices()
        val labels = voices.map { "${it.locale} \u2014 ${it.name}" }
            .ifEmpty { listOf("No English voices found on this device") }
        runOnUiThread {
            binding.voiceSpinner.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item, labels
            )
            val savedName = Prefs.getVoiceName(this)
            val savedIndex = voices.indexOfFirst { it.name == savedName }
            if (savedIndex >= 0) binding.voiceSpinner.setSelection(savedIndex)
        }
    }

    /** One row per saved alias, or a single empty row if there are none yet. */
    private fun loadAliasRows() {
        val aliases = Prefs.getAliases(this)
        if (aliases.isEmpty()) {
            addAliasRow("", "")
        } else {
            aliases.forEach { (key, value) -> addAliasRow(key, value) }
        }
    }

    private fun addAliasRow(key: String, value: String) {
        val rowBinding = ItemAliasRowBinding.inflate(
            LayoutInflater.from(this), binding.aliasRowsContainer, false
        )
        rowBinding.aliasKeyInput.setText(key)
        rowBinding.aliasValueInput.setText(value)
        rowBinding.removeAliasButton.setOnClickListener {
            binding.aliasRowsContainer.removeView(rowBinding.root)
        }
        binding.aliasRowsContainer.addView(rowBinding.root)
    }

    /** Walks every row currently in the container and rebuilds the stored format. */
    private fun serializeAliasRows(): String {
        val pairs = mutableListOf<String>()
        for (i in 0 until binding.aliasRowsContainer.childCount) {
            val row = binding.aliasRowsContainer.getChildAt(i)
            val keyInput = row.findViewById<EditText>(R.id.aliasKeyInput)
            val valueInput = row.findViewById<EditText>(R.id.aliasValueInput)
            val key = keyInput?.text?.toString()?.trim().orEmpty()
            val value = valueInput?.text?.toString()?.trim().orEmpty()
            if (key.isNotEmpty() && value.isNotEmpty()) pairs.add("$key=$value")
        }
        return pairs.joinToString(";")
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
