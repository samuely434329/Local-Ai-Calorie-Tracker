package com.imagecaltracker.assistant

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Minimal REST client for the Gemini API. Uses HttpURLConnection so we don't
 * pull in a second HTTP dependency — the ViewModel already downloads model
 * files with the same primitive.
 *
 * Not thread-safe; instances are cheap so callers should construct per-request
 * if they want to change the API key.
 */
class GeminiBackend(private val apiKey: String) {

    val hasKey: Boolean = apiKey.isNotBlank()

    suspend fun chat(userMessage: String): String? = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            systemInstruction = "You are a diet assistant. Keep replies short.",
            userText = userMessage.trim(),
        )
        val raw = callApi(body) ?: return@withContext null
        extractText(raw)?.takeIf { it.isNotBlank() }
    }

    suspend fun estimateMacros(description: String): MacroEstimate? = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            systemInstruction = "Estimate macronutrients. Reply ONE line JSON: " +
                "{\"name\":string,\"calories\":int,\"proteinG\":int,\"carbsG\":int,\"fatsG\":int}",
            userText = description.trim(),
        )
        val raw = callApi(body) ?: return@withContext null
        val text = extractText(raw) ?: return@withContext null
        parseMacroJson(text)
    }

    private fun buildRequestBody(systemInstruction: String, userText: String): String {
        val json = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userText))),
                ),
            )
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction)),
                ),
            )
        }
        return json.toString()
    }

    private fun callApi(body: String): String? {
        val url = URL("$ENDPOINT?key=$apiKey")
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 45_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "ImageCalTracker/1.0")
            }
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                Log.w(TAG, "Gemini HTTP $code: ${response.take(300)}")
                return null
            }
            response
        } catch (t: Throwable) {
            Log.w(TAG, "Gemini call failed: ${t.message}", t)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractText(response: String): String? = try {
        val candidates = JSONObject(response).optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return null
        buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Parse failed: ${t.message}")
        null
    }

    private fun parseMacroJson(raw: String): MacroEstimate? {
        val open = raw.indexOf('{')
        val close = raw.lastIndexOf('}')
        if (open < 0 || close <= open) return null
        val body = raw.substring(open, close + 1)
        return try {
            val obj = JSONObject(body)
            MacroEstimate(
                name = obj.optString("name", "").ifBlank { "Meal" },
                calories = obj.optInt("calories", -1).takeIf { it >= 0 } ?: return null,
                proteinG = obj.optInt("proteinG", 0),
                carbsG = obj.optInt("carbsG", 0),
                fatsG = obj.optInt("fatsG", 0),
                source = MacroSource.Llm,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Macro JSON parse failed: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GeminiBackend"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    }
}
