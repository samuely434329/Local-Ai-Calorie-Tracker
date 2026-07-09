package com.imagecaltracker.assistant

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imagecaltracker.data.ModelSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Who authored a chat message. */
enum class ChatRole { User, Assistant }

/** Single chat message bubble in the assistant's chat tab. */
data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
)

/** Runtime state for one model in the catalog. */
data class ModelStatus(
    val spec: ModelSpec,
    val downloaded: Boolean,
    val downloading: Boolean,
    val progress: Float,
)

/**
 * Combined state for the assistant dialog + model settings sheet. Drives both
 * the chat tab and the scanner tab — the dialog itself decides which slice to
 * render.
 */
data class AssistantUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            id = 0L,
            role = ChatRole.Assistant,
            text = "Hi! Ask me about diet, macros, or meal ideas. " +
                "Or switch to the SCAN tab to estimate calories from a photo.",
        )
    ),
    val sending: Boolean = false,
    val photoUri: Uri? = null,
    val description: String = "",
    val estimating: Boolean = false,
    val estimate: MacroEstimate? = null,
    val usingFallback: Boolean = false,
    val statusMessage: String = "Checking...",
    val selectedModel: ModelSpec = ModelCatalog.default,
    val models: List<ModelStatus> = ModelCatalog.all.map {
        ModelStatus(it, downloaded = false, downloading = false, progress = 0f)
    },
    val geminiApiKey: String = "",
) {
    /** Convenience: is any local model download currently in progress. */
    val anyDownloading: Boolean get() = models.any { it.downloading }
}

