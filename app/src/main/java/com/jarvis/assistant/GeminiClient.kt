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
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )

            val contents = JSONArray()

            for (turn in priorTurns) {
                if (turn.text.isBlank()) continue

                contents.put(
                    JSONObject().apply {
                        put("role", turn.role)
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", turn.text)
                            )
                        )
                    }
                )
            }

            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", userText)
                        )
                    )
                }
            )

            val body = JSONObject().apply {
                put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", systemPrompt)
                        )
                    )
                )
                put("contents", contents)
            }

            val raw = postJson(url, body)
                ?: return GeminiReply("I couldn't reach Gemini.", null)

            val json = JSONObject(raw)

            if (json.has("error")) {
                return GeminiReply(
                    "Gemini error.",
                    json.getJSONObject("error").optString("message")
                )
            }

            val candidates = json.optJSONArray("candidates")

            if (candidates == null || candidates.length() == 0) {
                return GeminiReply("No response from Gemini.", null)
            }

            val parts = candidates
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            val text = StringBuilder()

            for (i in 0 until parts.length()) {
                text.append(
                    parts
                        .optJSONObject(i)
                        ?.optString("text", "")
                        .orEmpty()
                )
            }

            val full = text.toString().trim()

            val spokenMatch = Regex(
                "SPOKEN:\\s*([\\s\\S]*?)\\n\\s*DETAIL:",
                RegexOption.IGNORE_CASE
            ).find(full)

            val detailMatch = Regex(
                "DETAIL:\\s*([\\s\\S]*)",
                RegexOption.IGNORE_CASE
            ).find(full)

            if (spokenMatch != null) {
                GeminiReply(
                    spoken = spokenMatch.groupValues[1].trim(),
                    detail = detailMatch
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                )
            } else {
                GeminiReply(
                    spoken = if (full.length > 200) {
                        full.take(180) + "…"
                    } else {
                        full
                    },
                    detail = full
                )
            }
        } catch (e: Exception) {
            GeminiReply(
                "I couldn't reach Gemini: ${e.message}",
                null
            )
        }
    }

    data class ImageResult(
        val bytes: ByteArray?,
        val error: String?
    )

    fun generateImage(
        apiKey: String,
        model: String,
        prompt: String
    ): ImageResult {
        return try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )

            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", prompt)
                                )
                            )
                        }
                    )
                )

                put(
                    "generationConfig",
                    JSONObject().put(
                        "responseModalities",
                        JSONArray()
                            .put("TEXT")
                            .put("IMAGE")
                    )
                )
            }

            val raw = postJson(url, body)
                ?: return ImageResult(
                    bytes = null,
                    error = "No response from the network request."
                )

            val json = JSONObject(raw)

            if (json.has("error")) {
                return ImageResult(
                    bytes = null,
                    error = json
                        .getJSONObject("error")
                        .optString("message", "Unknown API error")
                )
            }

            val candidates = json.optJSONArray("candidates")

            if (candidates == null || candidates.length() == 0) {
                return ImageResult(
                    bytes = null,
                    error = "The model returned no candidates. " +
                        "Raw response: ${raw.take(300)}"
                )
            }

            val parts = candidates
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue

                val inlineData =
                    part.optJSONObject("inlineData")
                        ?: part.optJSONObject("inline_data")

                if (inlineData != null) {
                    val base64Data = inlineData.optString("data")

                    if (base64Data.isNotBlank()) {
                        return ImageResult(
                            bytes = Base64.decode(
                                base64Data,
                                Base64.DEFAULT
                            ),
                            error = null
                        )
                    }
                }
            }

            /*
             * JSONArray is not an Iterable, so joinToString cannot be
             * called directly on parts. Iterate through it by index.
             */
            val textOnly = buildString {
                for (i in 0 until parts.length()) {
                    if (i > 0) {
                        append(' ')
                    }

                    append(
                        parts
                            .optJSONObject(i)
                            ?.optString("text", "")
                            .orEmpty()
                    )
                }
            }.trim()

            ImageResult(
                bytes = null,
                error = if (textOnly.isNotBlank()) {
                    "Model replied with text instead of an image: $textOnly"
                } else {
                    "No image data in the response."
                }
            )
        } catch (e: Exception) {
            ImageResult(
                bytes = null,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun postJson(
        url: URL,
        body: JSONObject
    ): String? {
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            connection.outputStream.use { outputStream ->
                outputStream.write(
                    body.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val responseStream =
                if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            responseStream?.bufferedReader()?.use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }
}
