package com.imagecaltracker.ui.sketch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imagecaltracker.ui.theme.CaveatFamily
import com.imagecaltracker.ui.theme.SketchColors
import kotlin.math.min

/**
 * Hand-drawn circular progress ring. Shows current/target with a big
 * cursive number in the middle, mimicking the reference image.
 */
@Composable
fun SketchyCalorieRing(
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = SketchColors.Track,
    progressColor: Color = SketchColors.AccentBlue,
    inkColor: Color = SketchColors.InkDark,
    seed: Int = 1001,
) {
    val safeTarget = if (target <= 0) 1 else target
    val fraction = (current.toFloat() / safeTarget).coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val sw = strokeWidth.toPx()
                    val pad = sw + 4f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val radius = min(this.size.width, this.size.height) / 2f - pad

                    // Track: full circle, two passes of jittered strokes.
                    sketchyArc(
                        center = center,
                        radius = radius,
                        startAngleDeg = 0f,
                        sweepAngleDeg = 360f,
                        color = trackColor,
                        strokeWidth = sw,
                        jitter = 1.6f,
                        passes = 2,
                        seed = seed,
                    )
                    // Progress: starts at top (-90°) and sweeps clockwise.
                    val sweep = 360f * fraction
                    if (sweep > 0f) {
                        sketchyArc(
                            center = center,
                            radius = radius,
                            startAngleDeg = -90f,
                            sweepAngleDeg = sweep,
                            color = progressColor,
                            strokeWidth = sw * 1.1f,
                            jitter = 2f,
                            passes = 3,
                            seed = seed + 99,
                        )
                    }
                },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = current.toString(),
                fontFamily = CaveatFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                color = inkColor,
            )
            Text(
                text = "of $target kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchColors.InkMid,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Hand-drawn linear progress bar with a sketchy outline rectangle and a
 * jittered "filled" section. Used for macros.
 */
@Composable
fun SketchyMacroBar(
    label: String,
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 14.dp,
    progressColor: Color = SketchColors.AccentBlue,
    inkColor: Color = SketchColors.InkDark,
    seed: Int = 0,
) {
    val safeTarget = if (target <= 0) 1 else target
    val fraction = (current.toFloat() / safeTarget).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = inkColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(top = 4.dp)
                .drawBehind {
                    val w = this.size.width
                    val h = this.size.height
                    // Sketchy outline.
                    sketchyRoundedRect(
                        rect = Rect(0f, 0f, w, h),
                        color = inkColor,
                        strokeWidth = 1.5f,
                        cornerRadius = h / 2f,
                        jitter = 1f,
                        passes = 2,
                        seed = seed + 11,
                    )
                    // Filled portion: a series of horizontal jittered strokes
                    // to simulate pencil hatching.
                    if (fraction > 0f) {
                        val fillW = (w - 4f) * fraction
                        val rand = sketchRandom(seed + 23)
                        val strokeCount = 6
                        repeat(strokeCount) { i ->
                            val y = 3f + (h - 6f) * (i / (strokeCount - 1f))
                            val xJ = rand.nextFloat() * 2f
                            sketchyLine(
                                start = Offset(3f + xJ, y),
                                end = Offset(2f + fillW, y),
                                color = progressColor.copy(alpha = 0.8f),
                                strokeWidth = 1.6f,
                                jitter = 0.8f,
                                passes = 1,
                                seed = seed + 31 + i,
                            )
                        }
                    }
                },
        )
        Text(
            text = "$current/${target}g",
            style = MaterialTheme.typography.bodySmall,
            color = SketchColors.InkMid,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
