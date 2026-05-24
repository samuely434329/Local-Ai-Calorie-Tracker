package com.imagecaltracker.ui.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.imagecaltracker.ui.theme.SketchColors

/**
 * A button that looks hand-drawn: jittered border, light blue tint behind it,
 * pencil-ink text. Used wherever Material's heavier Button would clash.
 */
@Composable
fun SketchyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = SketchColors.AccentBlueSoft,
    seed: Int = 91,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(color = accent.copy(alpha = 0.35f * alpha), shape = RoundedCornerShape(8.dp))
            .sketchyBorder(
                color = SketchColors.InkDark.copy(alpha = alpha),
                strokeWidth = 1.6.dp,
                cornerRadius = 8.dp,
                seed = seed,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = SketchColors.InkDark.copy(alpha = alpha),
        )
    }
}
