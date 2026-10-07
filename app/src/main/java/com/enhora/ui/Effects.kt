package com.enhora.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * Como `clickable`, pero el elemento se encoge con un muelle mientras se mantiene pulsado
 * y da una vibración suave al soltar. Con [onLongClick] también responde a la pulsación larga.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressClickable(
    pressedScale: Float = 0.94f,
    haptic: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val feedback = LocalHapticFeedback.current
    this.scale(scale).combinedClickable(
        interactionSource = source,
        indication = LocalIndication.current,
        role = role,
        onLongClick = onLongClick?.let { long ->
            {
                feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                long()
            }
        },
    ) {
        if (haptic) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}
