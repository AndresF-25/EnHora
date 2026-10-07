package com.enhora.ui

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enhora.data.Agenda
import com.enhora.data.CsvExport
import com.enhora.data.DaySummary
import com.enhora.data.Entry
import com.enhora.data.Status
import com.enhora.data.Task
import com.enhora.data.TaskRepository
import com.enhora.notification.NotificationHelper
import com.enhora.notification.ReminderAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Recordatorios de un día, para las tarjetas del calendario. */
data class DayEntries(val day: LocalDate, val entries: List<Entry>)

/** Un recordatorio concreto (tarea, día y hora) al que se quiere llevar la atención. */
data class Focus(val taskId: Long, val day: LocalDate, val minute: Int)

class MainViewModel(private val repo: TaskRepository) : ViewModel() {

    private val _selectedDay = MutableStateFlow(LocalDate.now())
    val selectedDay: StateFlow<LocalDate> = _selectedDay

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth

    private val completionSet = repo.completions.map { it.toSet() }

    val monthSummary: StateFlow<Map<LocalDate, DaySummary>> =
        combine(repo.tasks, completionSet, _visibleMonth) { tasks, done, month ->
            Agenda.summary(tasks, done, month)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val dayEntries: StateFlow<List<Entry>> =
        combine(repo.tasks, completionSet, _selectedDay) { tasks, done, day ->
            Agenda.entriesOn(tasks, done, day)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Las tarjetas del calendario: todos los días del mes visible que tienen recordatorios, más el día
     * seleccionado aunque esté vacío. Al tocar un día la lista se desplaza hasta su tarjeta.
     */
    val upcomingDays: StateFlow<List<DayEntries>> =
        combine(repo.tasks, completionSet, _visibleMonth, _selectedDay) { tasks, done, month, selected ->
            (1..month.lengthOfMonth()).mapNotNull { d ->
                val day = month.atDay(d)
                val entries = Agenda.entriesOn(tasks, done, day)
                if (entries.isNotEmpty() || day == selected) DayEntries(day, entries) else null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recordatorio que se acaba de abrir desde un aviso o el widget: se resalta en su tarjeta unos segundos. */
    val focus = androidx.compose.runtime.mutableStateOf<Focus?>(null)

    fun focusReminder(taskId: Long, day: LocalDate, minute: Int) {
        selectDay(day)
        focus.value = Focus(taskId, day, minute)
    }

    /** Recordatorios de hoy, para el resumen de la pantalla de inicio. */
    val todayEntries: StateFlow<List<Entry>> =
        combine(repo.tasks, completionSet) { tasks, done ->
            Agenda.entriesOn(tasks, done, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recordatorios de mañana, para el aviso discreto de la pantalla de inicio. */
    val tomorrowEntries: StateFlow<List<Entry>> =
        combine(repo.tasks, completionSet) { tasks, done ->
            Agenda.entriesOn(tasks, done, LocalDate.now().plusDays(1))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overdue: StateFlow<List<Entry>> =
        combine(repo.tasks, completionSet) { tasks, done ->
            Agenda.overdue(tasks, done, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Avisos pendientes de atender en la ventana propia de la app (el primero es el visible). */
    val alerts = mutableStateListOf<ReminderAlert>()

    fun addAlert(alert: ReminderAlert) {
        if (alerts.none { it.key == alert.key }) alerts += alert
    }

    fun resolveAlert(alert: ReminderAlert, status: Int) {
        alerts.removeAll { it.key == alert.key }
        viewModelScope.launch { repo.resolve(alert.reminderId, alert.epochDay, status) }
    }

    fun snoozeAlert(alert: ReminderAlert, minutes: Int) {
        alerts.removeAll { it.key == alert.key }
        viewModelScope.launch { repo.snooze(alert.reminderId, alert.epochDay, minutes) }
    }

    /** Aviso ignorado en la ventana propia: pasa a la notificación del sistema para no perderse. */
    fun escalateAlert(context: Context, alert: ReminderAlert) {
        alerts.removeAll { it.key == alert.key }
        val appContext = context.applicationContext
        viewModelScope.launch {
            val reminder = repo.getReminder(alert.reminderId) ?: return@launch
            val tw = repo.get(reminder.taskId) ?: return@launch
            if (!repo.isResolved(tw.task.id, LocalDate.ofEpochDay(alert.epochDay), reminder.minuteOfDay)) {
                NotificationHelper.show(appContext, tw.task, reminder, alert.epochDay)
            }
        }
    }

    /** Si la app deja de verse, los avisos de la ventana propia pasan a notificaciones. */
    fun escalateAll(context: Context) {
        alerts.toList().forEach { escalateAlert(context, it) }
    }

    fun selectDay(day: LocalDate) {
        _selectedDay.value = day
        _visibleMonth.value = YearMonth.from(day)
    }

    fun showMonth(month: YearMonth) { _visibleMonth.value = month }

    fun goToday() = selectDay(LocalDate.now())

    fun setDone(entry: Entry, done: Boolean) {
        setStatus(entry, if (done) Status.DONE else Status.PENDING)
    }

    /** Cancela el recordatorio de ese día, o lo restaura como pendiente. */
    fun setCanceled(entry: Entry, canceled: Boolean) {
        setStatus(entry, if (canceled) Status.CANCELED else Status.PENDING)
    }

    /** Devuelve el recordatorio a un estado anterior (Deshacer). */
    fun restore(entry: Entry, status: Int) = setStatus(entry, status)

    /** Escribe todos los recordatorios en [uri] como CSV (UTF-8) para analizarlos fuera de la app. */
    fun exportCsv(context: Context, uri: android.net.Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val message = try {
                val (tasks, completions) = repo.snapshot()
                val csv = CsvExport.build(tasks, completions, LocalDate.now())
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                "Exportado: ${csv.lines().size - 2} filas"
            } catch (e: Exception) {
                "No se pudo exportar"
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setStatus(entry: Entry, status: Int) {
        viewModelScope.launch { repo.setStatus(entry.taskId, entry.day, entry.minuteOfDay, status) }
    }

    fun save(task: Task, minutes: List<Int>, onSaved: () -> Unit = {}) {
        viewModelScope.launch { repo.save(task, minutes); onSaved() }
    }

    fun delete(taskId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch { repo.delete(taskId); onDeleted() }
    }

    suspend fun load(id: Long) = repo.get(id)
}
