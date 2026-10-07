package com.enhora.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.graphics.Typeface
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.enhora.EnHoraApp
import com.enhora.R
import com.enhora.data.Agenda
import com.enhora.data.Entry
import com.enhora.data.Slot
import com.enhora.notification.NotificationHelper
import com.enhora.ui.formatTime
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

/**
 * Alimenta las dos listas deslizables del widget: "Próximos" (hoy primero y luego los siguientes días con
 * recordatorios) y "Hechos" (lo ya atendido hoy). La columna se elige con [EXTRA_COLUMN].
 */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getBooleanExtra(EXTRA_DONE_COLUMN, false))

    companion object {
        const val EXTRA_DONE_COLUMN = "com.enhora.widget.DONE_COLUMN"
        private const val MAX_NEXT = 6
    }

    private sealed interface Row {
        data class Pending(val entry: Entry) : Row
        data class Attended(val entry: Entry) : Row
        /** Relleno cuando no hay nada que listar; [text] puede ir vacío. */
        data class Empty(val text: String) : Row
    }

    private class Factory(private val context: Context, private val doneColumn: Boolean) : RemoteViewsFactory {
        private var rows: List<Row> = emptyList()

        override fun onCreate() = Unit
        override fun onDestroy() { rows = emptyList() }

        override fun onDataSetChanged() {
            val app = context.applicationContext as EnHoraApp
            val (tasks, completions) = runBlocking { app.repository.snapshot() }
            val done = completions.toSet()
            val today = LocalDate.now()
            val todayEntries = Agenda.entriesOn(tasks, done, today)

            if (doneColumn) {
                // Solo lo completado hoy, del último completado hacia el primero del día.
                rows = todayEntries.filter { it.done }.sortedByDescending { it.minuteOfDay }.map<Entry, Row> { Row.Attended(it) }
                    .ifEmpty { listOf(Row.Empty("Aún nada")) }
                return
            }

            // Mientras quede algo pendiente hoy: eso (lo que viene primero y después lo atrasado).
            // Si hoy ya está todo atendido: solo lo de mañana; y si mañana no hay nada, la columna queda vacía.
            val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
            val pending = todayEntries.filter { !it.resolved }
            val next = if (pending.isNotEmpty()) {
                pending.filter { it.minuteOfDay >= nowMinute } + pending.filter { it.minuteOfDay < nowMinute }
            } else {
                Agenda.entriesOn(tasks, done, today.plusDays(1)).filter { !it.resolved }
            }
            rows = next.take(MAX_NEXT).map<Entry, Row> { Row.Pending(it) }.ifEmpty { listOf(Row.Empty("")) }
        }

        override fun getCount() = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val row = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_item_done)
            return when (row) {
                is Row.Pending -> RemoteViews(context.packageName, R.layout.widget_item_pending).apply {
                    val e = row.entry
                    val time = formatTime(e.minuteOfDay)
                    setTextViewText(
                        R.id.item_text,
                        SpannableString("$time  ${e.title}").apply {
                            setSpan(StyleSpan(Typeface.BOLD), 0, time.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        },
                    )
                    setOnClickFillInIntent(R.id.item_root, openIntent(e))
                }
                is Row.Empty -> RemoteViews(context.packageName, R.layout.widget_item_empty).apply {
                    setTextViewText(R.id.item_text, row.text)
                    setOnClickFillInIntent(R.id.item_root, Intent())
                }
                is Row.Attended -> RemoteViews(context.packageName, R.layout.widget_item_done).apply {
                    val e = row.entry
                    val mark = "✓"
                    val text = "$mark ${formatTime(e.minuteOfDay)}\n${e.title}"
                    setTextViewText(
                        R.id.item_text,
                        SpannableString(text).apply { setSpan(StrikethroughSpan(), 2, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) },
                    )
                    setInt(R.id.item_dot, "setColorFilter", dotColor(e))
                    setOnClickFillInIntent(R.id.item_root, openIntent(e))
                }
            }
        }

        /** Al tocar un recordatorio la app se abre en el calendario, en la tarjeta de su día, resaltándolo. */
        private fun openIntent(e: Entry) = Intent()
            .putExtra(NotificationHelper.EXTRA_OPEN_DAY, e.day.toEpochDay())
            .putExtra(NotificationHelper.EXTRA_OPEN_TASK, e.taskId)
            .putExtra(NotificationHelper.EXTRA_OPEN_MINUTE, e.minuteOfDay)

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 3
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}

/** Punto de cada hecho: el color de su franja horaria, el mismo que usa el panel del día de la app. */
private fun dotColor(e: Entry) = when (Slot.of(e.minuteOfDay)) {
    Slot.MORNING -> 0xFFFFE9B3.toInt()
    Slot.AFTERNOON -> 0xFFD3E8FF.toInt()
    Slot.NIGHT -> 0xFFE0DBFF.toInt()
}
