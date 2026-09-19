package com.jarvis.assistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apiKeyInput.setText(Prefs.getApiKeyOverride(this))
        binding.wakeWordInput.setText(Prefs.getWakeWord(this))
        binding.aliasesInput.setText(Prefs.getAliasesRaw(this))
        binding.textModelInput.setText(Prefs.getTextModel(this))
        binding.imageModelInput.setText(Prefs.getImageModel(this))
        binding.honorificSwitch.isChecked = Prefs.getHonorific(this) == "ma'am"
        binding.femaleVoiceSwitch.isChecked = Prefs.isFemaleVoice(this)

        binding.saveButton.setOnClickListener {
            Prefs.setApiKey(this, binding.apiKeyInput.text.toString().trim())
            Prefs.setWakeWord(this, binding.wakeWordInput.text.toString().trim().ifBlank { "jarvis" })
            Prefs.setAliasesRaw(this, binding.aliasesInput.text.toString().trim())
            Prefs.setTextModel(this, binding.textModelInput.text.toString())
            Prefs.setImageModel(this, binding.imageModelInput.text.toString())
            Prefs.setHonorific(this, if (binding.honorificSwitch.isChecked) "ma'am" else "sir")
            Prefs.setFemaleVoice(this, binding.femaleVoiceSwitch.isChecked)
            finish()
        }

        binding.clearMemoryButton.setOnClickListener {
            MemoryStore.clearEverything(this)
            android.widget.Toast.makeText(this, "Memory cleared", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
