package com.enhora.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enhora.notification.ReminderAlert
import com.enhora.notification.ReminderAlerts
import com.enhora.ui.theme.LocalGradient

/**
 * Aviso propio de la app, con el mismo contenido y acciones que la notificación del sistema:
 * Completar, Posponer (5 / 15 / 30 min) y Cancelar. Asoma arriba sin tapar la pantalla;
 * si se ignora 30 s (o se desliza hacia arriba) pasa a la notificación del sistema.
 */
@Composable
fun ReminderBanner(
    alerts: List<ReminderAlert>,
    onDone: (ReminderAlert) -> Unit,
    onCancel: (ReminderAlert) -> Unit,
    onSnooze: (ReminderAlert, Int) -> Unit,
    onIgnore: (ReminderAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = alerts.firstOrNull()
    val extra = alerts.size - 1
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // AnimatedContent conserva el aviso saliente mientras se anima su salida.
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "banner",
        ) { alert ->
            if (alert != null) BannerCard(alert, extra, onDone, onCancel, onSnooze, onIgnore)
        }
    }
}

@Composable
private fun BannerCard(
    alert: ReminderAlert,
    extra: Int,
    onDone: (ReminderAlert) -> Unit,
    onCancel: (ReminderAlert) -> Unit,
    onSnooze: (ReminderAlert, Int) -> Unit,
    onIgnore: (ReminderAlert) -> Unit,
) {
    val g = LocalGradient.current
    val ink = lerp(g.bottom, Color.Black, 0.35f)
    val shape = RoundedCornerShape(28.dp)
    var snoozeMenu by remember(alert.key) { mutableStateOf(false) }
    var drag by remember(alert.key) { mutableFloatStateOf(0f) }

    // Cuenta atrás visible: la barra se vacía mientras el aviso espera.
    var started by remember(alert.key) { mutableStateOf(false) }
    val remaining by animateFloatAsState(
        targetValue = if (started) 0f else 1f,
        animationSpec = tween(ReminderAlerts.IN_APP_TIMEOUT_MS.toInt(), easing = LinearEasing),
        label = "bannerTimeout",
    )
    LaunchedEffect(alert.key) {
        started = true
        kotlinx.coroutines.delay(ReminderAlerts.IN_APP_TIMEOUT_MS)
        onIgnore(alert)
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(g.mid, Color.Black, 0.25f), lerp(g.bottom, Color.Black, 0.2f))))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .pointerInput(alert.key) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag < -40f) onIgnore(alert); drag = 0f },
                    onDragCancel = { drag = 0f },
                    onVerticalDrag = { change, amount -> change.consume(); drag += amount },
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    alert.notes.ifBlank { "Es hora" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTime(alert.minuteOfDay), style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (extra > 0) {
                    Text("+$extra más", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }

        AnimatedContent(
            targetState = snoozeMenu,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "bannerActions",
        ) { menu ->
            if (!menu) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannerButton("Completar", Modifier.weight(1f), filled = true, ink = ink) { onDone(alert) }
                    BannerButton("Posponer", Modifier.weight(1f), ink = ink) { snoozeMenu = true }
                    BannerButton("Cancelar", Modifier.weight(1f), ink = ink) { onCancel(alert) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { minutes ->
                        BannerButton("$minutes min", Modifier.weight(1f), filled = true, ink = ink) { onSnooze(alert, minutes) }
                    }
                    BannerButton("Atrás", Modifier.weight(1f), ink = ink) { snoozeMenu = false }
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(Modifier.fillMaxWidth(remaining).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
        }
    }
}

@Composable
private fun BannerButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    ink: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(44.dp).clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.16f))
            .pressClickable(pressedScale = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) ink else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
