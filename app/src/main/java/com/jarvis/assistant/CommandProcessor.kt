package com.jarvis.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.core.app.ActivityCompat

interface JarvisCallback {
    fun onUserText(text: String)
    fun onJarvisSpoken(text: String)
    fun onJarvisDetail(text: String)
    fun onImageReady(filePath: String)
    fun onModeChanged(serious: Boolean)
}

/**
 * Parses a heard/typed command and either performs a device action directly,
 * or falls back to asking Gemini. Jarvis only *speaks* a short line; any
 * longer explanation is handed to the callback so the UI can show it as text.
 */
class CommandProcessor(
    private val ctx: Context,
    private val tts: TtsManager,
    private val callback: JarvisCallback
) {

    fun handle(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        callback.onUserText(text)
        val low = text.lowercase()
        val honorific = Prefs.getHonorific(ctx)

        if (Regex("\\b(serious mode|be serious|get serious)\\b").containsMatchIn(low)) {
            Prefs.setSeriousMode(ctx, true)
            callback.onModeChanged(true)
            respond("Serious mode, $honorific.")
            return
        }
        if (Regex("\\b(normal mode|relax|stand down)\\b").containsMatchIn(low)) {
            Prefs.setSeriousMode(ctx, false)
            callback.onModeChanged(false)
            respond("Back to normal, $honorific.")
            return
        }

        // --- Memory commands ---
        if (Regex("\\b(clear (your )?memory|forget everything|wipe your memory)\\b").containsMatchIn(low)) {
            MemoryStore.clearEverything(ctx)
            respond("Memory cleared, $honorific.")
            return
        }
        Regex("^(?:remember that|please remember|remember)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val fact = m.groupValues[1].trim().trimEnd('.', '!')
            if (fact.isNotBlank()) {
                MemoryStore.addFact(ctx, fact)
                respond("Got it, I'll remember that, $honorific.")
            }
            return
        }
        if (Regex("\\b(what do you remember|what have you remembered|list memory|show memory)\\b").containsMatchIn(low)) {
            val facts = MemoryStore.getFacts(ctx)
            if (facts.isEmpty()) {
                respond("I don't have anything stored yet, $honorific.")
            } else {
                respond("I remember ${facts.size} thing${if (facts.size == 1) "" else "s"}, $honorific.")
                callback.onJarvisDetail(facts.joinToString("\n") { "\u2022 $it" })
            }
            return
        }
        Regex("^forget (?:that |about )?(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val keyword = m.groupValues[1].trim()
            val removed = MemoryStore.removeFactContaining(ctx, keyword)
            respond(if (removed) "Forgotten, $honorific." else "I didn't have anything matching that.")
            return
        }

        Regex("^call\\s+(.+)").find(low)?.let { m ->
            val name = m.groupValues[1].trim()
            if (ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                respond("I need call permission first, $honorific.")
                return
            }
            val found = ContactActions.findNumber(ctx, name)
            if (found != null) {
                ContactActions.call(ctx, found.second)
                respond("Calling ${found.first}, $honorific.")
            } else {
                respond("I couldn't find $name in your contacts.")
            }
            return
        }

        Regex("^(?:open|launch|start)\\s+(.+)").find(low)?.let { m ->
            val appName = m.groupValues[1].trim()
            val launched = AppLauncherActions.launch(ctx, appName)
            if (launched != null) respond("Opening $launched.")
            else respond("I couldn't find an app called $appName.")
            return
        }

        Regex("^(?:play|search youtube for|youtube)\\s+(.+)").find(low)?.let { m ->
            val q = m.groupValues[1].trim()
            MediaActions.searchYoutube(ctx, q)
            respond("Searching YouTube for $q.")
            return
        }
        if (Regex("\\b(pause|stop music)\\b").containsMatchIn(low)) {
            MediaActions.mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE); respond("Paused."); return
        }
        if (Regex("\\b(resume|play music)\\b").containsMatchIn(low)) {
            MediaActions.mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY); respond("Playing."); return
        }
        if (Regex("\\bnext (song|track)\\b").containsMatchIn(low)) {
            MediaActions.mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT); respond("Next track."); return
        }
        if (Regex("\\bprevious (song|track)\\b").containsMatchIn(low)) {
            MediaActions.mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS); respond("Previous track."); return
        }
        if (Regex("\\bvolume up\\b").containsMatchIn(low)) { MediaActions.volumeUp(ctx); respond("Volume up."); return }
        if (Regex("\\bvolume down\\b").containsMatchIn(low)) { MediaActions.volumeDown(ctx); respond("Volume down."); return }

        Regex("^(?:generate|create|make|draw)\\s+(?:an?\\s+)?image\\s*(?:of|for|showing)?\\s*(.+)")
            .find(low)?.let { m ->
                val prompt = m.groupValues[1].trim()
                respond("Generating that image, $honorific.")
                Thread {
                    val apiKey = Prefs.getApiKey(ctx)
                    val bytes = GeminiClient.generateImage(apiKey, Prefs.getImageModel(ctx), prompt)
                    if (bytes != null) {
                        val file = java.io.File(ctx.cacheDir, "jarvis_image_${System.currentTimeMillis()}.png")
                        file.writeBytes(bytes)
                        callback.onImageReady(file.absolutePath)
                        tts.speak("Here's your image, $honorific.")
                    } else {
                        tts.speak("The image didn't come through that time.")
                    }
                }.start()
                return
            }

        // Fallback: general question to Gemini.
        Thread {
            val apiKey = Prefs.getApiKey(ctx)
            if (apiKey.isBlank()) {
                tts.speak("I don't have an API key yet. Add one in Settings.")
                callback.onJarvisSpoken("I don't have an API key yet. Add one in Settings.")
                return@Thread
            }
            val serious = Prefs.isSeriousMode(ctx)
            val facts = MemoryStore.getFacts(ctx)
            val factsBlock = if (facts.isNotEmpty())
                "\n\nThings the user has told you to remember, use them when relevant:\n" +
                    facts.joinToString("\n") { "- $it" }
            else ""
            val sys = (
                if (serious)
                    "You are Jarvis, a no-nonsense assistant. Be direct and brief. No pleasantries, no hedging, no filler."
                else
                    "You are Jarvis, a warm, capable voice assistant modeled on a classic AI butler. Occasionally address the user as \"$honorific\"."
                ) + factsBlock + "\n\nAlways answer in exactly this format, nothing else:\n" +
                "SPOKEN: <one short sentence, at most ~20 words, the single most important part, written to be read aloud>\n" +
                "DETAIL: <the fuller answer, as long as needed; repeat SPOKEN here if nothing more to add>"
            // Short-term memory: recent turns give Gemini conversational context.
            // The current turn is already the last transcript entry (added by
            // onUserText above), so drop it — it's passed separately as userText.
            val priorTurns = MemoryStore.getTranscript(ctx)
                .dropLast(1)
                .filter { it.first == "user" || it.first == "jarvis" }
                .takeLast(10)
                .map { Turn(if (it.first == "user") "user" else "model", it.second) }
            val reply = GeminiClient.ask(apiKey, Prefs.getTextModel(ctx), sys, priorTurns, text)
            callback.onJarvisSpoken(reply.spoken)
            tts.speak(reply.spoken)
            if (reply.detail != null && reply.detail != reply.spoken) callback.onJarvisDetail(reply.detail)
        }.start()
    }

    private fun respond(text: String) {
        callback.onJarvisSpoken(text)
        tts.speak(text)
    }
}
