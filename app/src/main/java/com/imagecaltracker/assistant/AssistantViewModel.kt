package com.imagecaltracker.assistant

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Combined state for the assistant dialog. Drives both the chat tab and the
 * scanner tab — the dialog itself decides which slice to render.
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
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val statusMessage: String = "Checking...",
)

/**
 * ViewModel for the in-app assistant dialog. Owns one [AssistantEngine] for
 * the lifetime of the dialog, and tracks chat + scanner state as a single
 * [AssistantUiState].
 *
 * The view model only manages state and engine calls; launching the camera
 * and consuming the captured photo are done by the composable via
 * ActivityResult APIs (which need an Activity, not an Application).
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AssistantEngine(application.applicationContext)

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    init {
        // Start engine initialization immediately when the dialog opens.
        viewModelScope.launch {
            try {
                engine.initialize()
            } catch (e: Exception) {
                // AssistantEngine.initialize should catch internally, but we wrap here too
                // to prevent any bubbling exceptions from crashing the VM scope.
            }
            _state.value = _state.value.copy(
                usingFallback = engine.usingFallback,
                statusMessage = engine.statusMessage
            )
        }
    }

    /** Monotonic id generator for chat messages — purely for LazyColumn keys. */
    private var nextMessageId: Long = 1L

    /**
     * Build a content Uri for a NEW capture and update state with it. The
     * returned Uri is what the camera intent should write into.
     *
     * The file lives under <external-files>/assistant_photos/, which is
     * mapped by res/xml/file_paths.xml.
     */
    fun newPhotoUri(): Uri {
        val ctx = getApplication<Application>().applicationContext
        val dir = File(ctx.getExternalFilesDir(null), "assistant_photos").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "scan_$stamp.jpg")
        // Touch the file so the FileProvider Uri resolves for the camera.
        if (!file.exists()) file.createNewFile()
        return FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file,
        )
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

    /** Called by the dialog after the camera intent succeeds. */
    fun onPhotoCaptured(uri: Uri) {
        _state.value = _state.value.copy(
            photoUri = uri,
            // Keep any existing description — common to retake the photo.
            estimate = null,
        )
    }

    fun setDescription(text: String) {
        _state.value = _state.value.copy(description = text)
    }

    /** Discard the current photo + description + estimate. */
    fun resetScan() {
        _state.value = _state.value.copy(
            photoUri = null,
            description = "",
            estimate = null,
        )
    }

    /**
     * Run the engine to estimate macros from the current description. Photo
     * is required by the UX rule, but the description is what actually drives
     * the estimate (text-only model).
     */
    fun estimateMacros() {
        val desc = _state.value.description.trim()
        if (desc.isEmpty() || _state.value.photoUri == null || _state.value.estimating) return

        _state.value = _state.value.copy(estimating = true, estimate = null)
        viewModelScope.launch {
            val result = try {
                engine.estimateMacros(desc)
            } catch (t: Throwable) {
                // Build a zeroed placeholder so the user can still edit & save.
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

    fun downloadModel() {
        if (_state.value.downloading) return

        _state.value = _state.value.copy(
            downloading = true,
            downloadProgress = 0f,
            statusMessage = "Downloading model...",
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { performDownload() }

            when (result) {
                is DownloadResult.Success -> {
                    engine.initialize()
                    _state.value = _state.value.copy(
                        downloading = false,
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
                        downloading = false,
                        downloadProgress = 0f,
                        statusMessage = msg,
                    )
                }
            }
        }
    }

    private sealed class DownloadResult {
        object Success : DownloadResult()
        data class Failure(val reason: String) : DownloadResult()
    }

    /**
     * Download the LiteRT-LM Qwen model from Hugging Face. Saves to
     * <external-files>/llm/Qwen3-0.6B.litertlm so the engine's auto-discovery
     * picks it up.
     *
     * The HF resolve endpoint returns 302 to a CloudFront URL.
     * HttpURLConnection follows that automatically when
     * instanceFollowRedirects is true AND the redirect stays within the
     * same protocol (https → https here, so it works).
     */
    private fun performDownload(): DownloadResult {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), "llm").apply { mkdirs() }
        val targetFile = File(dir, MODEL_FILENAME)
        val tempFile = File(dir, "$MODEL_FILENAME.part")

        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = (URL(MODEL_URL).openConnection() as java.net.HttpURLConnection).apply {
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
                                _state.value = _state.value.copy(
                                    downloadProgress = pct / 100f,
                                    statusMessage = "Downloading... $pct%",
                                )
                            }
                        } else {
                            // Unknown size — report MB every ~4 MB.
                            val mb = (totalRead / 1024 / 1024).toInt()
                            if (mb % 4 == 0) {
                                _state.value = _state.value.copy(
                                    statusMessage = "Downloading... ${mb} MB",
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }

            // Atomic-ish swap so a half-written file never poses as a real model.
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

    override fun onCleared() {
        super.onCleared()
        engine.close()
    }

    companion object {
        /**
         * Default LiteRT-LM Qwen3 0.6B model. The Hugging Face repo also offers
         * a smaller mixed-int4 variant (qwen3_0_6b_mixed_int4.litertlm) — swap
         * the URL if you want a smaller / faster model on lower-end phones.
         */
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
        private const val MODEL_FILENAME = "Qwen3-0.6B.litertlm"
    }
}
