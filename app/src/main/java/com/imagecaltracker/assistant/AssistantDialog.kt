package com.imagecaltracker.assistant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.imagecaltracker.ui.sketch.SketchyButton
import com.imagecaltracker.ui.sketch.SketchyTextField
import com.imagecaltracker.ui.sketch.sketchyBorder
import com.imagecaltracker.ui.theme.SketchColors

/**
 * The local-AI assistant dialog. Two tabs:
 *  - CHAT: free-form diet/nutrition questions.
 *  - SCAN: take a photo, write a mandatory description, get a macro estimate,
 *          and (optionally) push it into the food log via [onAddToLog].
 *
 * The dialog owns its own [AssistantViewModel] so closing the dialog releases
 * the underlying LLM handle.
 */
@Composable
fun AssistantDialog(
    onDismiss: () -> Unit,
    onAddToLog: (MacroEstimate) -> Unit,
    viewModel: AssistantViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(AssistantTab.Chat) }

    Dialog(
        onDismissRequest = onDismiss,
        // Allow our own large layout — Material's default dialog is too narrow.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(color = SketchColors.Paper, shape = RoundedCornerShape(14.dp))
                .sketchyBorder(
                    color = SketchColors.InkDark,
                    strokeWidth = 1.8.dp,
                    cornerRadius = 14.dp,
                    seed = 801,
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeaderRow(
                usingFallback = state.usingFallback, 
                statusMessage = state.statusMessage,
                downloading = state.downloading,
                downloadProgress = state.downloadProgress,
                onDownload = viewModel::downloadModel,
                onClose = onDismiss
            )
            TabSwitcher(current = tab, onSelect = { tab = it })
            HorizontalDivider(color = SketchColors.GridLine, thickness = 0.6.dp)

            when (tab) {
                AssistantTab.Chat -> ChatTab(
                    state = state,
                    onSend = viewModel::sendChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp, max = 520.dp),
                )
                AssistantTab.Scan -> ScanTab(
                    state = state,
                    onNewPhotoUri = viewModel::newPhotoUri,
                    onPhotoCaptured = viewModel::onPhotoCaptured,
                    onDescriptionChange = viewModel::setDescription,
                    onEstimate = viewModel::estimateMacros,
                    onReset = viewModel::resetScan,
                    onAddToLog = { estimate ->
                        onAddToLog(estimate)
                        viewModel.resetScan()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp, max = 600.dp),
                )
            }
        }
    }
}

private enum class AssistantTab { Chat, Scan }

// ---------------------------------------------------------------------------
// Header + tab switcher
// ---------------------------------------------------------------------------

@Composable
private fun HeaderRow(
    usingFallback: Boolean,
    statusMessage: String,
    downloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ASSISTANT",
                style = MaterialTheme.typography.titleLarge,
                color = SketchColors.InkDark,
            )
            val progressText = if (downloading) " (${(downloadProgress * 100).toInt()}%)" else ""
            Text(
                text = "offline · $statusMessage$progressText",
                style = MaterialTheme.typography.labelSmall,
                color = if (usingFallback) SketchColors.InkMid else SketchColors.InkLight,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (usingFallback && !downloading) {
                SketchyButton(
                    text = "Download Model",
                    onClick = onDownload,
                    seed = 812,
                    modifier = Modifier.widthIn(min = 120.dp),
                )
            }
            SketchyButton(
                text = "Close",
                onClick = onClose,
                seed = 811,
                modifier = Modifier.widthIn(min = 88.dp),
            )
        }
    }
}

@Composable
private fun TabSwitcher(current: AssistantTab, onSelect: (AssistantTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabPill(
            label = "CHAT",
            selected = current == AssistantTab.Chat,
            onClick = { onSelect(AssistantTab.Chat) },
            seed = 821,
            modifier = Modifier.weight(1f),
        )
        TabPill(
            label = "SCAN",
            selected = current == AssistantTab.Scan,
            onClick = { onSelect(AssistantTab.Scan) },
            seed = 831,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) SketchColors.AccentBlueSoft else SketchColors.Paper
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(
                color = accent.copy(alpha = if (selected) 0.55f else 0f),
                shape = RoundedCornerShape(8.dp)
            )
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = if (selected) 1.8.dp else 1.2.dp,
                cornerRadius = 8.dp,
                seed = seed,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = SketchColors.InkDark,
        )
    }
}

// ---------------------------------------------------------------------------
// Chat tab
// ---------------------------------------------------------------------------

@Composable
private fun ChatTab(
    state: AssistantUiState,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to the most recent message whenever the list grows.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }
            if (state.sending) {
                item(key = "typing") {
                    ThinkingBubble()
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchyTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "Ask about diet, macros, meals…",
                seed = 841,
                modifier = Modifier.weight(1f),
            )
            SketchyButton(
                text = "Send",
                enabled = draft.isNotBlank() && !state.sending,
                seed = 851,
                onClick = {
                    val toSend = draft
                    draft = ""
                    onSend(toSend)
                },
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatRole.User
    val bubbleColor =
        if (isUser) SketchColors.AccentBlueSoft.copy(alpha = 0.45f) else SketchColors.PaperShadow.copy(alpha = 0.6f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(color = bubbleColor, shape = RoundedCornerShape(10.dp))
                .sketchyBorder(
                    color = SketchColors.InkDark,
                    strokeWidth = 1.2.dp,
                    cornerRadius = 10.dp,
                    seed = (msg.id * 7 + 901).toInt(),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = SketchColors.InkDark,
            )
        }
    }
}

