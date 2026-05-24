package com.imagecaltracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SketchyColorScheme = lightColorScheme(
    primary = SketchColors.AccentBlue,
    onPrimary = SketchColors.InkDark,
    secondary = SketchColors.AccentBlueSoft,
    onSecondary = SketchColors.InkDark,
    background = SketchColors.Paper,
    onBackground = SketchColors.InkDark,
    surface = SketchColors.Paper,
    onSurface = SketchColors.InkDark,
    surfaceVariant = SketchColors.PaperShadow,
    onSurfaceVariant = SketchColors.InkMid,
    outline = SketchColors.InkDark,
    outlineVariant = SketchColors.InkMid,
)

/**
 * Theme wrapper. We force light-only because the pencil-on-paper aesthetic
 * doesn't make sense in dark mode.
 */
@Composable
fun SketchyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SketchyColorScheme,
        typography = SketchTypography,
        content = content,
    )
}
