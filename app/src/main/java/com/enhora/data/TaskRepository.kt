package com.enhora.data

import com.enhora.alarm.AlarmScheduler
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val scheduler: AlarmScheduler) {

    val tasks = dao.observeAll()
    val completions = dao.observeCompletions()

    suspend fun get(id: Long) = dao.get(id)

    /** Guarda la tarea con sus horas y (re)programa todas las alarmas. */
    suspend fun save(task: Task, minutes: List<Int>): Long {
        val id: Long
        if (task.id == 0L) {
            id = dao.insertTask(task)
        } else {
            dao.get(task.id)?.reminders?.forEach { scheduler.cancel(it.id) }
            dao.updateTask(task)
            dao.deleteReminders(task.id)
            id = task.id
        }
        val reminders = minutes.distinct().sorted().map { Reminder(taskId = id, minuteOfDay = it) }
        val ids = dao.insertReminders(reminders)
        val saved = task.copy(id = id)
        reminders.zip(ids).forEach { (r, rid) -> scheduler.schedule(saved, r.copy(id = rid)) }
        return id
    }

    suspend fun delete(taskId: Long) {
        dao.get(taskId)?.reminders?.forEach { scheduler.cancel(it.id) }
        dao.deleteTask(taskId)
    }

    /** Fija el estado de un recordatorio en un día: hecho, cancelado o de nuevo pendiente. */
    suspend fun setStatus(taskId: Long, day: LocalDate, minuteOfDay: Int, status: Int) {
        if (status == Status.PENDING) dao.clearCompletion(taskId, day.toEpochDay(), minuteOfDay)
        else dao.setCompletion(Completion(taskId, day.toEpochDay(), minuteOfDay, status))
    }

    /**
     * Resuelve el aviso de [reminderId] (hecho o cancelado): guarda el estado y anula
     * cualquier aplazamiento pendiente. Devuelve false si el recordatorio ya no existe.
     */
    suspend fun resolve(reminderId: Long, epochDay: Long, status: Int): Boolean {
        val reminder = dao.getReminder(reminderId) ?: return false
        setStatus(reminder.taskId, LocalDate.ofEpochDay(epochDay), reminder.minuteOfDay, status)
        clearSnooze(reminderId)
        return true
    }

    /** Pospone el aviso [minutes] minutos. Reutiliza el mismo recordatorio: no crea ninguno nuevo. */
    suspend fun snooze(reminderId: Long, epochDay: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        dao.putSnooze(Snooze(reminderId, epochDay, at))
        scheduler.snoozeAt(reminderId, epochDay, at)
    }

    /** Olvida el aplazamiento (ya sonó, o el recordatorio se resolvió) y anula su alarma. */
    suspend fun clearSnooze(reminderId: Long) {
        dao.deleteSnooze(reminderId)
        scheduler.cancelSnooze(reminderId)
    }

    /** Verdadero si el usuario ya lo completó o canceló (no hay que volver a avisar). */
    suspend fun isResolved(taskId: Long, day: LocalDate, minuteOfDay: Int) =
        dao.isCompleted(taskId, day.toEpochDay(), minuteOfDay) > 0

    suspend fun getReminder(id: Long) = dao.getReminder(id)

    /** Todo lo necesario para exportar: tareas con sus horas y el estado de cada día. */
    suspend fun snapshot(): Pair<List<TaskWithReminders>, List<Completion>> =
        dao.getAll() to dao.getCompletions()

    /**
     * Reprograma todas las alarmas (tras reiniciar, cambiar la hora o actualizar la app).
     * Devuelve los aplazamientos que vencieron mientras el móvil estaba apagado, para avisarlos ya.
     */
    suspend fun rescheduleAll(): List<DueSnooze> {
        dao.getAll().forEach { tw -> tw.reminders.forEach { scheduler.schedule(tw.task, it) } }
        val now = System.currentTimeMillis()
        val due = mutableListOf<DueSnooze>()
        dao.getSnoozes().forEach { s ->
            if (s.atMillis > now) {
                scheduler.snoozeAt(s.reminderId, s.epochDay, s.atMillis)
            } else {
                val reminder = dao.getReminder(s.reminderId)
                val tw = reminder?.let { dao.get(it.taskId) }
                dao.deleteSnooze(s.reminderId)
                if (reminder != null && tw != null &&
                    !isResolved(reminder.taskId, LocalDate.ofEpochDay(s.epochDay), reminder.minuteOfDay)
                ) {
                    due += DueSnooze(tw.task, reminder, s.epochDay)
                }
            }
        }
        return due
    }
}

/** Aplazamiento que ya debería haber sonado. */
data class DueSnooze(val task: Task, val reminder: Reminder, val epochDay: Long)
