package com.imagecaltracker.ui.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imagecaltracker.R
import com.imagecaltracker.ui.theme.PatrickHandFamily
import com.imagecaltracker.ui.theme.SketchColors

/**
 * Hand-drawn circular progress ring. Shows current/target with a big
 * cursive number in the middle, mimicking the reference image.
 */
@Composable
fun SketchyCalorieRing(
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    inkColor: Color = SketchColors.InkMid,
) {
    val safeTarget = if (target <= 0) 1 else target
    val fraction = (current.toFloat() / safeTarget).coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // 1. The full sketched image from jpeg.
        Image(
            painter = painterResource(R.drawable.calorie_circle_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 2. The "Masking" overlay.
        // Revised logic: more eat = less circle.
        // At 0% eaten, mask is 0 (full circle visible).
        // At 100% eaten, mask is 360 (circle completely covered).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweep = 360f * fraction
            if (sweep > 0f) {
                drawArc(
                    color = SketchColors.Paper,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = true,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = current.toString(),
                fontFamily = PatrickHandFamily,
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

@Composable
fun SketchyMacroBar(
    label: String,
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 12.dp,
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
                .sketchyBorder(
                    color = inkColor,
                    strokeWidth = 1.2.dp,
                    cornerRadius = barHeight / 2,
                    seed = seed,
                )
                .clip(RoundedCornerShape(barHeight / 2)),
            contentAlignment = Alignment.CenterStart
        ) {
            // 1. The full sketched bar image (Back)
            Image(
                painter = painterResource(R.drawable.sketched_bar),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // 2. The Masking layer (Front)
            // Gradually reveals the image from left to right.
            // At 0% progress, mask covers 100% (right to left).
            Canvas(modifier = Modifier.fillMaxSize()) {
                val revealWidth = size.width * fraction
                val maskWidth = size.width - revealWidth
                if (maskWidth > 0f) {
                    val radius = size.height / 2f

                    // Draw the main mask rectangle
                    drawRect(
                        color = SketchColors.Paper,
                        topLeft = Offset(revealWidth + radius, 0f),
                        size = Size(maxOf(0f, maskWidth - radius), size.height)
                    )

                    // Draw the "inverse" meniscus: two small rects at the top/bottom 
                    // and a circle cutout, making the liquid (image) look convex.
                    if (fraction > 0f && fraction < 1f) {
                        // Top corner mask
                        drawRect(
                            color = SketchColors.Paper,
                            topLeft = Offset(revealWidth, 0f),
                            size = Size(radius, radius)
                        )
                        // Bottom corner mask
                        drawRect(
                            color = SketchColors.Paper,
                            topLeft = Offset(revealWidth, size.height - radius),
                            size = Size(radius, radius)
                        )
                        //Draw the white rectangle from revealWidth.
                        // Then draw an ARC of Paper (white) with sweep -180.
                        drawArc(
                            color = SketchColors.Paper,
                            startAngle = 90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(revealWidth - radius, 0f),
                            size = Size(radius * 2, size.height)
                        )
                    }
                }
            }
        }
        Text(
            text = "$current/${target}g",
            style = MaterialTheme.typography.bodySmall,
            color = SketchColors.InkMid,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
