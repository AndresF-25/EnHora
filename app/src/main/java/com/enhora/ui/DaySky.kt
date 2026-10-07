package com.enhora.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.enhora.ui.theme.Gradient
import com.enhora.ui.theme.LocalGradient
import com.enhora.ui.theme.SkyPreviewHour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * El cielo del día: el fondo de la app hace de cielo.
 *
 *  - No tiene cielo propio: sol, luna, estrellas, resplandor y colinas se dibujan sobre el degradado
 *    del tema, que en modo "Auto" ya sigue las horas del día. Por eso no hay frontera de color.
 *  - Ocupa todo el ancho de la pantalla, sin contorno. Arriba y abajo se difumina con una máscara,
 *    y a los lados el sol entra y sale por el borde de la pantalla.
 *  - El sol es la flor de EnHora: sale por la izquierda hacia las 6:00, está en lo más alto al mediodía
 *    y se oculta por la derecha hacia las 18:00. Sus tres anillos de pétalos giran en el mismo sentido,
 *    como las manecillas de un reloj: el interior da una vuelta en 45 s, el medio en 90 s y el
 *    exterior en 180 s.
 *  - La luna sale cuando el sol se oculta y cruza la noche; la rodean tres anillos de puntos que giran igual.
 *  - Mantén pulsado para ver el día entero en 12 segundos.
 *
 * `drawSkyScene` también dibuja el fondo del widget, así que ambos muestran lo mismo a la misma hora.
 */

private const val INNER_MS = 45_000
private const val MIDDLE_MS = 90_000
private const val OUTER_MS = 180_000
private const val PREVIEW_MS = 12_000

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Pico suave centrado en [center] horas con semiancho [width] (1 en el centro, 0 fuera). */
private fun bump(hour: Float, center: Float, width: Float) = 1f - smooth(abs(hour - center) / width)

/** Cuánto se ven las estrellas (0 de día, 1 de noche). */
private fun starsAt(hour: Float): Float {
    val keys = listOf(0f to 1f, 5f to 1f, 6.6f to 0f, 17.4f to 0f, 19.6f to 1f, 24f to 1f)
    val i = keys.indexOfLast { it.first <= hour }.coerceIn(0, keys.size - 2)
    val (h0, v0) = keys[i]
    val (h1, v1) = keys[i + 1]
    return v0 + (v1 - v0) * smooth((hour - h0) / (h1 - h0))
}

private data class Star(val x: Float, val y: Float, val size: Float, val phase: Float)

private val Stars = Random(11).let { rnd ->
    List(46) {
        Star(
            x = rnd.nextFloat(),
            y = 0.03f + rnd.nextFloat() * 0.55f,
            size = 0.6f + rnd.nextFloat() * 1.1f,
            phase = rnd.nextFloat() * 360f,
        )
    }
}

internal fun currentHour(): Float = LocalTime.now().let { it.hour + it.minute / 60f + it.second / 3600f }

private fun skyDescription(hour: Float) = when {
    hour < 5f || hour >= 20.5f -> "Cielo de noche con la luna"
    hour < 8f -> "Cielo al amanecer con el sol saliendo"
    hour < 17f -> "Cielo de día con el sol en lo alto"
    else -> "Cielo al atardecer con el sol ocultándose"
}

