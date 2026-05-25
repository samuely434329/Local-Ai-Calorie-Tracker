package com.imagecaltracker.assistant

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * On-device LLM wrapper for the in-app assistant.
 *
 * The model file is NOT bundled in the APK because Gemma .task files are
 * hundreds of MB. Instead, we look for the file on disk at runtime in the
 * app's external-files dir at:
 *
 *     /Android/data/com.imagecaltracker/files/llm/gemma.task
 *
 * If the file is present, we initialise MediaPipe LlmInference. Otherwise
 * we fall back to a deterministic heuristic responder so the assistant UI
 * still works in development and on devices without a model.
 *
 * The class is safe to construct without the model present — initialisation
 * happens lazily on first call and any failure falls back gracefully.
 */
class AssistantEngine(private val appContext: Context) {

    /** Cached MediaPipe inference handle. Null until first successful load. */
    private var llm: LlmInference? = null

    /** True once we've attempted to load the model — used so we don't retry forever. */
    private var loadAttempted: Boolean = false

    /** True if the heuristic fallback is in use (model missing or failed to load). */
    val usingFallback: Boolean
        get() = loadAttempted && llm == null

    /** Path the user is expected to drop the .task file at. Surfaced in the UI. */
    val expectedModelPath: String
        get() = modelFile().absolutePath

    private fun modelFile(): File {
        val dir = File(appContext.getExternalFilesDir(null), "llm").apply { mkdirs() }
        return File(dir, "gemma.task")
    }

