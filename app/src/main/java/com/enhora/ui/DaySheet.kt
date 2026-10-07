package com.enhora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.enhora.data.Entry
import com.enhora.data.Slot
import com.enhora.ui.theme.slotPalette
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    day: LocalDate,
    entries: List<Entry>,
    overdue: List<Entry>,
    onDismiss: () -> Unit,
    onToggle: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onQuickAdd: (Slot) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(formatLongDate(day), style = MaterialTheme.typography.titleLarge)
                val done = entries.count { it.done }
                val canceled = entries.count { it.canceled }
                Text(
                    when {
                        entries.isEmpty() -> "Sin recordatorios"
                        canceled > 0 -> "$done/${entries.size} completados · $canceled cancelados"
                        else -> "$done/${entries.size} completados"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (day == LocalDate.now() && overdue.isNotEmpty()) {
                Text("Atrasadas", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error)
                overdue.forEach { EntryRow(it, showDate = true, onToggle = onToggle, onEdit = onEdit) }
            }

            Slot.entries.forEach { slot ->
                SlotSection(slot, entries.filter { Slot.of(it.minuteOfDay) == slot }, onToggle, onEdit, onQuickAdd)
            }
        }
    }
}

@Composable
private fun SlotSection(
    slot: Slot,
    items: List<Entry>,
    onToggle: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onQuickAdd: (Slot) -> Unit,
) {
    val palette = slotPalette(slot)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(palette.container)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(slotIcon(slot), contentDescription = null, tint = palette.accent)
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                Text(slot.label, fontWeight = FontWeight.SemiBold, color = palette.accent)
                Text(slot.range, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onQuickAdd(slot) }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir en ${slot.label}", tint = palette.accent)
            }
        }
        if (items.isEmpty()) {
            Text("Sin pendientes", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
        } else {
            items.forEach { EntryRow(it, showDate = false, onToggle = onToggle, onEdit = onEdit) }
        }
    }
}

@Composable
fun EntryRow(
    entry: Entry,
    showDate: Boolean,
    onToggle: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onEdit(entry.taskId) }.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.done, onCheckedChange = { onToggle(entry, it) })
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (entry.resolved) TextDecoration.LineThrough else null,
                color = if (entry.resolved) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                (if (showDate) "${formatShortDate(entry.day)} · ${formatTime(entry.minuteOfDay)}"
                else formatTime(entry.minuteOfDay)) + if (entry.canceled) " · Cancelado" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueSheet(
    overdue: List<Entry>,
    onDismiss: () -> Unit,
    onToggle: (Entry, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) {
            Text("Atrasadas", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp))
            if (overdue.isEmpty()) {
                Text("No tienes nada atrasado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            overdue.forEach { EntryRow(it, showDate = true, onToggle = onToggle, onEdit = onEdit) }
        }
    }
}
