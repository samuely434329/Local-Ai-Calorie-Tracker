package com.imagecaltracker.assistant

/** Which model family we're talking to — drives prompt formatting + feature gating. */
enum class ModelFamily { Qwen, Gemma, Gemini }

/** Prompt formatting scheme. Kept separate from family so we can vary independently. */
enum class PromptTemplate { QwenChatMl, GemmaTurn, Gemini }

/**
 * Static description of a supported model. All fields are immutable metadata;
 * runtime state (downloaded / downloading / selected) lives in the view model.
 *
 * `downloadUrl` and `filename` are null for remote models (Gemini).
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val family: ModelFamily,
    val template: PromptTemplate,
    val downloadUrl: String?,
    val filename: String?,
    val approxSizeMb: Int,
    val supportsQuickScan: Boolean,
) {
    val isRemote: Boolean get() = downloadUrl == null
}

object ModelCatalog {
    val Qwen3_0_6B = ModelSpec(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        shortLabel = "Q3 0.6B",
        family = ModelFamily.Qwen,
        template = PromptTemplate.QwenChatMl,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        filename = "Qwen3-0.6B.litertlm",
        approxSizeMb = 614,
        supportsQuickScan = false,
    )

    val Gemma4_E2B = ModelSpec(
        id = "gemma4-e2b",
        displayName = "Gemma 4 E2B",
        shortLabel = "G4 E2B",
        family = ModelFamily.Gemma,
        template = PromptTemplate.GemmaTurn,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        filename = "gemma-4-E2B-it.litertlm",
        approxSizeMb = 2590,
        supportsQuickScan = true,
    )

    val Gemma4_E4B = ModelSpec(
        id = "gemma4-e4b",
        displayName = "Gemma 4 E4B",
        shortLabel = "G4 E4B",
        family = ModelFamily.Gemma,
        template = PromptTemplate.GemmaTurn,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        filename = "gemma-4-E4B-it.litertlm",
        approxSizeMb = 3660,
        supportsQuickScan = true,
    )

    val Gemini = ModelSpec(
        id = "gemini",
        displayName = "Gemini (cloud)",
        shortLabel = "Gemini",
        family = ModelFamily.Gemini,
        template = PromptTemplate.Gemini,
        downloadUrl = null,
        filename = null,
        approxSizeMb = 0,
        supportsQuickScan = false,
    )

    val all: List<ModelSpec> = listOf(Qwen3_0_6B, Gemma4_E2B, Gemma4_E4B, Gemini)
    val default: ModelSpec = Qwen3_0_6B

    fun byId(id: String): ModelSpec = all.firstOrNull { it.id == id } ?: default
}
