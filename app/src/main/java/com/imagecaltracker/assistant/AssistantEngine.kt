package com.imagecaltracker.assistant

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device LLM wrapper for the in-app assistant.
 *
 * Using LiteRT-LM (replaces deprecated MediaPipe LlmInference).
 */
class AssistantEngine(private val appContext: Context) {

    private var engine: Engine? = null
    private var loadAttempted: Boolean = false
    private val initLock = Mutex()

    val usingFallback: Boolean
        get() = loadAttempted && engine == null

    val expectedModelPath: String
        get() = modelFile().absolutePath

    private fun modelFile(): File {
        val dir = File(appContext.getExternalFilesDir(null), "llm").apply { mkdirs() }
        return File(dir, "model.litertlm")
    }

    private suspend fun ensureLoaded(): Engine? = initLock.withLock {
        if (engine != null) return engine

        val file = modelFile()
        if (!file.exists() || file.length() == 0L) {
            loadAttempted = true
            Log.i(TAG, "No LLM model at ${file.absolutePath}")
            return null
        }

        return try {
            val config = EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU() // Can be Backend.GPU() if supported
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            loadAttempted = true
            newEngine
        } catch (t: Throwable) {
            loadAttempted = true
            Log.w(TAG, "Failed to initialise LiteRT-LM, using fallback: ${t.message}", t)
            null
        }
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {}
        engine = null
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.Default) {
        val prompt = buildString {
            append("<|im_start|>system\n")
            append("You are a friendly diet and nutrition assistant. Keep replies short (2-4 sentences).<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userMessage.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
        runLlm(prompt) ?: fallbackChat(userMessage)
    }

    suspend fun estimateMacros(description: String): MacroEstimate = withContext(Dispatchers.Default) {
        val prompt = buildString {
            append("<|im_start|>system\n")
            append("Estimate calories/macros. Reply with ONE line of strict JSON: ")
            append("{\"name\":string,\"calories\":int,\"proteinG\":int,\"carbsG\":int,\"fatsG\":int}<|im_end|>\n")
            append("<|im_start|>user\n")
            append(description.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
        val raw = runLlm(prompt)
        raw?.let { parseMacroJson(it) } ?: error("Failed to generate MacroEstimate")
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private suspend fun runLlm(prompt: String): String? {
        val currentEngine = ensureLoaded() ?: return null
        return try {
            currentEngine.createConversation().use { conversation ->
                val response = conversation.sendMessage(prompt)
                val text = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                text.trim().takeIf { it.isNotEmpty() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "sendMessage failed: ${t.message}", t)
            null
        }
    }

    private fun fallbackChat(userMessage: String): String {
        return "I'm a small offline diet assistant. Add a model.litertlm file for smarter answers."
    }

    private fun parseMacroJson(raw: String): MacroEstimate? {
        val open = raw.indexOf('{')
        val close = raw.lastIndexOf('}')
        if (open < 0 || close <= open) return null
        val body = raw.substring(open + 1, close)

        val name = extractString(body, "name") ?: "Meal"
        val calories = extractInt(body, "calories") ?: return null
        val protein = extractInt(body, "proteinG") ?: 0
        val carbs = extractInt(body, "carbsG") ?: 0
        val fats = extractInt(body, "fatsG") ?: 0

        return MacroEstimate(
            name = name.ifBlank { "Meal" },
            calories = calories.coerceAtLeast(0),
            proteinG = protein.coerceAtLeast(0),
            carbsG = carbs.coerceAtLeast(0),
            fatsG = fats.coerceAtLeast(0),
            source = MacroSource.Llm,
        )
    }

    private fun extractString(body: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(body)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun extractInt(body: String, key: String): Int? {
        val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
        return pattern.find(body)?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        private const val TAG = "AssistantEngine"
    }
}

enum class MacroSource { Llm, Heuristic }

data class MacroEstimate(
    val name: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int,
    val source: MacroSource,
)
