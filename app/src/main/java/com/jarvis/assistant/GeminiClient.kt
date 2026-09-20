package com.jarvis.assistant

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GeminiReply(val spoken: String, val detail: String?)
data class Turn(val role: String, val text: String) // role: "user" or "model"

/**
 * Minimal client for the Gemini generateContent REST API using only
 * classes already bundled with Android (no extra HTTP library needed).
 * Must be called off the main thread.
 */
object GeminiClient {

    fun ask(
        apiKey: String,
        model: String,
        systemPrompt: String,
        priorTurns: List<Turn>,
        userText: String
    ): GeminiReply {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val contents = JSONArray()
            for (t in priorTurns) {
                if (t.text.isBlank()) continue
                contents.put(JSONObject().apply {
                    put("role", t.role)
                    put("parts", JSONArray().put(JSONObject().put("text", t.text)))
                })
            }
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userText)))
            })
            val body = JSONObject().apply {
                put(
                    "system_instruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                )
                put("contents", contents)
            }
            val raw = postJson(url, body) ?: return GeminiReply("I couldn't reach Gemini.", null)
            val json = JSONObject(raw)
            if (json.has("error")) {
                return GeminiReply("Gemini error.", json.getJSONObject("error").optString("message"))
            }
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return GeminiReply("No response from Gemini.", null)
            }
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val text = StringBuilder()
            for (i in 0 until parts.length()) {
                text.append(parts.getJSONObject(i).optString("text", ""))
            }
            val full = text.toString().trim()
            val spokenMatch = Regex("SPOKEN:\\s*([\\s\\S]*?)\\n\\s*DETAIL:", RegexOption.IGNORE_CASE).find(full)
            val detailMatch = Regex("DETAIL:\\s*([\\s\\S]*)", RegexOption.IGNORE_CASE).find(full)
            if (spokenMatch != null) {
                GeminiReply(spokenMatch.groupValues[1].trim(), detailMatch?.groupValues?.get(1)?.trim())
            } else {
                GeminiReply(if (full.length > 200) full.take(180) + "…" else full, full)
            }
        } catch (e: Exception) {
            GeminiReply("I couldn't reach Gemini: ${e.message}", null)
        }
    }

    data class ImageResult(val bytes: ByteArray?, val error: String?)

    fun generateImage(apiKey: String, model: String, prompt: String): ImageResult {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    })
                )
                put(
                    "generationConfig",
                    JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                )
            }
            val raw = postJson(url, body) ?: return ImageResult(null, "No response from the network request.")
            val json = JSONObject(raw)
            if (json.has("error")) {
                return ImageResult(null, json.getJSONObject("error").optString("message", "Unknown API error"))
            }
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return ImageResult(null, "The model returned no candidates. Raw response: ${raw.take(300)}")
            }
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
                if (inline != null) {
                    val b64 = inline.optString("data")
                    return ImageResult(Base64.decode(b64, Base64.DEFAULT), null)
                }
            }
            // Got a response, but no image part — usually means the model
            // replied with text only (e.g. it refused, or the account/key
            // doesn't have image-generation access enabled).
            val textOnly = parts.joinToString(" ") { it.optString("text", "") }.trim()
            ImageResult(null, if (textOnly.isNotBlank()) "Model replied with text instead of an image: $textOnly" else "No image data in the response.")
        } catch (e: Exception) {
            ImageResult(null, e.message ?: "Unknown error")
        }
    }

    private fun postJson(url: URL, body: JSONObject): String? {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
