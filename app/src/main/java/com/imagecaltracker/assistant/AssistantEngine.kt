package com.imagecaltracker.assistant

import android.content.Context
import android.os.Build
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
 * Assistant backend router. Owns either a LiteRT-LM [Engine] for on-device
 * models (Qwen / Gemma) or a [GeminiBackend] for the remote Gemini API. The
 * active [ModelSpec] and API key are pushed in via [setModel]; when the spec
 * changes we tear down the previous backend and lazy-load the new one.
 */
class AssistantEngine(private val appContext: Context) {

    @Volatile
    private var currentSpec: ModelSpec = ModelCatalog.default

    // Local (LiteRT) state
    private var engine: Engine? = null
    private var loadAttempted: Boolean = false
    private var lastError: String? = null
    private val initLock = Mutex()

    // Remote state
    private var gemini: GeminiBackend? = null

    val usingFallback: Boolean
        get() = when (currentSpec.family) {
            ModelFamily.Gemini -> gemini?.hasKey != true
            else -> loadAttempted && engine == null
        }

    val statusMessage: String
        get() = when (currentSpec.family) {
            ModelFamily.Gemini -> when {
                gemini?.hasKey == true -> "${currentSpec.displayName} · Ready"
                else -> "${currentSpec.displayName} · Enter API key"
            }
            else -> when {
                engine != null -> "${currentSpec.displayName} · Ready"
                lastError != null -> "${currentSpec.displayName} · ${lastError!!}"
                !loadAttempted -> "${currentSpec.displayName} · Checking..."
                else -> "${currentSpec.displayName} · Not downloaded"
            }
        }

    val activeSpec: ModelSpec get() = currentSpec

    /**
     * Swap the active backend. Safe to call at any time; if the same spec is
     * already active and only the API key changed we just refresh the Gemini
     * client.
     */
    suspend fun setModel(spec: ModelSpec, geminiApiKey: String?) = withContext(Dispatchers.IO) {
        initLock.withLock {
            val familyChanged = currentSpec.family != spec.family || currentSpec.id != spec.id
            if (familyChanged) {
                try { engine?.close() } catch (_: Throwable) {}
                engine = null
                loadAttempted = false
                lastError = null
                gemini = null
            }
            currentSpec = spec
            if (spec.family == ModelFamily.Gemini) {
                gemini = GeminiBackend(geminiApiKey.orEmpty())
            }
        }
    }

    /** Pre-initialize the engine so it's ready before the user types. */
    suspend fun initialize() {
        if (currentSpec.family == ModelFamily.Gemini) return
        ensureLoaded()
    }

    private suspend fun ensureLoaded(): Engine? = withContext(Dispatchers.IO) {
        initLock.withLock {
            if (currentSpec.family == ModelFamily.Gemini) return@withLock null
            if (engine != null) return@withLock engine

            val spec = currentSpec
            val filename = spec.filename ?: return@withLock null
            val dir = File(appContext.getExternalFilesDir(null), "llm").apply { mkdirs() }
            val file = File(dir, filename)

            if (!file.exists() || file.length() == 0L) {
                loadAttempted = true
                lastError = "Not downloaded"
                Log.w(TAG, "Model file missing: ${file.absolutePath}")
                return@withLock null
            }

            Log.d(TAG, "Initializing LiteRT-LM with: ${file.name} (${file.length() / 1024 / 1024} MB)...")

            if (isEmulator() && Build.SUPPORTED_ABIS.firstOrNull()?.contains("x86") == true) {
                loadAttempted = true
                lastError = "AI not supported on x86 emulators"
                Log.w(TAG, lastError!!)
                return@withLock null
            }

            try {
                val config = EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                loadAttempted = true
                lastError = null
                newEngine
            } catch (t: Throwable) {
                loadAttempted = true
                lastError = "Init failed: ${t.message}"
                Log.e(TAG, lastError!!, t)
                null
            }
        }
    }

