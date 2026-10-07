package com.enhora.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Medidas de la pantalla del móvil para que cada elemento se ajuste a su proporción:
 * móviles estrechos o anchos, texto grande del sistema y orientación horizontal.
 */
@Immutable
class Dimens(val widthDp: Int, val heightDp: Int, val fontScale: Float) {
    /** Proporción respecto a un móvil de 360 dp de ancho (acotada para no deformar el diseño). */
    val scale: Float get() = (widthDp / 360f).coerceIn(0.85f, 1.25f)

    /** Tamaño de letra del sistema, acotado: sirve para dar más alto a lo que contiene texto. */
    val fs: Float get() = fontScale.coerceIn(1f, 1.35f)

    /**
     * Pantalla donde no caben a la vez el calendario fijo y las tarjetas (móvil pequeño, texto grande
     * u horizontal): el contenido pasa a desplazarse entero. El umbral crece con el tamaño de letra.
     */
    val compactHeight: Boolean get() = heightDp < 680 * fs
}

@Composable
fun rememberDimens(): Dimens {
    val c = LocalConfiguration.current
    return remember(c.screenWidthDp, c.screenHeightDp, c.fontScale) {
        Dimens(c.screenWidthDp, c.screenHeightDp, c.fontScale)
    }
}
