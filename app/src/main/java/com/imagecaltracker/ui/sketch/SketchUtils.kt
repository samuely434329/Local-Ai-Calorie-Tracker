package com.imagecaltracker.ui.sketch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imagecaltracker.ui.theme.SketchColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural pencil-sketch primitives.
 *
 * Everything is drawn at runtime via Compose Canvas with deterministic
 * pseudo-random jitter so we get consistent (but irregular) strokes
 * instead of a "perfect" geometric look. Each shape is layered 2–3 times
 * with slight offsets and partial opacities to mimic real graphite passes.
 *
 * Smudges are semi-transparent irregular blobs; grain is fine random dots.
 * No bitmap assets are needed.
 */

// ---------------------------------------------------------------------------
// Stroke jitter helpers
// ---------------------------------------------------------------------------

/**
 * Returns a deterministic [Random] for a given seed. Using a fixed seed per
 * composable means the stroke wobble doesn't change every recomposition —
 * the sketch looks stable while the user scrolls or types.
 */
internal fun sketchRandom(seed: Int): Random = Random(seed)

/**
 * Draws a slightly wobbly straight line by walking from start to end in
 * small steps and adding jitter perpendicular to the line direction.
 * Repeats [passes] times with decreasing alpha, simulating multiple pencil strokes.
 */
