package com.enhora.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.enhora.MainActivity
import com.enhora.alarm.ActionReceiver
import com.enhora.alarm.AlarmScheduler
import com.enhora.data.AlertMode
import com.enhora.data.Reminder
import com.enhora.data.Task

/**
 * Aviso emergente del sistema (heads-up): asoma arriba sin ocupar la pantalla.
 * Paso 1: Completar / Posponer / Cancelar. Paso 2 (tras Posponer): 5 / 15 / 30 min.
 * Nada se marca solo: el recordatorio cambia de estado únicamente cuando el usuario elige.
 * Un aviso ignorado desaparece solo a los [TIMEOUT_MS] (queda pendiente en su día).
 */
object NotificationHelper {
    private const val CHANNEL_SOUND = "avisos_sonido"
    private const val CHANNEL_VIBRATE = "avisos_vibracion"
    private const val CHANNEL_SILENT = "avisos_silencioso"
    private val LegacyChannels = listOf("recordatorios", "recordatorios_v2")

    private const val GROUP = "com.enhora.RECORDATORIOS"
    private const val SUMMARY_ID = Int.MIN_VALUE

    /** Tiempo que el aviso del sistema espera antes de retirarse solo. */
    const val TIMEOUT_MS = 30 * 60_000L

    const val ACTION_DONE = "com.enhora.DONE"
    const val ACTION_CANCEL = "com.enhora.CANCEL"
    const val ACTION_SNOOZE_MENU = "com.enhora.SNOOZE_MENU"
    const val ACTION_SNOOZE_5 = "com.enhora.SNOOZE_5"
    const val ACTION_SNOOZE_15 = "com.enhora.SNOOZE_15"
    const val ACTION_SNOOZE_30 = "com.enhora.SNOOZE_30"

    /** Minutos de aplazamiento de cada acción de posponer. */
    val SnoozeMinutes = mapOf(ACTION_SNOOZE_5 to 5, ACTION_SNOOZE_15 to 15, ACTION_SNOOZE_30 to 30)

    /** Extra de la actividad: día (epochDay) al que abrir el calendario. */
    const val EXTRA_OPEN_DAY = "openDay"
    const val EXTRA_OPEN_TASK = "openTask"
    const val EXTRA_OPEN_MINUTE = "openMinute"

    fun notificationId(reminderId: Long) = reminderId.toInt()

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        LegacyChannels.forEach { manager.deleteNotificationChannel(it) }
        fun channel(id: String, name: String, vibrate: Boolean, sound: Boolean) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos a la hora de cada recordatorio"
                enableVibration(vibrate)
                if (!sound) setSound(null, null)
            }
        manager.createNotificationChannel(channel(CHANNEL_SOUND, "Recordatorios (sonido y vibración)", vibrate = true, sound = true))
        manager.createNotificationChannel(channel(CHANNEL_VIBRATE, "Recordatorios (solo vibración)", vibrate = true, sound = false))
        manager.createNotificationChannel(channel(CHANNEL_SILENT, "Recordatorios (silencioso)", vibrate = false, sound = false))
    }

    private fun channelFor(mode: Int) = when (mode) {
        AlertMode.VIBRATE -> CHANNEL_VIBRATE
        AlertMode.SILENT -> CHANNEL_SILENT
        else -> CHANNEL_SOUND
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    fun show(context: Context, task: Task, reminder: Reminder, epochDay: Long, snoozeMenu: Boolean = false) {
        if (!canNotify(context)) return

        val open = PendingIntent.getActivity(
            context, notificationId(reminder.id),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_DAY, epochDay)
                .putExtra(EXTRA_OPEN_TASK, task.id).putExtra(EXTRA_OPEN_MINUTE, reminder.minuteOfDay),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channelFor(task.alertMode))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(task.title)
            .setContentText(
                if (snoozeMenu) "¿Cuánto tiempo quieres posponerlo?" else task.notes.ifBlank { "Es hora" },
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            // Se queda hasta que el usuario decida (o hasta que caduque); tocarla solo abre la app.
            .setAutoCancel(false)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(open)

        if (snoozeMenu) {
            builder.addAction(0, "5 min", action(context, ACTION_SNOOZE_5, reminder.id, epochDay))
                .addAction(0, "15 min", action(context, ACTION_SNOOZE_15, reminder.id, epochDay))
                .addAction(0, "30 min", action(context, ACTION_SNOOZE_30, reminder.id, epochDay))
        } else {
            builder.addAction(0, "Completar", action(context, ACTION_DONE, reminder.id, epochDay))
                .addAction(0, "Posponer", action(context, ACTION_SNOOZE_MENU, reminder.id, epochDay))
                .addAction(0, "Cancelar", action(context, ACTION_CANCEL, reminder.id, epochDay))
        }
        // Mismo id: el segundo paso reemplaza al primero; no se crea otro aviso.
        NotificationManagerCompat.from(context).notify(notificationId(reminder.id), builder.build())
        updateSummary(context, extraId = notificationId(reminder.id))
    }

    /** Retira el aviso de [reminderId] (y el resumen del grupo si ya no hace falta). */
    fun dismiss(context: Context, reminderId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId(reminderId))
        updateSummary(context, excludeId = notificationId(reminderId))
    }

    /** Con 2 o más avisos a la vez se agrupan bajo un solo resumen. */
    private fun updateSummary(context: Context, extraId: Int? = null, excludeId: Int? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ids = manager.activeNotifications
            .filter { it.id != SUMMARY_ID && it.notification.group == GROUP }
            .map { it.id }
            .toMutableSet()
        extraId?.let { ids += it }
        excludeId?.let { ids -= it }
        if (ids.size < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }
        if (!canNotify(context)) return
        val summary = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${ids.size} recordatorios")
            .setContentText("Toca para abrir EnHora")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
    }

    private val ActionOrder = listOf(
        ACTION_DONE, ACTION_CANCEL, ACTION_SNOOZE_MENU, ACTION_SNOOZE_5, ACTION_SNOOZE_15, ACTION_SNOOZE_30,
    )

    private fun action(context: Context, action: String, reminderId: Long, epochDay: Long): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_EPOCH_DAY, epochDay)
        }
        val code = (reminderId * ActionOrder.size + ActionOrder.indexOf(action)).toInt()
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
