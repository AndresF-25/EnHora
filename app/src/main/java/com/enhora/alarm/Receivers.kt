package com.enhora.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.enhora.EnHoraApp
import com.enhora.alarm.AlarmScheduler.Companion.EXTRA_EPOCH_DAY
import com.enhora.alarm.AlarmScheduler.Companion.EXTRA_REMINDER_ID
import com.enhora.alarm.AlarmScheduler.Companion.EXTRA_SNOOZE
import com.enhora.data.Status
import com.enhora.notification.NotificationHelper
import com.enhora.notification.ReminderAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val result = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } finally { result.finish() }
    }
}

/** Se dispara a la hora del recordatorio: avisa (ventana de la app o notificación) y reprograma la siguiente. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, 0)
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val app = context.applicationContext as EnHoraApp
        async {
            val reminder = app.repository.getReminder(reminderId) ?: return@async
            val tw = app.repository.get(reminder.taskId) ?: return@async
            val day = LocalDate.ofEpochDay(epochDay)
            // El aplazamiento ya sonó: se olvida (la fila guardada solo servía para sobrevivir a un reinicio).
            if (snooze) app.repository.clearSnooze(reminderId)
            if (!app.repository.isResolved(tw.task.id, day, reminder.minuteOfDay)) {
                ReminderAlerts.deliver(context, tw.task, reminder, epochDay)
            }
            if (!snooze) app.scheduler.schedule(tw.task, reminder)
        }
    }
}

/** Botones de la notificación: Completar, Cancelar y Posponer (5 / 15 / 30 min). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, 0)
        val action = intent.action
        val app = context.applicationContext as EnHoraApp
        async {
            val reminder = app.repository.getReminder(reminderId)
            val tw = reminder?.let { app.repository.get(it.taskId) }
            if (reminder == null || tw == null) {
                NotificationHelper.dismiss(context, reminderId)
                return@async
            }
            when (action) {
                NotificationHelper.ACTION_DONE -> {
                    app.repository.resolve(reminderId, epochDay, Status.DONE)
                    NotificationHelper.dismiss(context, reminderId)
                }
                NotificationHelper.ACTION_CANCEL -> {
                    app.repository.resolve(reminderId, epochDay, Status.CANCELED)
                    NotificationHelper.dismiss(context, reminderId)
                }
                NotificationHelper.ACTION_SNOOZE_MENU ->
                    NotificationHelper.show(context, tw.task, reminder, epochDay, snoozeMenu = true)
                else -> NotificationHelper.SnoozeMinutes[action]?.let { minutes ->
                    // Reutiliza el mismo recordatorio: solo se programa otro disparo, no se crea nada nuevo.
                    app.repository.snooze(reminderId, epochDay, minutes)
                    NotificationHelper.dismiss(context, reminderId)
                }
            }
        }
    }
}

/**
 * Reprograma todas las alarmas tras reiniciar el móvil, actualizar la app o cambiar la hora,
 * la zona horaria o el permiso de alarmas exactas. Los aplazamientos guardados se recuperan,
 * y los que vencieron con el móvil apagado se avisan al encender.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EnHoraApp
        async {
            val due = app.repository.rescheduleAll()
            due.forEach { NotificationHelper.show(context, it.task, it.reminder, it.epochDay) }
        }
    }
}