fun DrawScope.sketchyLine(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
    jitter: Float = 1.2f,
    passes: Int = 2,
    seed: Int = 0,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = max(1f, kotlin.math.sqrt(dx * dx + dy * dy))
    val nx = -dy / len   // perpendicular unit vector
    val ny = dx / len
    val segments = (len / 6f).toInt().coerceIn(6, 80)

    repeat(passes) { pass ->
        val rand = sketchRandom(seed + pass * 31)
        val path = Path().apply {
            val ox = (rand.nextFloat() - 0.5f) * jitter
            val oy = (rand.nextFloat() - 0.5f) * jitter
            moveTo(start.x + nx * ox, start.y + ny * oy)
            for (i in 1..segments) {
                val t = i / segments.toFloat()
                val px = start.x + dx * t
                val py = start.y + dy * t
                val j = (rand.nextFloat() - 0.5f) * jitter * 2f
                lineTo(px + nx * j, py + ny * j)
            }
        }
        drawPath(
            path = path,
            color = color.copy(alpha = color.alpha * (1f - pass * 0.35f)),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Sketchy rounded rectangle border drawn as four wobbly lines + four short
 * corner curves. Approximates a hand-drawn box.
 */
fun DrawScope.sketchyRoundedRect(
    rect: Rect,
    color: Color,
    strokeWidth: Float,
    cornerRadius: Float,
    jitter: Float = 1.2f,
    passes: Int = 2,
    seed: Int = 0,
) {
    val r = cornerRadius.coerceAtMost(min(rect.width, rect.height) / 2f)
    val l = rect.left
    val t = rect.top
    val rt = rect.right
    val b = rect.bottom

    // Sides.
    sketchyLine(Offset(l + r, t), Offset(rt - r, t), color, strokeWidth, jitter, passes, seed + 1)
    sketchyLine(Offset(rt, t + r), Offset(rt, b - r), color, strokeWidth, jitter, passes, seed + 2)
    sketchyLine(Offset(rt - r, b), Offset(l + r, b), color, strokeWidth, jitter, passes, seed + 3)
    sketchyLine(Offset(l, b - r), Offset(l, t + r), color, strokeWidth, jitter, passes, seed + 4)

    // Corner arcs.
    sketchyArc(Offset(l + r, t + r), r, 180f, 90f, color, strokeWidth, jitter, passes, seed + 5)
    sketchyArc(Offset(rt - r, t + r), r, 270f, 90f, color, strokeWidth, jitter, passes, seed + 6)
    sketchyArc(Offset(rt - r, b - r), r, 0f, 90f, color, strokeWidth, jitter, passes, seed + 7)
    sketchyArc(Offset(l + r, b - r), r, 90f, 90f, color, strokeWidth, jitter, passes, seed + 8)
}

private fun min(a: Float, b: Float) = if (a < b) a else b

/**
 * Sketchy circular arc. [startAngleDeg] uses standard math convention
 * (0° = +x axis, increases counter-clockwise visually but we adapt).
 *
 * We approximate by sampling points along the arc and connecting them
 * with jittered segments, replayed in [passes] strokes for a layered look.
 */
fun DrawScope.sketchyArc(
    center: Offset,
    radius: Float,
    startAngleDeg: Float,
    sweepAngleDeg: Float,
    color: Color,
    strokeWidth: Float,
    jitter: Float = 1.5f,
    passes: Int = 2,
    seed: Int = 0,
) {
    if (sweepAngleDeg <= 0f) return
    val segments = max(8, (kotlin.math.abs(sweepAngleDeg) / 4f).toInt())

    repeat(passes) { pass ->
        val rand = sketchRandom(seed + pass * 17)
        val path = Path()
        for (i in 0..segments) {
            val a = Math.toRadians((startAngleDeg + sweepAngleDeg * (i / segments.toFloat())).toDouble())
            val rJitter = (rand.nextFloat() - 0.5f) * jitter
            val rad = radius + rJitter
            val x = center.x + cos(a).toFloat() * rad
            val y = center.y + sin(a).toFloat() * rad
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = color.alpha * (1f - pass * 0.3f)),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// ---------------------------------------------------------------------------
// Texture overlays — graph paper, smudges, grain
// ---------------------------------------------------------------------------

/**
 * Modifier that paints the graph-paper background AND background smudges
 * under content. Drawn with [drawBehind] so it never covers the UI.
 */
fun Modifier.graphPaperBackground(
    paper: Color = SketchColors.Paper,
    grid: Color = SketchColors.GridLine,
    cellDp: Dp = 14.dp,
    seed: Int = 7,
): Modifier = drawBehind {
    drawRect(paper)
    val cell = cellDp.toPx()
    val rand = sketchRandom(seed)

    // Vertical grid lines with slight color jitter so they don't look perfect.
    var x = 0f
    while (x <= size.width) {
        val a = 0.55f + rand.nextFloat() * 0.25f
        drawLine(
            color = grid.copy(alpha = grid.alpha * a),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 0.6f,
        )
        x += cell
    }
    // Horizontal.
    var y = 0f
    while (y <= size.height) {
        val a = 0.55f + rand.nextFloat() * 0.25f
        drawLine(
            color = grid.copy(alpha = grid.alpha * a),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.6f,
        )
        y += cell
    }
}
/**
 * Modifier that paints procedural pencil "smudges" UNDER content as part of
 * the paper texture (drawBehind). Soft irregular dark blobs made of
 * overlapping translucent ellipses give the paper a worn, used feel.
 *
 * Optimized version with fewer layers to prevent emulator lag.
 */
fun Modifier.smudgeBackground(
    color: Color = SketchColors.InkSmudge,
    count: Int = 8,
    seed: Int = 13,
): Modifier = drawBehind {
    val rand = sketchRandom(seed)
    repeat(count) {
        val cx = rand.nextFloat() * size.width
        val cy = rand.nextFloat() * size.height
        val rx = 20f + rand.nextFloat() * 40f
        val ry = 10f + rand.nextFloat() * 25f
        
        // Just 2 overlapping ellipses for a soft effect without killing performance.
        repeat(2) { l ->
            val a = (rand.nextFloat() * 0.05f + 0.03f) * (1f - l * 0.5f)
            val ox = (rand.nextFloat() - 0.5f) * 12f
            val oy = (rand.nextFloat() - 0.5f) * 12f
            drawOval(
                color = color.copy(alpha = a),
                topLeft = Offset(cx - rx + ox, cy - ry + oy),
                size = Size(rx * 2f, ry * 2f),
            )
        }
    }
}

/**
 * Modifier that overlays fine pencil grain on top of content — tiny dark
 * dots at very low alpha. Subtle enough to not interfere with readability,
 * but enough to give a paper-fiber feel.
 */
fun Modifier.grainOverlay(
    color: Color = SketchColors.InkMid,
    density: Float = 0.0006f,
    seed: Int = 23,
): Modifier = drawWithCache {
    onDrawWithContent {
        drawContent()
        val rand = sketchRandom(seed)
        val total = (size.width * size.height * density).toInt().coerceAtMost(2500)
        repeat(total) {
            val x = rand.nextFloat() * size.width
            val y = rand.nextFloat() * size.height
            val a = 0.04f + rand.nextFloat() * 0.10f
            drawCircle(
                color = color.copy(alpha = a),
                radius = 0.5f + rand.nextFloat() * 0.6f,
                center = Offset(x, y),
            )
        }
    }
}

/**
 * Modifier that draws a sketchy hand-drawn border around the composable.
 * Useful for input fields, cards, the phone bezel, etc.
 */
fun Modifier.sketchyBorder(
    color: Color = SketchColors.InkDark,
    strokeWidth: Dp = 1.5.dp,
    cornerRadius: Dp = 6.dp,
    jitter: Float = 1.3f,
    passes: Int = 2,
    seed: Int = 41,
): Modifier = drawBehind {
    sketchyRoundedRect(
        rect = Rect(0f, 0f, size.width, size.height),
        color = color,
        strokeWidth = strokeWidth.toPx(),
        cornerRadius = cornerRadius.toPx(),
        jitter = jitter,
        passes = passes,
        seed = seed,
    )
}

/**
 * Container that stacks paper background + smudges + grain in the right order.
 * Use this as the root of any screen for a consistent "real paper" feel.
 *
 * Drawing order: paper grid → smudges → content → grain.
 */
@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    seed: Int = 101,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .graphPaperBackground(seed = seed)
            .smudgeBackground(seed = seed + 1)
            .grainOverlay(seed = seed + 2),
        content = content,
    )
}