@Composable
fun DaySky(modifier: Modifier = Modifier, height: Dp = 280.dp) {
    var clockHour by remember { mutableFloatStateOf(currentHour()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockHour = currentHour()
            delay(10_000)
        }
    }

    // Vista previa: mantener pulsado recorre las 24 horas, empezando y terminando en la hora actual.
    val preview = remember { Animatable(0f) }
    var previewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hour = if (previewing) preview.value % 24f else clockHour
    val gradient = LocalGradient.current
    // Mientras dura la vista previa, el tema Auto (y con él todo el fondo de la app) sigue la misma hora.
    androidx.compose.runtime.SideEffect { SkyPreviewHour.value = if (previewing) hour else null }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SkyPreviewHour.value = null } }

    // Con las animaciones del sistema desactivadas, el cielo no gira (solo cambia con la hora).
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val transition = rememberInfiniteTransition(label = "sky")
    fun spin(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = LinearEasing), RepeatMode.Restart)
    val inner by transition.animateFloat(0f, 360f, spin(INNER_MS), label = "inner")
    val middle by transition.animateFloat(0f, 360f, spin(MIDDLE_MS), label = "middle")
    val outer by transition.animateFloat(0f, 360f, spin(OUTER_MS), label = "outer")

    Canvas(
        modifier.fillMaxWidth().height(height)
            // Capa propia: la máscara de los bordes solo afecta a la escena, no al resto de la pantalla.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics { contentDescription = skyDescription(hour) }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    if (!previewing) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            previewing = true
                            preview.snapTo(clockHour)
                            preview.animateTo(clockHour + 24f, tween(PREVIEW_MS, easing = LinearEasing))
                            previewing = false
                        }
                    }
                })
            },
    ) {
        if (animate) {
            drawSkyScene(hour, gradient, inner, middle, outer, fillBackground = false, fadeEdges = true)
        } else {
            drawSkyScene(hour, gradient, 0f, 0f, 0f, fillBackground = false, fadeEdges = true)
        }
    }
}

/**
 * Dibuja el cielo en todo el lienzo. Con [fillBackground] pinta antes el degradado del tema (widget);
 * con [fadeEdges] difumina arriba y abajo para que no haya ningún borde (pantalla de inicio).
 */
