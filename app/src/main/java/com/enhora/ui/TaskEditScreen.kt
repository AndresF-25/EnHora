package com.enhora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enhora.data.AlertMode
import com.enhora.data.Repeat
import com.enhora.data.Task
import com.enhora.ui.theme.GradientScreen
import com.enhora.ui.theme.LocalGradient
import com.enhora.ui.theme.OnGradientTheme
import com.enhora.ui.theme.gradientBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Botón de cristal a todo el ancho (fecha, hora). */
@Composable
private fun GlassButton(text: String, modifier: Modifier = Modifier, withPlus: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.clip(FieldShape).background(Color.White.copy(alpha = 0.14f))
            .pressClickable(pressedScale = 0.97f, onClick = onClick)
            .height(56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (withPlus) Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (withPlus) 8.dp else 0.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    vm: MainViewModel,
    taskId: Long?,
    initialDay: LocalDate,
    initialMinute: Int?,
    initialTitle: String,
    onClose: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(initialDay) }
    var repeat by remember { mutableIntStateOf(Repeat.NONE) }
    var weekDays by remember { mutableIntStateOf(0) }
    var alertMode by remember { mutableIntStateOf(AlertMode.SOUND) }
    // Sin hora por defecto: la elige el usuario (solo viene puesta si se tocó una hora concreta).
    var minutes by remember { mutableStateOf(listOfNotNull(initialMinute)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            vm.load(taskId)?.let { tw ->
                title = tw.task.title; notes = tw.task.notes
                day = LocalDate.ofEpochDay(tw.task.epochDay)
                weekDays = tw.task.weekDays
                alertMode = tw.task.alertMode
                // Un "semanal" con días distintos al de inicio es, en realidad, personalizado.
                repeat = if (tw.task.repeat == Repeat.WEEKLY && tw.task.weekDays != (1 shl (day.dayOfWeek.value - 1))) {
                    Repeat.CUSTOM
                } else tw.task.repeat
                minutes = tw.reminders.map { it.minuteOfDay }.sorted()
            }
        }
    }

    GradientScreen {
        Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(if (taskId == null) "Nuevo recordatorio" else "Editar recordatorio") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        }) { padding ->
            Column(
                Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextField(
                    title, { title = it }, label = { Text("Título") }, singleLine = true,
                    shape = FieldShape, colors = glassFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    notes, { notes = it }, label = { Text("Notas") },
                    shape = FieldShape, colors = glassFieldColors(), modifier = Modifier.fillMaxWidth(),
                )

                GlassButton(
                    "${if (repeat == Repeat.NONE) "Fecha" else "Inicia"}: ${day.dayOfMonth}/${day.monthValue}/${day.year}",
                    modifier = Modifier.fillMaxWidth(),
                ) { showDate = true }

                Text("Hora del aviso", style = MaterialTheme.typography.titleSmall)
                if (minutes.isEmpty()) {
                    Text(
                        "Elige a qué hora quieres que suene.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ink = inkColor()
                    minutes.forEach { m ->
                        Row(
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.94f))
                                .padding(start = 16.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(formatTime(m), color = ink, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { minutes = minutes - m }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar ${formatTime(m)}", tint = ink, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                GlassButton(
                    if (minutes.isEmpty()) "Establecer hora" else "Añadir otra hora",
                    modifier = Modifier.fillMaxWidth(), withPlus = true,
                ) { showTime = true }

                Text("Repetir", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Repeat.NONE to "Una vez", Repeat.DAILY to "Diario",
                        Repeat.WEEKLY to "Semanal", Repeat.CUSTOM to "Personalizado",
                    ).forEach { (r, label) ->
                        FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(label) })
                    }
                }
                if (repeat == Repeat.CUSTOM) {
                    Text(
                        "Suena los días que marques, desde la fecha de inicio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val ink = inkColor()
                        DayOfWeek.entries.forEach { d ->
                            val bit = 1 shl (d.value - 1)
                            val on = weekDays and bit != 0
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                    .background(if (on) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.14f))
                                    .pressClickable(pressedScale = 0.88f, role = Role.Checkbox) { weekDays = weekDays xor bit },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.getDisplayName(TextStyle.NARROW, Locale("es")).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (on) ink else Color.White,
                                )
                            }
                        }
                    }
                }

                Text("Aviso", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AlertMode.SOUND to "Sonido y vibración", AlertMode.VIBRATE to "Solo vibración",
                        AlertMode.SILENT to "Silencioso",
                    ).forEach { (m, label) ->
                        FilterChip(selected = alertMode == m, onClick = { alertMode = m }, label = { Text(label) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = title.isNotBlank() && minutes.isNotEmpty() &&
                            (repeat != Repeat.CUSTOM || weekDays != 0),
                        onClick = {
                            vm.save(
                                Task(
                                    id = taskId ?: 0,
                                    title = title.trim(),
                                    notes = notes.trim(),
                                    epochDay = day.toEpochDay(),
                                    repeat = repeat,
                                    alertMode = alertMode,
                                    weekDays = when (repeat) {
                                        Repeat.WEEKLY -> 1 shl (day.dayOfWeek.value - 1)
                                        Repeat.CUSTOM -> weekDays
                                        else -> 0
                                    },
                                ),
                                minutes,
                                onClose,
                            )
                        },
                    ) { Text("Guardar") }
                    if (taskId != null) {
                        TextButton(onClick = { vm.delete(taskId, onClose) }) { Text("Eliminar") }
                    }
                }
            }
        }
    }

    if (showDate) {
        var pickerMonth by remember { mutableStateOf(YearMonth.from(day)) }
        Dialog(onDismissRequest = { showDate = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // Mismo calendario de cristal que la pantalla principal, sobre el degradado del tema.
            Box(
                Modifier.padding(16.dp).clip(RoundedCornerShape(32.dp))
                    .gradientBackground(LocalGradient.current).padding(12.dp),
            ) {
                OnGradientTheme {
                    CalendarCard(
                        month = pickerMonth,
                        selected = day,
                        summary = emptyMap(),
                        onMonth = { pickerMonth = it },
                        onSelect = { day = it; showDate = false },
                        onToday = { pickerMonth = YearMonth.now() },
                    )
                }
            }
        }
    }

    if (showTime) {
        val now = LocalTime.now()
        val state = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = false)
        val ink = inkColor()
        Dialog(onDismissRequest = { showTime = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            // Mismo cristal sobre el degradado del tema que el selector de fecha.
            Box(
                Modifier.padding(16.dp).clip(RoundedCornerShape(32.dp))
                    .gradientBackground(LocalGradient.current).padding(20.dp),
            ) {
                OnGradientTheme {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimePicker(
                            state = state,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = Color.White.copy(alpha = 0.16f),
                                selectorColor = Color.White,
                                containerColor = Color.Transparent,
                                clockDialSelectedContentColor = ink,
                                clockDialUnselectedContentColor = Color.White,
                                periodSelectorBorderColor = Color.White.copy(alpha = 0.4f),
                                periodSelectorSelectedContainerColor = Color.White,
                                periodSelectorUnselectedContainerColor = Color.Transparent,
                                periodSelectorSelectedContentColor = ink,
                                periodSelectorUnselectedContentColor = Color.White.copy(alpha = 0.8f),
                                timeSelectorSelectedContainerColor = Color.White.copy(alpha = 0.3f),
                                timeSelectorUnselectedContainerColor = Color.White.copy(alpha = 0.14f),
                                timeSelectorSelectedContentColor = Color.White,
                                timeSelectorUnselectedContentColor = Color.White,
                            ),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { showTime = false }) { Text("Cancelar") }
                            Button(onClick = {
                                val m = state.hour * 60 + state.minute
                                if (m !in minutes) minutes = (minutes + m).sorted()
                                showTime = false
                            }) { Text("Añadir") }
                        }
                    }
                }
            }
        }
    }
}