/**
 * ViewModel for the in-app assistant dialog + model settings. Owns one
 * [AssistantEngine] for the lifetime of the VM and tracks all model / chat
 * / scanner state as a single [AssistantUiState].
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AssistantEngine(application.applicationContext)
    private val settings = ModelSettingsRepository(application.applicationContext)

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            geminiApiKey = settings.getGeminiApiKey().orEmpty(),
            models = computeModelStatuses(),
        )
        // Observe the selected model id and swap the backend when it changes.
        viewModelScope.launch {
            settings.selectedModelIdFlow.collect { id ->
                val spec = ModelCatalog.byId(id)
                try {
                    engine.setModel(spec, settings.getGeminiApiKey())
                    if (spec.family != ModelFamily.Gemini) engine.initialize()
                } catch (_: Exception) {
                    // Engine already logs internally.
                }
                _state.value = _state.value.copy(
                    selectedModel = spec,
                    usingFallback = engine.usingFallback,
                    statusMessage = engine.statusMessage,
                    models = computeModelStatuses(_state.value.models),
                )
            }
        }
    }

    private var nextMessageId: Long = 1L

    fun newPhotoUri(): Uri {
        val ctx = getApplication<Application>().applicationContext
        val dir = File(ctx.getExternalFilesDir(null), "assistant_photos").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "scan_$stamp.jpg")
        if (!file.exists()) file.createNewFile()
        return FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file,
        )
    }

    // -----------------------------------------------------------------------
    // Model selection & settings
    // -----------------------------------------------------------------------

    fun setModel(id: String) {
        viewModelScope.launch { settings.setSelectedModelId(id) }
    }

    fun setGeminiApiKey(key: String) {
        settings.setGeminiApiKey(key)
        _state.value = _state.value.copy(geminiApiKey = key)
        // If Gemini is currently active, refresh the backend so the new key takes effect.
        if (_state.value.selectedModel.family == ModelFamily.Gemini) {
            viewModelScope.launch {
                engine.setModel(_state.value.selectedModel, key)
                _state.value = _state.value.copy(
                    usingFallback = engine.usingFallback,
                    statusMessage = engine.statusMessage,
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Chat tab
    // -----------------------------------------------------------------------

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.sending) return

        val userMsg = ChatMessage(nextMessageId++, ChatRole.User, trimmed)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            sending = true,
        )

        viewModelScope.launch {
            val reply = try {
                engine.chat(trimmed)
            } catch (t: Throwable) {
                "Sorry, I hit an error generating a reply: ${t.message ?: "unknown"}."
            }
            val assistantMsg = ChatMessage(nextMessageId++, ChatRole.Assistant, reply)
            _state.value = _state.value.copy(
                messages = _state.value.messages + assistantMsg,
                sending = false,
                usingFallback = engine.usingFallback,
                statusMessage = engine.statusMessage,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Scanner tab
    // -----------------------------------------------------------------------

    fun onPhotoCaptured(uri: Uri) {
        _state.value = _state.value.copy(
            photoUri = uri,
            estimate = null,
        )
    }

    fun setDescription(text: String) {
        _state.value = _state.value.copy(description = text)
    }

    fun resetScan() {
        _state.value = _state.value.copy(
            photoUri = null,
            description = "",
            estimate = null,
        )
    }

    fun estimateMacros() {
        val desc = _state.value.description.trim()
        if (desc.isEmpty() || _state.value.photoUri == null || _state.value.estimating) return

        _state.value = _state.value.copy(estimating = true, estimate = null)
        viewModelScope.launch {
            val result = try {
                engine.estimateMacros(desc)
            } catch (t: Throwable) {
                MacroEstimate(
                    name = desc.take(40),
                    calories = 0,
                    proteinG = 0,
                    carbsG = 0,
                    fatsG = 0,
                    source = MacroSource.Heuristic,
                )
            }
            _state.value = _state.value.copy(
                estimating = false,
                estimate = result,
                usingFallback = engine.usingFallback,
                statusMessage = engine.statusMessage,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Downloads
    // -----------------------------------------------------------------------

    fun downloadModel(id: String? = null) {
        val spec = id?.let { ModelCatalog.byId(it) } ?: _state.value.selectedModel
        if (spec.family == ModelFamily.Gemini || spec.downloadUrl == null || spec.filename == null) return
        val existing = _state.value.models.firstOrNull { it.spec.id == spec.id }
        if (existing?.downloading == true) return

        _state.value = _state.value.copy(
            models = _state.value.models.map {
                if (it.spec.id == spec.id) it.copy(downloading = true, progress = 0f) else it
            },
            statusMessage = if (spec.id == _state.value.selectedModel.id) {
                "${spec.displayName} · Downloading 0%"
            } else _state.value.statusMessage,
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { performDownload(spec) }

            when (result) {
                is DownloadResult.Success -> {
                    if (spec.id == _state.value.selectedModel.id) {
                        engine.initialize()
                    }
                    _state.value = _state.value.copy(
                        models = _state.value.models.map {
                            if (it.spec.id == spec.id) {
                                it.copy(downloading = false, progress = 1f, downloaded = true)
                            } else it
                        },
                        usingFallback = engine.usingFallback,
                        statusMessage = engine.statusMessage,
                    )
                }
                is DownloadResult.Failure -> {
                    val msg = "Download failed: ${result.reason}"
                    Log.e("AssistantVM", msg)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                    }
                    _state.value = _state.value.copy(
                        models = _state.value.models.map {
                            if (it.spec.id == spec.id) it.copy(downloading = false, progress = 0f) else it
                        },
                        statusMessage = if (spec.id == _state.value.selectedModel.id) msg
                        else _state.value.statusMessage,
                    )
                }
            }
        }
    }

    private sealed class DownloadResult {
        object Success : DownloadResult()
        data class Failure(val reason: String) : DownloadResult()
    }

    private fun performDownload(spec: ModelSpec): DownloadResult {
        val url = spec.downloadUrl ?: return DownloadResult.Failure("No URL for ${spec.displayName}")
        val filename = spec.filename ?: return DownloadResult.Failure("No filename for ${spec.displayName}")
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "llm").apply { mkdirs() }
        val targetFile = File(dir, filename)
        val tempFile = File(dir, "$filename.part")

        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ImageCalTracker/1.0")
            }

            val code = connection.responseCode
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                return DownloadResult.Failure("HTTP $code ${connection.responseMessage ?: ""}".trim())
            }

            val totalSize = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    var lastReportedPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        totalRead += n
                        if (totalSize > 0) {
                            val pct = ((totalRead * 100) / totalSize).toInt()
                            if (pct != lastReportedPercent) {
                                lastReportedPercent = pct
                                val progress = pct / 100f
                                _state.value = _state.value.copy(
                                    models = _state.value.models.map {
                                        if (it.spec.id == spec.id) it.copy(progress = progress) else it
                                    },
                                    statusMessage = if (spec.id == _state.value.selectedModel.id) {
                                        "${spec.displayName} · Downloading $pct%"
                                    } else _state.value.statusMessage,
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                return DownloadResult.Failure("Could not move downloaded file into place")
            }
            DownloadResult.Success
        } catch (t: Throwable) {
            tempFile.delete()
            DownloadResult.Failure("${t.javaClass.simpleName}: ${t.message ?: "unknown error"}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Rebuild the model status list from disk. Preserves any `downloading` /
     * `progress` state from the previous snapshot so an in-flight download
     * isn't lost when we refresh.
     */
    private fun computeModelStatuses(
        previous: List<ModelStatus> = emptyList(),
    ): List<ModelStatus> {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "llm")
        return ModelCatalog.all.map { spec ->
            val prev = previous.firstOrNull { it.spec.id == spec.id }
            val downloaded = spec.filename?.let { name ->
                val f = File(dir, name)
                f.exists() && f.length() > 0
            } ?: false
            ModelStatus(
                spec = spec,
                downloaded = downloaded,
                downloading = prev?.downloading ?: false,
                progress = prev?.progress ?: 0f,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
    }
}
