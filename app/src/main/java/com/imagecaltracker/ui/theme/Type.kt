package com.imagecaltracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.imagecaltracker.R

/**
 * Downloadable Google Fonts give us a handwritten look without bundling binary font files.
 *
 * `Patrick Hand` — neat printed handwriting, used for body text & numbers.
 * `Caveat`        — flowing handwritten cursive, used for the big calorie number.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val patrickHand = FontFamily(
    Font(googleFont = GoogleFont("Patrick Hand"), fontProvider = googleFontProvider)
)

private val caveat = FontFamily(
    Font(googleFont = GoogleFont("Caveat"), fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

/** Exposed so individual composables can opt into the cursive face for big display numbers. */
val CaveatFamily: FontFamily = caveat
val PatrickHandFamily: FontFamily = patrickHand

val SketchTypography = Typography(
    displayLarge = TextStyle(fontFamily = caveat, fontWeight = FontWeight.Bold, fontSize = 56.sp),
    headlineMedium = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 22.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 20.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 18.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = patrickHand, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.5.sp),
)