/**
 * Placeholder bubble shown while the model is generating. A pulsing color
 * shade tells the user the assistant is alive — not stuck — even though
 * we can't stream partial tokens out of the LiteRT-LM API yet.
 */
@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-phase",
    )
    // Lerp between two ink shades so the text smoothly fades light → dark → light.
    val animatedColor = Color(
        red = lerp(SketchColors.InkLight.red, SketchColors.InkDark.red, phase),
        green = lerp(SketchColors.InkLight.green, SketchColors.InkDark.green, phase),
        blue = lerp(SketchColors.InkLight.blue, SketchColors.InkDark.blue, phase),
        alpha = 1f,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = SketchColors.PaperShadow.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                )
                .sketchyBorder(
                    color = SketchColors.InkDark,
                    strokeWidth = 1.2.dp,
                    cornerRadius = 10.dp,
                    seed = 905,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "thinking…",
                style = MaterialTheme.typography.bodyMedium,
                color = animatedColor,
            )
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ---------------------------------------------------------------------------
// Scan tab
// ---------------------------------------------------------------------------

@Composable
public fun ScanTab(
    state: AssistantUiState,
    onNewPhotoUri: () -> Uri,
    onPhotoCaptured: (Uri) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onEstimate: () -> Unit,
    onReset: () -> Unit,
    onAddToLog: (MacroEstimate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Holds the Uri we passed to the camera intent so we can confirm it on return.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingUri
        if (success && uri != null) onPhotoCaptured(uri)
        pendingUri = null
    }

    val descriptionMissing = state.description.isBlank()
    val photoMissing = state.photoUri == null
    val canEstimate = !descriptionMissing && !photoMissing && !state.estimating

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // -- Photo slot -------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(10.dp))
                .background(SketchColors.PaperShadow.copy(alpha = 0.4f))
                .sketchyBorder(
                    color = SketchColors.InkDark,
                    strokeWidth = 1.4.dp,
                    cornerRadius = 10.dp,
                    seed = 861,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val uri = state.photoUri
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Captured meal photo",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "No photo yet — tap TAKE PHOTO below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchColors.InkLight,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SketchyButton(
                text = if (state.photoUri == null) "Take Photo" else "Retake",
                seed = 871,
                modifier = Modifier.weight(1f),
                onClick = {
                    val uri = onNewPhotoUri()
                    pendingUri = uri
                    takePictureLauncher.launch(uri)
                },
            )
            if (state.photoUri != null || state.description.isNotEmpty()) {
                SketchyButton(
                    text = "Reset",
                    seed = 881,
                    modifier = Modifier.weight(1f),
                    onClick = onReset,
                )
            }
        }

        // -- Description (mandatory) -----------------------------------------
        Column {
            Text(
                text = "DESCRIBE THE MEAL (required)",
                style = MaterialTheme.typography.labelSmall,
                color = SketchColors.InkMid,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            MultilineSketchyTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                placeholder = "e.g. Large grilled chicken bowl with rice, avocado, black beans",
                seed = 891,
                modifier = Modifier.fillMaxWidth(),
            )
            if (photoMissing || descriptionMissing) {
                Text(
                    text = buildString {
                        append("Need ")
                        if (photoMissing) append("a photo") else append("")
                        if (photoMissing && descriptionMissing) append(" and ")
                        if (descriptionMissing) append("a description")
                        append(" before estimating.")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchColors.InkLight,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        //
        SketchyButton(
            text = if (state.estimating) "Estimating…" else "Estimate Macros",
            enabled = canEstimate,
            seed = 901,
            modifier = Modifier.fillMaxWidth(),
            onClick = onEstimate,
        )

        // -- Result card -----------------------------------------------------
        state.estimate?.let { est ->
            EstimateCard(estimate = est, onAddToLog = onAddToLog)
        }
    }
}

@Composable
private fun EstimateCard(estimate: MacroEstimate, onAddToLog: (MacroEstimate) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SketchColors.AccentBlueSoft.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp)
            )
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = 1.4.dp,
                cornerRadius = 10.dp,
                seed = 911,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = estimate.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = SketchColors.InkDark,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MacroChip(label = "kcal", value = estimate.calories)
            MacroChip(label = "P (g)", value = estimate.proteinG)
            MacroChip(label = "C (g)", value = estimate.carbsG)
            MacroChip(label = "F (g)", value = estimate.fatsG)
        }
        Text(
            text = when (estimate.source) {
                MacroSource.Llm -> "Estimated by on-device model. Tap below to log it."
                MacroSource.Heuristic -> "Heuristic estimate (no model loaded). Edit after logging if needed."
            },
            style = MaterialTheme.typography.labelSmall,
            color = SketchColors.InkLight,
        )
        SketchyButton(
            text = "Add to Today's Log",
            seed = 921,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAddToLog(estimate) },
        )
    }
}

@Composable
private fun MacroChip(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = SketchColors.InkDark,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SketchColors.InkMid,
        )
    }
}

/**
 * Multi-line cousin of SketchyTextField. The shared sketch component is single-line,
 * and the description field needs to wrap, so we inline a small variant here.
 */
@Composable
private fun MultilineSketchyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 72.dp)
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = 1.4.dp,
                cornerRadius = 6.dp,
                seed = seed,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = SketchColors.InkLight,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            cursorBrush = SolidColor(SketchColors.InkDark),
            textStyle = LocalTextStyle.current.copy(color = SketchColors.InkDark),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
