package com.imagecaltracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.imagecaltracker.assistant.ModelFamily
import com.imagecaltracker.assistant.ModelSpec
import com.imagecaltracker.assistant.ModelStatus
import com.imagecaltracker.ui.sketch.SketchyButton
import com.imagecaltracker.ui.sketch.sketchyBorder
import com.imagecaltracker.ui.theme.SketchColors

/**
 * Bottom sheet that lists every catalog model with per-model status and
 * actions, plus a section for the Gemini API key. Rendered when the user
 * taps the model chip in the top bar or the model settings button inside
 * the assistant dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsSheet(
    models: List<ModelStatus>,
    selectedId: String,
    geminiApiKey: String,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (String) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SketchColors.Paper,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "MODEL SETTINGS",
                style = MaterialTheme.typography.titleLarge,
                color = SketchColors.InkDark,
            )
            Text(
                text = "Only Gemma models can power the Quick Scan button on the main screen.",
                style = MaterialTheme.typography.labelSmall,
                color = SketchColors.InkMid,
            )
            HorizontalDivider(color = SketchColors.GridLine, thickness = 0.6.dp)

            val (local, remote) = models.partition { it.spec.family != ModelFamily.Gemini }

            local.forEachIndexed { index, status ->
                ModelRow(
                    status = status,
                    selected = status.spec.id == selectedId,
                    onSelect = { onSelectModel(status.spec.id) },
                    onDownload = { onDownloadModel(status.spec.id) },
                    seed = 1001 + index * 7,
                )
            }

            if (remote.isNotEmpty()) {
                HorizontalDivider(color = SketchColors.GridLine, thickness = 0.6.dp)
                Text(
                    text = "REMOTE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SketchColors.InkMid,
                )
                remote.forEach { status ->
                    GeminiSection(
                        status = status,
                        selected = status.spec.id == selectedId,
                        apiKey = geminiApiKey,
                        onSelect = { onSelectModel(status.spec.id) },
                        onSaveKey = onSaveGeminiKey,
                    )
                }
            }

            Box(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModelRow(
    status: ModelStatus,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    seed: Int,
) {
    val spec = status.spec
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) SketchColors.AccentBlueSoft.copy(alpha = 0.25f) else SketchColors.Paper,
                shape = RoundedCornerShape(10.dp),
            )
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = if (selected) 1.6.dp else 1.2.dp,
                cornerRadius = 10.dp,
                seed = seed,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = SketchColors.InkDark,
                )
                Text(
                    text = statusLine(status, selected),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) SketchColors.InkMid else SketchColors.InkLight,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                when {
                    status.downloading -> Text(
                        text = "${(status.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = SketchColors.InkMid,
                    )
                    !status.downloaded -> SketchyButton(
                        text = "Download",
                        onClick = onDownload,
                        seed = seed + 1,
                        modifier = Modifier.widthIn(min = 108.dp),
                    )
                    selected -> Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SketchColors.InkDark,
                    )
                    else -> SketchyButton(
                        text = "Select",
                        onClick = onSelect,
                        seed = seed + 2,
                        modifier = Modifier.widthIn(min = 92.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GeminiSection(
    status: ModelStatus,
    selected: Boolean,
    apiKey: String,
    onSelect: () -> Unit,
    onSaveKey: (String) -> Unit,
) {
    var draft by remember(apiKey) { mutableStateOf(apiKey) }
    val spec = status.spec

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) SketchColors.AccentBlueSoft.copy(alpha = 0.25f) else SketchColors.Paper,
                shape = RoundedCornerShape(10.dp),
            )
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = if (selected) 1.6.dp else 1.2.dp,
                cornerRadius = 10.dp,
                seed = 1077,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = SketchColors.InkDark,
                )
                Text(
                    text = if (apiKey.isNotBlank()) "API key stored" else "API key required",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchColors.InkLight,
                )
            }
            if (selected) {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = SketchColors.InkDark,
                )
            } else {
                SketchyButton(
                    text = "Select",
                    onClick = onSelect,
                    seed = 1085,
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.widthIn(min = 92.dp),
                )
            }
        }

        Text(
            text = "Google AI Studio API key",
            style = MaterialTheme.typography.labelSmall,
            color = SketchColors.InkMid,
        )
        MaskedSketchyTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "AIza…",
            seed = 1091,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SketchyButton(
                text = "Save",
                onClick = { onSaveKey(draft.trim()) },
                seed = 1093,
                enabled = draft.trim() != apiKey,
                modifier = Modifier.weight(1f),
            )
            SketchyButton(
                text = "Clear",
                onClick = {
                    draft = ""
                    onSaveKey("")
                },
                seed = 1095,
                enabled = apiKey.isNotBlank() || draft.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
        if (selected && apiKey.isBlank()) {
            Text(
                text = "Enter and save an API key to send messages to Gemini.",
                style = MaterialTheme.typography.labelSmall,
                color = SketchColors.InkDark,
            )
        }
    }
}

@Composable
private fun MaskedSketchyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    // SketchyTextField doesn't take a visualTransformation, so inline a masked variant.
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = 1.4.dp,
                cornerRadius = 6.dp,
                seed = seed,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
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
            singleLine = true,
            cursorBrush = SolidColor(SketchColors.InkDark),
            textStyle = LocalTextStyle.current.copy(color = SketchColors.InkDark),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun statusLine(status: ModelStatus, selected: Boolean): String {
    val spec: ModelSpec = status.spec
    val size = if (spec.approxSizeMb > 0) " · ${formatMb(spec.approxSizeMb)}" else ""
    val state = when {
        status.downloading -> "Downloading ${(status.progress * 100).toInt()}%"
        status.downloaded -> if (selected) "Ready" else "Downloaded"
        else -> "Not downloaded"
    }
    val quickScan = if (spec.supportsQuickScan) " · Quick Scan enabled" else ""
    return "$state$size$quickScan"
}

private fun formatMb(mb: Int): String {
    return if (mb >= 1024) "${"%.1f".format(mb / 1024f)} GB" else "$mb MB"
}
