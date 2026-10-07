package com.enhora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enhora.R

/** Plus Jakarta Sans (OFL), fuente variable: un solo archivo para todos los pesos (minSdk 26). */
@OptIn(ExperimentalTextApi::class)
val EnHoraFont = FontFamily(
    listOf(
        FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
        FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
    ).map { w ->
        Font(
            R.font.plus_jakarta_sans, w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
        )
    },
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = EnHoraFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

val EnHoraTypography = Typography(
    displayLarge = style(64, 68, FontWeight.Light, -2.0),
    displayMedium = style(44, 48, FontWeight.Light, -1.2),
    displaySmall = style(34, 40, FontWeight.Normal, -0.6),
    headlineLarge = style(30, 36, FontWeight.Medium, -0.4),
    headlineMedium = style(26, 32, FontWeight.Medium, -0.3),
    headlineSmall = style(22, 28, FontWeight.Medium, -0.2),
    titleLarge = style(21, 28, FontWeight.SemiBold, -0.2),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.15),
    bodySmall = style(12, 16, FontWeight.Normal, 0.2),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.3),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.8),
    labelSmall = style(11, 16, FontWeight.Medium, 0.6),
)
