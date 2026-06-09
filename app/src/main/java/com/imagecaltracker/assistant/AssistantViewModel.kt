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
        
        _state.value = _state.value.copy(downloading = true, statusMessage = "Downloading model...")
        
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val dir = File(getApplication<Application>().getExternalFilesDir(null), "llm").apply { mkdirs() }
                    val file = File(dir, "model.litertlm")
                    
                    // Public sample URL for a LiteRT-LM compatible Qwen model
                    val modelUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3-0.6b-it-litert.bin"
                    
                    var connection = URL(modelUrl).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    
                    var responseCode = connection.responseCode
                    
                    // Manually follow redirects if the automatic one fails (common across http/https)
                    var redirectCount = 0
                    while (responseCode / 100 == 3 && redirectCount < 5) {
                        val newUrl = connection.getHeaderField("Location")
                        connection = URL(newUrl).openConnection() as java.net.HttpURLConnection
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        responseCode = connection.responseCode
                        redirectCount++
                    }

                    if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                        throw Exception("HTTP $responseCode: ${connection.responseMessage}")
                    }
                    
                    val totalSize = connection.contentLength.toLong()
                    val input = connection.inputStream
                    val output = FileOutputStream(file)
                    
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalSize > 0) {
                            val progress = totalRead.toFloat() / totalSize
                            _state.value = _state.value.copy(downloadProgress = progress)
                        } else {
                            // If totalSize is unknown, just pulse the status
                            _state.value = _state.value.copy(statusMessage = "Downloading... (${totalRead / 1024 / 1024} MB)")
                        }
                    }
                    
                    output.flush()
                    output.close()
                    input.close()
                    true
                } catch (e: Exception) {
                    Log.e("AssistantVM", "Download error: ${e.javaClass.simpleName} - ${e.message}")
                    false
                }
            }
            
            if (success) {
                engine.initialize()
                _state.value = _state.value.copy(
                    downloading = false,
                    usingFallback = engine.usingFallback,
                    statusMessage = engine.statusMessage
                )
            } else {
                val errorMsg = _state.value.statusMessage
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), errorMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                _state.value = _state.value.copy(
                    downloading = false,
                    statusMessage = "Download failed. Check connection."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
    }
}
