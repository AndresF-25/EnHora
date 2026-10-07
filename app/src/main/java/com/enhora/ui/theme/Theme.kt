package com.enhora.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.enhora.data.Slot

/** Indica si la app está en modo noche (puede forzarse desde el menú lateral). */
val LocalDarkTheme = compositionLocalOf { false }

private val LightBase = lightColorScheme(
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
)

private val DarkBase = darkColorScheme(
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A),
)

private fun scheme(dark: Boolean, accent: Color): ColorScheme =
    if (dark) {
        DarkBase.copy(
            primary = lerp(accent, Color.White, 0.55f),
            onPrimary = lerp(accent, Color.Black, 0.6f),
            primaryContainer = lerp(accent, Color.Black, 0.55f),
            onPrimaryContainer = lerp(accent, Color.White, 0.85f),
        )
    } else {
        LightBase.copy(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.85f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
        )
    }

@Immutable
data class SlotPalette(val container: Color, val accent: Color)

@Composable
fun slotPalette(slot: Slot): SlotPalette {
    val dark = LocalDarkTheme.current
    return when (slot) {
        Slot.MORNING ->
            if (dark) SlotPalette(Color(0xFF4A3A00), Color(0xFFFFC857))
            else SlotPalette(Color(0xFFFFE9B3), Color(0xFFB26A00))
        Slot.AFTERNOON ->
            if (dark) SlotPalette(Color(0xFF0F3A63), Color(0xFF8CC4FF))
            else SlotPalette(Color(0xFFD3E8FF), Color(0xFF1565C0))
        Slot.NIGHT ->
            if (dark) SlotPalette(Color(0xFF2E2766), Color(0xFFC2B9FF))
            else SlotPalette(Color(0xFFE0DBFF), Color(0xFF4F3FB0))
    }
}

@Composable
fun EnHoraTheme(
    dark: Boolean,
    palette: AppPalette = AppPalettes.first(),
    content: @Composable () -> Unit,
) {
    val gradient = if (dark) palette.dark else palette.light
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalGradient provides gradient) {
        MaterialTheme(colorScheme = scheme(dark, palette.accent), typography = EnHoraTypography, content = content)
    }
}

/** Degradado de la paleta activa, según el modo claro/noche. */
val LocalGradient = compositionLocalOf { AppPalettes.first().light }

fun Modifier.gradientBackground(g: Gradient): Modifier = background(
    Brush.verticalGradient(0f to g.top, 0.6f to g.mid, 1f to g.bottom),
)

/**
 * Colores para contenido que se dibuja directamente sobre el degradado: textos blancos,
 * superficies de cristal translúcido y botones blancos con texto del color del degradado.
 * No incluir aquí diálogos ni hojas inferiores: llevan su propia superficie.
 */
@Composable
fun OnGradientTheme(content: @Composable () -> Unit) {
    val g = LocalGradient.current
    val white = Color.White
    val glass = white.copy(alpha = 0.14f)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = white,
            onPrimary = g.mid,
            primaryContainer = white.copy(alpha = 0.22f),
            onPrimaryContainer = white,
            secondaryContainer = white.copy(alpha = 0.28f),
            onSecondaryContainer = white,
            background = Color.Transparent,
            onBackground = white,
            surface = Color.Transparent,
            onSurface = white,
            surfaceVariant = glass,
            onSurfaceVariant = white.copy(alpha = 0.78f),
            outline = white.copy(alpha = 0.6f),
            outlineVariant = white.copy(alpha = 0.3f),
            surfaceContainerLowest = glass,
            surfaceContainerLow = glass,
            surfaceContainer = glass,
            surfaceContainerHigh = glass,
            surfaceContainerHighest = glass,
        ),
    ) {
        // Fuera de un Scaffold o Surface el texto heredaría el negro por defecto.
        CompositionLocalProvider(LocalContentColor provides white, content = content)
    }
}

/** Pantalla completa sobre el degradado, con iconos del sistema claros. */
@Composable
fun GradientScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    LightSystemBars()
    Box(
        modifier.fillMaxSize().gradientBackground(LocalGradient.current)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        OnGradientTheme(content)
    }
}

/** Iconos de la barra de estado y de navegación claros (el fondo es el degradado). */
@Composable
fun LightSystemBars() {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}

/** Colores de una tarjeta de día: fondo pastel, texto principal y pastilla de tarea. */
@Immutable
data class DayCardColors(val container: Color, val content: Color, val chip: Color, val onChip: Color)

/**
 * Color de la tarjeta de un día (1 = lunes): recorre el degradado de la paleta elegida,
 * aclarado en modo claro y oscurecido en modo noche, para que siempre combine con el tema.
 */
@Composable
fun dayCardColors(dayOfWeek: Int): DayCardColors {
    val dark = LocalDarkTheme.current
    val g = LocalGradient.current
    val t = ((dayOfWeek - 1).coerceIn(0, 6)) / 6f
    val base = if (t < 0.5f) lerp(g.top, g.mid, t * 2f) else lerp(g.mid, g.bottom, (t - 0.5f) * 2f)
    return if (dark) {
        val content = lerp(base, Color.White, 0.85f)
        DayCardColors(lerp(base, Color.Black, 0.35f), content, content, lerp(base, Color.Black, 0.7f))
    } else {
        val content = lerp(base, Color.Black, 0.65f)
        DayCardColors(lerp(base, Color.White, 0.72f), content, content, Color.White)
    }
}
