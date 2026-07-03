package com.imagecaltracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette derived from the hand-drawn pencil sketch reference image.
 *
 * The paper is an off-white cream, the ink is a soft graphite gray (not pure black),
 * and a single light blue accent is used for filled progress arcs / highlights —
 * matching the colored-pencil shading in the mockup.
 */
object SketchColors {
    // Paper / surfaces.
    val Paper = Color(0xFFFDFDFD)           // white paper#fdfdfd)          // white paper
    val PaperShadow = Color(0xFFF5F5F5)    // light grey for contrast
    val GridLine = Color(0xFFD1F1F5)       // light blue graph-paper line

    // Ink (pencil).
    val InkDark = Color(0xFF2E2C28)        // dark graphite — main strokes/text
    val InkMid = Color(0xFF555149)         // mid pencil — secondary text/strokes
    val InkLight = Color(0xFF8A8579)       // light pencil — hint text / faint marks
    val InkSmudge = Color(0x33555149)      // semi-transparent for smudges

    // Accent (colored-pencil blue from the reference).
    val AccentBlue = Color(0xFF6FA8DC)
    val AccentBlueSoft = Color(0xFFA9C8E6)

    // Track / "empty" portions of the rings & bars.
    val Track = Color(0xFFD7D2C2)
}