    fun close() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
        gemini = null
    }

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.Default) {
        val spec = currentSpec
        val trimmed = userMessage.trim()
        when (spec.family) {
            ModelFamily.Gemini -> {
                gemini?.chat(trimmed) ?: fallbackChat(spec)
            }
            else -> {
                val prompt = buildChatPrompt(spec.template, trimmed)
                runLlm(prompt) ?: fallbackChat(spec)
            }
        }
    }

    suspend fun estimateMacros(description: String): MacroEstimate = withContext(Dispatchers.Default) {
        val spec = currentSpec
        val trimmed = description.trim()
        when (spec.family) {
            ModelFamily.Gemini -> {
                gemini?.estimateMacros(trimmed) ?: error("Gemini call failed (missing key or network)")
            }
            else -> {
                val prompt = buildMacroPrompt(spec.template, trimmed)
                val raw = runLlm(prompt)
                raw?.let { parseMacroJson(it) } ?: error("Failed to generate MacroEstimate")
            }
        }
    }

    // ---- Prompt templates ---------------------------------------------------

    private fun buildChatPrompt(template: PromptTemplate, userText: String): String = when (template) {
        PromptTemplate.QwenChatMl -> buildString {
            append("<|im_start|>system\n")
            append("You are a diet assistant. Keep replies short.<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userText)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
        PromptTemplate.GemmaTurn -> buildString {
            // Gemma has no dedicated system role — prepend the instruction to the user turn.
            append("<start_of_turn>user\n")
            append("You are a diet assistant. Keep replies short.\n\n")
            append(userText)
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        PromptTemplate.Gemini -> userText // never used for local
    }

    private fun buildMacroPrompt(template: PromptTemplate, description: String): String = when (template) {
        PromptTemplate.QwenChatMl -> buildString {
            append("<|im_start|>system\n")
            append("Estimate macronutrients. Reply ONE line JSON: ")
            append("{\"name\":string,\"calories\":int,\"proteinG\":int,\"carbsG\":int,\"fatsG\":int}<|im_end|>\n")
            append("<|im_start|>user\n")
            append(description)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
        PromptTemplate.GemmaTurn -> buildString {
            append("<start_of_turn>user\n")
            append("Estimate macronutrients. Reply ONE line JSON: ")
            append("{\"name\":string,\"calories\":int,\"proteinG\":int,\"carbsG\":int,\"fatsG\":int}\n\n")
            append(description)
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
        PromptTemplate.Gemini -> description
    }

    // ---- Local runtime ------------------------------------------------------

    private suspend fun runLlm(prompt: String): String? {
        val currentEngine = ensureLoaded() ?: return null
        return try {
            currentEngine.createConversation().use { conversation ->
                val response = conversation.sendMessage(prompt)
                val text = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                stripThinking(text).trim().takeIf { it.isNotEmpty() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "sendMessage failed: ${t.message}", t)
            null
        }
    }

    /**
     * Qwen3 emits an internal reasoning trace inside <think>...</think> tags
     * before the user-visible answer. Gemma models may or may not — the
     * regex is a no-op if the tags aren't present, so it's safe to run for
     * both families.
     */
    private fun stripThinking(raw: String): String {
        if (!raw.contains("<think", ignoreCase = true)) return raw
        val closed = Regex("(?is)<think>.*?</think>").replace(raw, "")
        val openIdx = closed.indexOf("<think", ignoreCase = true)
        return if (openIdx >= 0) closed.substring(0, openIdx) else closed
    }

    private fun fallbackChat(spec: ModelSpec): String = when (spec.family) {
        ModelFamily.Gemini -> "Add your Gemini API key in Model Settings to enable the cloud assistant."
        else -> "I'm in heuristic mode. Download ${spec.displayName} in Model Settings to enable AI."
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

    private fun isEmulator(): Boolean {
        return Build.DEVICE.contains("generic") ||
            Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk_gphone")
    }

    companion object {
        private const val TAG = "AssistantEngine"
        init {
            try {
                System.loadLibrary("litertlm_jni")
            } catch (t: Throwable) {
                Log.w("AssistantEngine", "Native library 'litertlm_jni' not pre-loaded: ${t.message}")
            }
        }
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