    /**
     * Lazily load the model. Returns the LlmInference instance, or null if
     * the model is missing or load failed (in which case we use the fallback).
     */
    @Synchronized
    private fun ensureLoaded(): LlmInference? {
        if (llm != null) return llm
        if (loadAttempted) return null
        loadAttempted = true

        val file = modelFile()
        if (!file.exists() || file.length() == 0L) {
            Log.i(TAG, "No LLM model at ${file.absolutePath}; using fallback responder.")
            return null
        }

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setTopK(64)
                .build()
            LlmInference.createFromOptions(appContext, options).also { llm = it }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialise LlmInference, using fallback: ${t.message}", t)
            null
        }
    }

    /**
     * Free the underlying native handle. Call from ViewModel.onCleared().
     */
    fun close() {
        try {
            llm?.close()
        } catch (_: Throwable) {
            // best-effort
        }
        llm = null
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Free-form chat reply. The system prompt nudges the model toward concise
     * diet-related answers so the assistant stays focused.
     */
    suspend fun chat(userMessage: String): String = withContext(Dispatchers.Default) {
        val prompt = buildString {
            append("You are a friendly diet and nutrition assistant inside a calorie ")
            append("tracking app. Keep replies short (2-4 sentences), practical, and ")
            append("non-judgemental. If asked something outside diet/nutrition/fitness, ")
            append("politely steer back. Do not give medical advice.\n\n")
            append("User: ")
            append(userMessage.trim())
            append("\nAssistant:")
        }
        runLlm(prompt) ?: fallbackChat(userMessage)
    }

    /**
     * Estimate macros for a meal described in [description]. The image itself
     * is not analysed (we use a text-only model); the description is the source
     * of truth, which matches the UX where the user MUST describe the photo.
     *
     * Returns a [MacroEstimate] — never null. On model failure we fall back to
     * a heuristic so the user always sees a result they can edit before saving.
     */
    suspend fun estimateMacros(description: String): MacroEstimate = withContext(Dispatchers.Default) {
        val prompt = buildString {
            append("You estimate calories and macronutrients from a meal description.\n")
            append("Reply with ONE line of strict JSON, no prose, no code fences. Schema:\n")
            append("{\"name\":string,\"calories\":int,\"proteinG\":int,\"carbsG\":int,\"fatsG\":int}\n")
            append("Be realistic for a typical single serving. If the description is vague, ")
            append("guess sensibly. All numeric fields are non-negative integers.\n\n")
            append("Description: ")
            append(description.trim())
            append("\nJSON:")
        }
        val raw = runLlm(prompt)
        raw?.let { parseMacroJson(it) } ?: fallbackMacros(description)
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /** Run the loaded LLM if available, returning null on any failure. */
    private fun runLlm(prompt: String): String? {
        val engine = ensureLoaded() ?: return null
        return try {
            engine.generateResponse(prompt)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "generateResponse failed: ${t.message}", t)
            null
        }
    }

    // ----- Heuristic fallback for chat -----

    private fun fallbackChat(userMessage: String): String {
        val q = userMessage.lowercase().trim()
        return when {
            q.contains("protein") -> "For most active adults, ~0.8–1.0 g of protein per pound of bodyweight is a reasonable target. Spread it across 3–4 meals — eggs, chicken, Greek yogurt, tofu, and beans are all efficient sources."
            q.contains("carb") -> "Carbs aren't the enemy — they fuel workouts and recovery. Lean on whole grains, fruit, beans and starchy veg, and keep added sugar low. Match intake to your activity level."
            q.contains("fat") -> "Healthy fats from olive oil, avocado, nuts and fatty fish support hormones and satiety. Aim for ~20–35% of your daily calories from fat, mostly unsaturated."
            q.contains("breakfast") -> "A balanced breakfast usually pairs a protein (eggs, yogurt, cottage cheese) with a slow carb (oats, whole-grain toast) and a piece of fruit. ~400–500 kcal works for most."
            q.contains("snack") -> "Smart snacks combine protein + fibre to keep you full: apple + peanut butter, Greek yogurt + berries, hummus + carrots, or a handful of mixed nuts."
            q.contains("water") || q.contains("hydration") -> "A common rule of thumb is ~30–35 ml of water per kg of bodyweight per day, more if you're sweating. Pale yellow urine is a decent visual check."
            q.contains("lose") || q.contains("weight loss") || q.contains("cut") -> "A modest 300–500 kcal/day deficit usually drives sustainable loss without wrecking energy. Keep protein high to preserve muscle and add some resistance training."
            q.contains("gain") || q.contains("bulk") || q.contains("muscle") -> "To build muscle, aim for a 200–400 kcal/day surplus, ~1.6–2.2 g/kg protein, and progressive resistance training. Sleep is half the equation."
            q.contains("sugar") -> "Most guidelines suggest keeping added sugar under ~25 g/day. Whole-fruit sugar is a different story — fibre and water blunt the spike."
            q.contains("hello") || q.contains("hi ") || q == "hi" || q == "hey" -> "Hey! I can help with quick diet questions or estimate macros from a meal photo + description. What's up?"
            else -> "I'm a small offline diet assistant — I can suggest meal ideas, talk macros, or estimate calories. (Drop a Gemma .task model in the app's files dir for smarter answers.)"
        }
    }

    // ----- Heuristic fallback for macro estimation -----

    private fun fallbackMacros(description: String): MacroEstimate {
        // Very rough keyword-driven baseline — gives the user something plausible
        // to edit rather than zeros. Numbers chosen for a typical single serving.
        val q = description.lowercase()
        var calories = 350
        var protein = 18
        var carbs = 40
        var fats = 12

        if (matches(q, "salad", "greens", "veg")) {
            calories = 220; protein = 8; carbs = 18; fats = 12
        }
        if (matches(q, "burger")) {
            calories = 650; protein = 35; carbs = 45; fats = 35
        }
        if (matches(q, "pizza")) {
            calories = 550; protein = 22; carbs = 60; fats = 22
        }
        if (matches(q, "rice", "fried rice", "biryani")) {
            calories = 480; protein = 14; carbs = 75; fats = 12
        }
        if (matches(q, "pasta", "spaghetti", "noodle")) {
            calories = 520; protein = 18; carbs = 78; fats = 12
        }
        if (matches(q, "chicken", "grilled chicken")) {
            calories = 420; protein = 40; carbs = 12; fats = 18
        }
        if (matches(q, "fish", "salmon", "tuna")) {
            calories = 380; protein = 38; carbs = 6; fats = 18
        }
        if (matches(q, "egg", "omelette", "omelet")) {
            calories = 320; protein = 22; carbs = 6; fats = 22
        }
        if (matches(q, "smoothie", "shake")) {
            calories = 280; protein = 18; carbs = 38; fats = 6
        }
        if (matches(q, "oatmeal", "oats", "porridge")) {
            calories = 320; protein = 12; carbs = 52; fats = 8
        }
        if (matches(q, "sandwich", "wrap", "burrito")) {
            calories = 480; protein = 22; carbs = 50; fats = 18
        }
        if (matches(q, "soup", "broth")) {
            calories = 220; protein = 12; carbs = 24; fats = 8
        }
        if (matches(q, "yogurt", "yoghurt")) {
            calories = 180; protein = 14; carbs = 22; fats = 4
        }
        if (matches(q, "fruit", "apple", "banana", "berries")) {
            calories = 110; protein = 1; carbs = 28; fats = 0
        }

        // Crude portion modifiers.
        if (matches(q, "large", "big", "double")) {
            calories = (calories * 1.4).roundToInt()
            protein = (protein * 1.4).roundToInt()
            carbs = (carbs * 1.4).roundToInt()
            fats = (fats * 1.4).roundToInt()
        }
        if (matches(q, "small", "mini", "half")) {
            calories = (calories * 0.7).roundToInt()
            protein = (protein * 0.7).roundToInt()
            carbs = (carbs * 0.7).roundToInt()
            fats = (fats * 0.7).roundToInt()
        }

        return MacroEstimate(
            name = guessName(description),
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatsG = fats,
            source = MacroSource.Heuristic,
        )
    }

    private fun matches(q: String, vararg keywords: String): Boolean =
        keywords.any { q.contains(it) }

    private fun guessName(description: String): String {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) return "Meal"
        // First sentence / first comma chunk, capped length, title-case-ish.
        val firstChunk = trimmed.split('.', ',', '\n').first().trim()
        return firstChunk.take(40).replaceFirstChar { it.uppercaseChar() }
    }

    // ----- JSON parser (manual, no Gson dep) -----

    /**
     * Pulls the first {...} block out of [raw] and parses the five fields we
     * care about. Robust to surrounding prose / code fences. Returns null on
     * obviously broken output so the caller can fall back to the heuristic.
     */
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

/** Where a [MacroEstimate] came from — used by the UI for honest labelling. */
enum class MacroSource { Llm, Heuristic }

/**
 * Result of estimating macros for one meal. Numeric fields are non-negative
 * integers in grams (or kcal for [calories]).
 */
data class MacroEstimate(
    val name: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int,
    val source: MacroSource,
)
