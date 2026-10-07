package com.enhora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import com.enhora.ui.Focus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhora.data.Entry
import com.enhora.ui.theme.LocalGradient
import java.time.LocalDate
import java.time.format.TextStyle

/** Alto fijo de la tarjeta: no crece con el número de recordatorios (se desplazan de lado). */
private val CardHeight = 164.dp

/** Recordatorios que se apilan como máximo bajo una misma hora antes de mostrar "+N". */
private const val MaxPerHour = 2

/**
 * Tarjeta de cristal de un día: día de la semana y número a la izquierda; a la derecha, el progreso
 * (`2/5`) y un carrusel horizontal con una columna por hora. Un toque abre el detalle del día.
 */
@Composable
fun DayCard(
    day: LocalDate,
    entries: List<Entry>,
    isSelected: Boolean,
    onToggle: (Entry, Boolean) -> Unit,
    onCancel: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier,
    focus: Focus? = null,
) {
    val d = rememberDimens()
    // La escala por ancho se limita para que el número y el mes siempre quepan en el alto fijo de la tarjeta.
    val sc = d.scale.coerceAtMost(1.1f)
    val ink = lerp(LocalGradient.current.bottom, Color.Black, 0.35f)
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, EsLocale).replaceFirstChar { it.uppercase() }
    val month = day.month.getDisplayName(TextStyle.SHORT, EsLocale).trimEnd('.').uppercase().take(3)
    val shape = RoundedCornerShape(28.dp)
    val resolved = entries.count { it.resolved }
    val byHour = entries.groupBy { it.minuteOfDay }.toSortedMap()
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Al llegar desde el widget o un aviso, la fila se desplaza hasta la hora del recordatorio.
    androidx.compose.runtime.LaunchedEffect(focus) {
        val i = byHour.keys.indexOf(focus?.minute)
        if (i >= 0) rowState.animateScrollToItem(i)
    }

    Row(
        modifier.fillMaxWidth().height(CardHeight * d.fs)
            .clip(shape)
            .background(Color.White.copy(alpha = if (isSelected) 0.26f else 0.14f))
            .then(if (isSelected) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), shape) else Modifier)
            // Un toque en la tarjeta abre el detalle del día, con los recordatorios repartidos en mañana, tarde y noche.
            .pressClickable(pressedScale = 0.985f, haptic = false, role = null, onClick = onOpenDay)
            .padding(start = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.widthIn(min = (96 * sc).dp)) {
            Text(weekday, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text(
                day.dayOfMonth.toString(),
                fontSize = (56 * sc).sp, lineHeight = (58 * sc).sp,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Text(
                month,
                fontSize = (34 * sc).sp, lineHeight = (36 * sc).sp,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
            )
        }
        Column(Modifier.weight(1f)) {
            // Cabecera: progreso del día; toca para ver todo el día.
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp).height(24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (entries.isEmpty()) "Día libre" else "$resolved/${entries.size}",
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onOpenDay).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Sin clave: una hora nueva antes de las demás aparece al inicio en vez de quedar oculta a la izquierda.
                items(byHour.entries.toList()) { (minute, list) ->
                    HourColumn(minute, list, ink, onToggle, onCancel, onEdit, onOpenDay, focus)
                }
                item(key = "add") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier.padding(top = 6.dp).size(34.dp)
                                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .clip(CircleShape)
                                .pressClickable(pressedScale = 0.85f, onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir el $day", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Columna de una hora: etiqueta y hasta [MaxPerHour] pastillas; el resto se resume en "+N". */
@Composable
private fun HourColumn(
    minute: Int,
    list: List<Entry>,
    ink: Color,
    onToggle: (Entry, Boolean) -> Unit,
    onCancel: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onOpenDay: () -> Unit,
    focus: Focus?,
) {
    // El recordatorio resaltado va primero para que nunca quede oculto tras "+N más".
    val ordered = if (focus == null) list else list.sortedByDescending { it.taskId == focus.taskId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(formatTime(minute), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        ordered.take(MaxPerHour).forEach { EntryChip(it, ink, onToggle, onCancel, onEdit, highlighted = focus != null && it.taskId == focus.taskId && it.minuteOfDay == focus.minute) }
        if (list.size > MaxPerHour) {
            Text(
                "+${list.size - MaxPerHour} más",
                modifier = Modifier.clip(CircleShape).clickable(onClick = onOpenDay).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

/** Pastilla de un recordatorio: círculo de estado + título. Toque = editar; pulsación larga = menú. */
@Composable
private fun EntryChip(
    entry: Entry,
    ink: Color,
    onToggle: (Entry, Boolean) -> Unit,
    onCancel: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    highlighted: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    // Resalte: un anillo blanco que late unos segundos.
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "focus").animateFloat(
        0.35f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "focusPulse",
    )
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = if (entry.resolved) 0.5f else 0.94f))
                // El anillo va sobre el fondo y en el tono oscuro del tema para que se distinga de la pastilla blanca.
                .then(if (highlighted) Modifier.border(3.dp, ink.copy(alpha = pulse), RoundedCornerShape(14.dp)) else Modifier)
                .pressClickable(pressedScale = 0.96f, haptic = false, role = null, onLongClick = { menu = true }) { onEdit(entry.taskId) }
                .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (entry.resolved) ink else ink.copy(alpha = 0.16f))
                    .clickable {
                        // Pendiente → hecho; hecho → pendiente; cancelado → pendiente.
                        if (entry.canceled) onCancel(entry, false) else onToggle(entry, !entry.done)
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    entry.done -> Icon(Icons.Default.Check, contentDescription = "Completado", tint = Color.White, modifier = Modifier.size(14.dp))
                    entry.canceled -> Icon(Icons.Default.Close, contentDescription = "Cancelado", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                entry.title,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = (112 * rememberDimens().scale).dp),
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (entry.resolved) TextDecoration.LineThrough else null,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = ink) {
            DropdownMenuItem(
                text = { Text(if (entry.done) "Marcar pendiente" else "Completar", color = Color.White) },
                onClick = { menu = false; onToggle(entry, !entry.done) },
            )
            DropdownMenuItem(
                text = { Text(if (entry.canceled) "Restaurar" else "Cancelar", color = Color.White) },
                onClick = { menu = false; onCancel(entry, !entry.canceled) },
            )
            DropdownMenuItem(text = { Text("Editar", color = Color.White) }, onClick = { menu = false; onEdit(entry.taskId) })
        }
    }
}
