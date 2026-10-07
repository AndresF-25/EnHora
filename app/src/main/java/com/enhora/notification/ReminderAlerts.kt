package com.enhora.notification

import android.content.Context
import com.enhora.data.Reminder
import com.enhora.data.Task
import kotlinx.coroutines.flow.MutableSharedFlow

/** Datos de un aviso, lo justo para mostrarlo y resolverlo (la ventana propia de la app usa esto). */
data class ReminderAlert(
    val reminderId: Long,
    val epochDay: Long,
    val title: String,
    val notes: String,
    val minuteOfDay: Int,
    val alertMode: Int,
) {
    val key get() = reminderId to epochDay
}

/**
 * Reparte los avisos: con la app a la vista se muestra la ventana propia (estilo de la app) y, si la app
 * no está en uso, ese mismo aviso sobre las demás apps. La notificación del sistema queda como respaldo
 * (sin permiso para mostrar sobre otras apps, móvil bloqueado o aviso ignorado).
 */
object ReminderAlerts {
    /** Verdadero mientras la actividad principal está en primer plano. */
    @Volatile var appVisible = false

    /** Cuánto tarda la ventana propia en dar el aviso por ignorado (pasa a la notificación del sistema). */
    const val IN_APP_TIMEOUT_MS = 30_000L

    val events = MutableSharedFlow<ReminderAlert>(extraBufferCapacity = 16)

    fun alertFor(task: Task, reminder: Reminder, epochDay: Long) =
        ReminderAlert(reminder.id, epochDay, task.title, task.notes, reminder.minuteOfDay, task.alertMode)

    fun deliver(context: Context, task: Task, reminder: Reminder, epochDay: Long) {
        val alert = alertFor(task, reminder, epochDay)
        val shownInApp = appVisible && events.subscriptionCount.value > 0 && events.tryEmit(alert)
        when {
            shownInApp -> Unit
            !appVisible && AlertOverlay.canShow(context) -> AlertOverlay.show(context, alert)
            else -> NotificationHelper.show(context, task, reminder, epochDay)
        }
    }
}
