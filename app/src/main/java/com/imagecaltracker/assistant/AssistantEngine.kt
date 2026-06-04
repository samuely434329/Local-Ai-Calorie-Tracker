package com.imagecaltracker.assistant

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.type.content
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
 * On-device LLM wrapper for the in-app assistant using modern LiteRT-LM.
 */
class AssistantEngine(private val appContext: Context) {

    private var engine: Engine? = null
    private var loadAttempted: Boolean = false
    private var lastError: String? = null
    private val initLock = Mutex()

    val usingFallback: Boolean
        get() = loadAttempted && engine == null

    val statusMessage: String
        get() = when {
            engine != null -> "Local Model Active"
            lastError != null -> lastError!!
            !loadAttempted -> "Checking for model..."
            else -> "Heuristic Mode (No model found)"
        }

    /** Pre-initialize the engine so it's ready before the user types. */
    suspend fun initialize() {
        ensureLoaded()
    }

    private suspend fun ensureLoaded(): Engine? = initLock.withLock {
        if (engine != null) return engine

        val dir = File(appContext.getExternalFilesDir(null), "llm").apply { mkdirs() }
        
        var file = File(dir, "model.litertlm")
        if (!file.exists() || file.length() == 0L) {
            val autoFile = dir.listFiles()?.find { 
                it.name.endsWith(".litertlm", ignoreCase = true) || 
                it.name.endsWith(".task", ignoreCase = true) 
            }
            if (autoFile != null) {
                file = autoFile
            }
        }

        if (!file.exists() || file.length() == 0L) {
            loadAttempted = true
            lastError = "Model not found in ${dir.absolutePath}"
            Log.w(TAG, lastError!!)
            return null
        }

        Log.d(TAG, "Initializing LiteRT-LM with: ${file.name} (${file.length() / 1024 / 1024} MB)...")
        return try {
            val config = EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU()
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            loadAttempted = true
            newEngine
        } catch (t: Throwable) {
            loadAttempted = true
            lastError = "Init failed: ${t.message}"
            Log.e(TAG, lastError!!, t)
            null
        }
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {}
        engine = null
    }

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.Default) {
        val prompt = buildString {
            append("<|im_start|>system\n")
            append("You are a diet assistant. Keep replies short.<|im_end|>\n")
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
            append("Estimate macronutrients. Reply ONE line JSON: ")
            append("{\"name\":string,\"calories\":int,\"proteinG\":int,\"carbsG\":int,\"fatsG\":int}<|im_end|>\n")
            append("<|im_start|>user\n")
            append(description.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
        val raw = runLlm(prompt)
        raw?.let { parseMacroJson(it) } ?: error("Failed to generate MacroEstimate")
    }

    private suspend fun runLlm(prompt: String): String? {
        val currentEngine = ensureLoaded() ?: return null
        return try {
            currentEngine.createConversation().use { conversation ->
                val response = conversation.sendMessage(prompt)
                // June 4th edited
                val text = response.content
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
        return "I'm in heuristic mode. Add 'model.litertlm' to your 'llm' folder to enable AI."
    }

    private fun parseMacroJson(raw: String): MacroEstimate? {
        val open = raw.indexOf('{')
        val close = raw.lastIndexOf('}')
        if (open < 0 || close <= open) return null
        val body = raw.substring(open + 1, close)
        val name = extractString(body, "name") ?: "Meal"
        val calories = extractInt(body, "calories") ?: return null
        return MacroEstimate(
            name = name.ifBlank { "Meal" },
            calories = calories,
            proteinG = extractInt(body, "proteinG") ?: 0,
            carbsG = extractInt(body, "carbsG") ?: 0,
            fatsG = extractInt(body, "fatsG") ?: 0,
            source = MacroSource.Llm,
        )
    }

    private fun extractString(body: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(body)?.groupValues?.get(1)?.replace("\\\"", "\"")
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
