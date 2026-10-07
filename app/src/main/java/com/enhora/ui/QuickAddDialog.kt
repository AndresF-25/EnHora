package com.enhora.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.enhora.data.Slot
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Diálogo corto para crear un recordatorio de una sola vez dentro de una franja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddDialog(
    slot: Slot,
    day: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, minute: Int) -> Unit,
    onMore: (title: String, minute: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val titleFocus = remember { FocusRequester() }
    // TimeInput pide el foco al componerse; se lo devolvemos al título para poder escribir de inmediato.
    LaunchedEffect(Unit) { delay(200); titleFocus.requestFocus() }
    val time = rememberTimePickerState(
        initialHour = slot.defaultMinute / 60,
        initialMinute = slot.defaultMinute % 60,
        is24Hour = false,
    )
    val minute = time.hour * 60 + time.minute
    val inSlot = Slot.of(minute) == slot

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slot.label} · ${formatShortDate(day)}") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                )
                TimeInput(time, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onMore(title.trim(), minute) }) { Text("Más opciones") }
                if (!inSlot) {
                    Text(
                        "La hora debe estar entre ${slot.range}.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && inSlot,
                onClick = { onSave(title.trim(), minute) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
