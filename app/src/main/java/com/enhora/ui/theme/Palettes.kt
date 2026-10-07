package com.enhora.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.LocalTime
import kotlinx.coroutines.delay

/** Degradado de tres paradas (arriba, centro, abajo) de la pantalla de inicio. */
data class Gradient(val top: Color, val mid: Color, val bottom: Color)

/** Paleta elegible desde el menú lateral. [accent] tiñe los controles de Material. */
data class AppPalette(
    val name: String,
    val accent: Color,
    val light: Gradient,
    val dark: Gradient,
)

val AppPalettes = listOf(
    AppPalette(
        name = "Océano",
        accent = Color(0xFF0277BD),
        light = Gradient(Color(0xFF4FC3F7), Color(0xFF3F7FD0), Color(0xFF2B3F94)),
        dark = Gradient(Color(0xFF1E88B8), Color(0xFF2A4F9C), Color(0xFF141F4F)),
    ),
    AppPalette(
        name = "Bosque",
        accent = Color(0xFF2E7D32),
        light = Gradient(Color(0xFF7BD389), Color(0xFF2FA58A), Color(0xFF1C5F73)),
        dark = Gradient(Color(0xFF3E9E55), Color(0xFF1F7468), Color(0xFF0F3340)),
    ),
    AppPalette(
        name = "Atardecer",
        accent = Color(0xFFE65100),
        light = Gradient(Color(0xFFFFB74D), Color(0xFFF2705B), Color(0xFFA4377A)),
        dark = Gradient(Color(0xFFD9892B), Color(0xFFB5483F), Color(0xFF5E2352)),
    ),
)

/** Modo de apariencia: sigue al sistema, siempre claro o siempre noche. */
object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/** Índice de la opción "Automático": la paleta cambia sola a lo largo del día. */
val AutoPaletteIndex: Int get() = AppPalettes.size

private class DayKey(val hour: Float, val paletteName: String)

/**
 * Noche azul marino, solo para el modo "Auto" (no se elige a mano). El fondo de la app hace de cielo,
 * así que de noche tiene que ser de verdad oscuro para que luzcan la luna y las estrellas.
 */
private val NightPalette = AppPalette(
    name = "Noche",
    accent = Color(0xFF4F6BE8),
    light = Gradient(Color(0xFF1C2A66), Color(0xFF131C52), Color(0xFF0A1032)),
    dark = Gradient(Color(0xFF16215A), Color(0xFF0E1544), Color(0xFF070B26)),
)

/**
 * Ciclo del día, igual que el cielo: noche azul marino, amanecer cálido (sale el sol hacia las 6:00),
 * día de cielo azul, atardecer cálido (se pone hacia las 18:00) y vuelta a la noche.
 * Bosque queda como tema elegible a mano; mezclarlo en pleno día daría un cielo verde poco natural.
 */
private val DayCycle = listOf(
    DayKey(0f, "Noche"),
    DayKey(4.8f, "Noche"),
    DayKey(6.2f, "Atardecer"),
    DayKey(8.4f, "Océano"),
    DayKey(16.4f, "Océano"),
    DayKey(18.1f, "Atardecer"),
    DayKey(19.9f, "Noche"),
    DayKey(24f, "Noche"),
)

private fun paletteNamed(name: String) =
    if (name == NightPalette.name) NightPalette else AppPalettes.first { it.name == name }

private fun lerpGradient(a: Gradient, b: Gradient, t: Float) =
    Gradient(lerp(a.top, b.top, t), lerp(a.mid, b.mid, t), lerp(a.bottom, b.bottom, t))

/** Paleta intermedia para [hour] (0–24), mezclando suavemente las dos paletas vecinas del ciclo. */
fun autoPalette(hour: Float): AppPalette {
    val i = DayCycle.indexOfLast { it.hour <= hour }.coerceIn(0, DayCycle.size - 2)
    val from = DayCycle[i]
    val to = DayCycle[i + 1]
    val raw = ((hour - from.hour) / (to.hour - from.hour)).coerceIn(0f, 1f)
    val t = raw * raw * (3f - 2f * raw) // suavizado: sin saltos al empezar ni al terminar el tramo
    val a = paletteNamed(from.paletteName)
    val b = paletteNamed(to.paletteName)
    return AppPalette(
        name = "Automático",
        accent = lerp(a.accent, b.accent, t),
        light = lerpGradient(a.light, b.light, t),
        dark = lerpGradient(a.dark, b.dark, t),
    )
}

private fun nowHour(): Float = LocalTime.now().let { it.hour + it.minute / 60f }

/** Hora forzada mientras se previsualiza el día completo (mantener pulsado el cielo); null = hora real. */
val SkyPreviewHour = androidx.compose.runtime.mutableStateOf<Float?>(null)

/** Paleta elegida; con "Automático" se recalcula cada medio minuto. */
@Composable
fun rememberPalette(index: Int): AppPalette {
    AppPalettes.getOrNull(index)?.let { return it }
    var hour by remember { mutableFloatStateOf(nowHour()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            hour = nowHour()
        }
    }
    return autoPalette(SkyPreviewHour.value ?: hour)
}
