package com.imagecaltracker.ui.sketch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imagecaltracker.ui.theme.SketchColors

/**
 * A folder-style tab with a hand-drawn border: rounded top corners, straight
 * open bottom so it visually merges with the container beneath.
 *
 * The active tab renders slightly heavier strokes and bold text so it reads as
 * "in front of" the inactive tab.
 */
@Composable
fun SketchyFolderTab(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    seed: Int = 501,
) {
    val strokeWidthDp: Dp = if (active) 1.8.dp else 1.4.dp

    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .drawBehind {
                sketchyTabBorder(
                    rect = Rect(0f, 0f, size.width, size.height),
                    color = SketchColors.InkDark,
                    strokeWidth = strokeWidthDp.toPx(),
                    cornerRadius = cornerRadius.toPx(),
                    jitter = 1.2f,
                    passes = if (active) 3 else 2,
                    seed = seed,
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (active) SketchColors.InkDark else SketchColors.InkMid,
        )
    }
}
