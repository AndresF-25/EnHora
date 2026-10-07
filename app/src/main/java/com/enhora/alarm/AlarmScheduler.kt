package com.enhora.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.enhora.data.Occurrence
import com.enhora.data.Reminder
import com.enhora.data.Task
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact() = alarmManager.canScheduleExactAlarms()

    /** Programa el próximo disparo de [reminder] (si lo hay). */
    fun schedule(task: Task, reminder: Reminder, from: LocalDateTime = LocalDateTime.now()) {
        val next = Occurrence.next(task, reminder.minuteOfDay, from) ?: return
        setAlarm(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), reminder.id, next.toLocalDate().toEpochDay(), snooze = false)
    }

    /** Programa el aviso aplazado de [reminderId] para [atMillis]. */
    fun snoozeAt(reminderId: Long, epochDay: Long, atMillis: Long) {
        setAlarm(atMillis, reminderId, epochDay, snooze = true)
    }

    /** Anula solo el aplazamiento pendiente (al completar o cancelar el recordatorio). */
    fun cancelSnooze(reminderId: Long) {
        alarmManager.cancel(pending(reminderId, 0, snooze = true))
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(pending(reminderId, 0, snooze = false))
        alarmManager.cancel(pending(reminderId, 0, snooze = true))
    }

    private fun setAlarm(atMillis: Long, reminderId: Long, epochDay: Long, snooze: Boolean) {
        val pi = pending(reminderId, epochDay, snooze)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun pending(reminderId: Long, epochDay: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_EPOCH_DAY, epochDay)
            putExtra(EXTRA_SNOOZE, snooze)
        }
        val code = (reminderId * 2 + if (snooze) 1 else 0).toInt()
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_EPOCH_DAY = "epochDay"
        const val EXTRA_SNOOZE = "snooze"
    }
}
