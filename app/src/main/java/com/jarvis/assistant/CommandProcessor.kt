package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
    // Known sites with a direct search URL. Opening these via ACTION_VIEW lets
    // Android itself decide whether to hand it to an installed app (YouTube,
    // Amazon, etc.) or fall back to the browser — we don't need to care which.
    private val siteSearchUrls: Map<String, (String) -> String> = mapOf(
        "youtube" to { q: String -> "https://www.youtube.com/results?search_query=${Uri.encode(q)}" },
        "google" to { q: String -> "https://www.google.com/search?q=${Uri.encode(q)}" },
        "amazon" to { q: String -> "https://www.amazon.com/s?k=${Uri.encode(q)}" },
        "spotify" to { q: String -> "https://open.spotify.com/search/${Uri.encode(q)}" },
        "github" to { q: String -> "https://github.com/search?q=${Uri.encode(q)}" },
        "wikipedia" to { q: String -> "https://en.wikipedia.org/wiki/Special:Search?search=${Uri.encode(q)}" },
        "reddit" to { q: String -> "https://www.reddit.com/search/?q=${Uri.encode(q)}" },
        "maps" to { q: String -> "https://www.google.com/maps/search/${Uri.encode(q)}" }
    )

    private fun openSiteSearch(site: String, query: String, honorific: String) {
        val builder = siteSearchUrls[site] ?: siteSearchUrls["google"]!!
        val label = site.replaceFirstChar { it.uppercase() }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(builder(query)))
        SystemLauncher.launch(ctx, intent, label)
        respond("Searching $label for $query.")
    }

    fun handle(rawText: String) {
        // A crash anywhere in here (or in the background threads it spawns)
        // used to be able to kill the entire app process silently — which
        // looks exactly like "voice and text both stopped responding at
        // once" from outside. Everything routes through this guard now so a
        // single bad command can't take the whole assistant down with it.
        try {
            handleInternal(rawText)
        } catch (e: Exception) {
            callback.onJarvisSpoken("Something went wrong with that command.")
            callback.onJarvisDetail("Error in handle(): ${e.javaClass.simpleName}: ${e.message}")
            try { tts.speak("Something went wrong with that command.") } catch (e2: Exception) {}
        }
    }

    private fun handleInternal(rawText: String) {
        val rawTrimmed = rawText.trim()
        if (rawTrimmed.isEmpty()) return
        callback.onUserText(rawTrimmed)
        // "yt" is an extremely common shorthand that speech recognition also
        // often produces literally, so normalize it before any matching.
        val text = Regex("\\byt\\b", RegexOption.IGNORE_CASE).replace(rawTrimmed, "youtube")
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

        // --- Flexible "open/go on/go to SITE (and) search for QUERY" phrasing ---
        // Handles: "open youtube and search for X", "go on youtube search for X",
        // "go to youtube and search X", etc. — any of open/go on/go to, any of
        // search/search for, with or without "and" in between.
        Regex("^(?:open|go (?:on|to)|launch|start)\\s+(\\w+)\\s+(?:and\\s+)?search(?:\\s+for)?\\s+(.+)")
            .find(low)?.let { m ->
                val site = m.groupValues[1].trim()
                val query = m.groupValues[2].trim()
                openSiteSearch(site, query, honorific)
                return
            }
        // Reversed phrasing: "search for X on youtube" / "search X in google"
        Regex("^search(?:\\s+for)?\\s+(.+?)\\s+(?:on|in)\\s+(\\w+)$")
            .find(low)?.let { m ->
                val query = m.groupValues[1].trim()
                val site = m.groupValues[2].trim()
                openSiteSearch(site, query, honorific)
                return
            }
        // "youtube search X" / "google search X"
        Regex("^(${siteSearchUrls.keys.joinToString("|")})\\s+search(?:\\s+for)?\\s+(.+)")
            .find(low)?.let { m ->
                openSiteSearch(m.groupValues[1].trim(), m.groupValues[2].trim(), honorific)
                return
            }
        // "search google for X" / "search youtube for X" — site named right
        // after "search", before "for". Must come before the bare "search for
        // X" fallback below, or "google"/"youtube" would end up inside the
        // query text instead of being read as the destination site.
        Regex("^search\\s+(${siteSearchUrls.keys.joinToString("|")})\\s+for\\s+(.+)")
            .find(low)?.let { m ->
                openSiteSearch(m.groupValues[1].trim(), m.groupValues[2].trim(), honorific)
                return
            }
        // Bare "search for X" or "google X" — no app/browser required either
        // way, this always has somewhere to go: Google.
        Regex("^google\\s+(.+)").find(low)?.let { m ->
            openSiteSearch("google", m.groupValues[1].trim(), honorific)
            return
        }
        Regex("^search(?:\\s+for)?\\s+(.+)").find(low)?.let { m ->
            openSiteSearch("google", m.groupValues[1].trim(), honorific)
            return
        }

        Regex("^(?:open|launch|start)\\s+(.+)").find(low)?.let { m ->
            val appName = m.groupValues[1].trim()
            val launched = AppLauncherActions.launch(ctx, appName)
            if (launched != null) {
                respond("Opening $launched.")
            } else {
                // No matching app installed — go to the web instead of just
                // giving up, exactly like a real assistant would.
                MediaActions.searchGoogle(ctx, appName)
                respond("I don't have $appName installed, so I searched the web for it instead.")
            }
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
        // Bare "play X" (a song/video name) — falls through to here only once
        // "play music"/"resume" above didn't match, so this is safe as a
        // catch-all for "play believer by imagine dragons" style requests.
        Regex("^play\\s+(.+)").find(low)?.let { m ->
            openSiteSearch("youtube", m.groupValues[1].trim(), honorific)
            return
        }

        Regex("^(?:generate|create|make|draw)\\s+(?:an?\\s+)?image\\s*(?:of|for|showing)?\\s*(.+)")
            .find(low)?.let { m ->
                val prompt = m.groupValues[1].trim()
                respond("Generating that image, $honorific.")
                Thread {
                    try {
                        val apiKey = Prefs.getApiKey(ctx)
                        val result = GeminiClient.generateImage(apiKey, Prefs.getImageModel(ctx), prompt)
                        if (result.bytes != null) {
                            val file = java.io.File(ctx.cacheDir, "jarvis_image_${System.currentTimeMillis()}.png")
                            file.writeBytes(result.bytes)
                            callback.onImageReady(file.absolutePath)
                            tts.speak("Here's your image, $honorific.")
                        } else {
                            // Speak the real reason instead of a generic failure —
                            // this is almost always a billing/quota/model-access
                            // issue on the API key, not a bug, so it's worth seeing.
                            callback.onJarvisDetail("Image generation failed: ${result.error}")
                            tts.speak("That image didn't work. I've put the exact error on screen.")
                        }
                    } catch (e: Exception) {
                        // An uncaught exception on a background thread kills the
                        // whole app process in Android — this is what stood
                        // between "the image failed" and "everything went silent".
                        callback.onJarvisDetail("Image generation crashed: ${e.javaClass.simpleName}: ${e.message}")
                        try { tts.speak("That image request crashed.") } catch (e2: Exception) {}
                    }
                }.start()
                return
            }

        // Fallback: general question to Gemini.
        Thread {
            try {
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
            } catch (e: Exception) {
                callback.onJarvisDetail("Gemini call crashed: ${e.javaClass.simpleName}: ${e.message}")
                try { tts.speak("Something went wrong reaching Gemini.") } catch (e2: Exception) {}
            }
        }.start()
    }

    private fun respond(text: String) {
        callback.onJarvisSpoken(text)
        tts.speak(text)
    }
}