internal fun DrawScope.drawSkyScene(
    hour: Float,
    g: Gradient,
    inner: Float,
    middle: Float,
    outer: Float,
    fillBackground: Boolean,
    fadeEdges: Boolean,
) {
    val w = size.width
    val h = size.height
    val u = minOf(h, w * 0.6f)         // unidad de tamaño: lo que crece o se encoge con el lienzo
    val horizonY = 0.74f * h
    val arcHeight = 0.50f * h

    if (fillBackground) {
        drawRect(Brush.verticalGradient(0f to g.top, 0.6f to g.mid, 1f to g.bottom))
    }

    // Estrellas, parpadeando despacio con el giro del anillo interior
    val night = starsAt(hour)
    if (night > 0.01f) {
        Stars.forEach { st ->
            val twinkle = 0.65f + 0.35f * sin(Math.toRadians((inner * 8f + st.phase).toDouble())).toFloat()
            drawCircle(Color.White.copy(alpha = night * twinkle), radius = st.size.dp.toPx(), center = Offset(st.x * w, st.y * h))
        }
    }

    // Posición del sol: entra por el borde izquierdo y sale por el derecho
    val ts = (hour - 6f) / 12f
    val sunElev = sin(PI * ts).toFloat()
    val sx = w / 2f - cos(PI * ts).toFloat() * 0.58f * w
    val sy = horizonY - sunElev * arcHeight

    // Resplandor del amanecer y del atardecer, sobre el horizonte y cerca del sol
    val twilight = max(bump(hour, 6.1f, 1.7f), bump(hour, 18.0f, 1.7f))
    if (twilight > 0.01f) {
        val gc = Offset(sx.coerceIn(0f, w), horizonY)
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFFFA862).copy(alpha = 0.6f * twilight), Color.Transparent),
                center = gc, radius = 0.85f * w,
            ),
        )
    }

    // Luna: sale al caer el sol por la izquierda y cruza la noche
    val tm = if (hour >= 12f) (hour - 18f) / 12f else (hour + 6f) / 12f
    if (tm > -0.2f && tm < 1.2f) {
        val elevM = sin(PI * tm).toFloat()
        val c = Offset(w / 2f - cos(PI * tm).toFloat() * 0.58f * w, horizonY - elevM * arcHeight)
        val moonR = 0.085f * u
        val fade = smooth((elevM + 0.1f) / 0.25f) * night
        if (fade > 0.01f) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFE8ECFF).copy(alpha = 0.40f * fade), Color.Transparent), c, moonR * 3.4f),
                radius = moonR * 3.4f, center = c,
            )
            val disc = Path().apply { addOval(Rect(c, moonR)) }
            val bite = Path().apply { addOval(Rect(c + Offset(moonR * 0.5f, -moonR * 0.22f), moonR * 0.88f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), Color(0xFFF6F2DE).copy(alpha = fade))
            // Tres anillos de puntos, mismo sentido, distinta velocidad
            listOf(inner to 0, middle to 1, outer to 2).forEach { (deg, k) ->
                val ringR = moonR * (2.1f + 0.75f * k)
                val count = 10 + 5 * k
                repeat(count) { i ->
                    val a = Math.toRadians(deg + 360.0 / count * i)
                    drawCircle(
                        Color.White.copy(alpha = (0.6f - 0.13f * k) * fade),
                        radius = moonR * 0.075f,
                        center = c + Offset((cos(a) * ringR).toFloat(), (sin(a) * ringR).toFloat()),
                    )
                }
            }
        }
    }

    // Sol (la flor de EnHora)
    if (ts > -0.2f && ts < 1.2f) {
        val c = Offset(sx, sy)
        val high = smooth(sunElev.coerceIn(0f, 0.6f) / 0.6f)
        val sunColor = lerp(Color(0xFFFF7A45), Color(0xFFFFE27A), high)
        val len = u * (0.19f + 0.05f * (1f - high))
        drawCircle(
            Brush.radialGradient(listOf(sunColor.copy(alpha = 0.6f), Color.Transparent), c, len * 2.1f),
            radius = len * 2.1f, center = c,
        )
        val petalColor = lerp(Color.White, sunColor, 0.35f)
        petalRing(c, 24, len, len * 0.17f, petalColor.copy(alpha = 0.30f), outer)
        petalRing(c, 18, len * 0.82f, len * 0.18f, petalColor.copy(alpha = 0.52f), middle + 10f)
        petalRing(c, 14, len * 0.62f, len * 0.17f, petalColor.copy(alpha = 0.92f), inner)
        drawCircle(lerp(Color.White, sunColor, 0.25f), radius = len * 0.2f, center = c)
        val dotRing = len * 0.12f
        repeat(10) { i ->
            val a = Math.toRadians(inner + 36.0 * i)
            drawCircle(
                sunColor.copy(alpha = 0.75f), radius = len * 0.016f,
                center = c + Offset((cos(a) * dotRing).toFloat(), (sin(a) * dotRing).toFloat()),
            )
        }
    }

    // Colinas del horizonte, en tonos del propio fondo: el sol y la luna salen de detrás
    fun hills(baseY: Float, amp: Float, freq: Float, phase: Float, color: Color) {
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseY)
            var x = 0f
            while (x <= w) {
                lineTo(x, baseY + sin((x / w) * 2f * PI.toFloat() * freq + phase) * amp)
                x += w / 60f
            }
            lineTo(w, h)
            close()
        }
        drawPath(path, color)
    }
    hills(horizonY + 0.02f * h, 0.035f * h, 1.3f, 0.6f, lerp(g.bottom, Color.Black, 0.30f).copy(alpha = 0.92f))
    hills(horizonY + 0.10f * h, 0.045f * h, 2.2f, 2.0f, lerp(g.bottom, Color.Black, 0.50f).copy(alpha = 0.96f))

    // Bordes difuminados: nada marca dónde empieza o acaba la escena
    if (fadeEdges) {
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.20f to Color.Black, 0.80f to Color.Black, 1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * Fondo del widget: la misma escena sobre el degradado del tema, con las esquinas redondeadas y un
 * velo suave a la izquierda para que se lea el número de pendientes.
 */
fun renderSkyBitmap(widthPx: Int, heightPx: Int, density: Float, hour: Float, g: Gradient, cornerDp: Float): Bitmap {
    val image = ImageBitmap(widthPx, heightPx)
    val canvas = ComposeCanvas(image)
    val millis = System.currentTimeMillis()
    val inner = (millis % INNER_MS) / INNER_MS.toFloat() * 360f
    val middle = (millis % MIDDLE_MS) / MIDDLE_MS.toFloat() * 360f
    val outer = (millis % OUTER_MS) / OUTER_MS.toFloat() * 360f
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(widthPx.toFloat(), heightPx.toFloat())) {
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerDp * density)))
        }
        clipPath(clip) {
            drawSkyScene(hour, g, inner, middle, outer, fillBackground = true, fadeEdges = false)
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.30f), 0.55f to Color.Transparent,
                ),
            )
        }
    }
    return image.asAndroidBitmap()
}
