package com.enhora.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Exporta los recordatorios a CSV para analizarlos fuera de la app (Excel, Sheets, Python...).
 * Una fila por cada aparición de cada hora de cada recordatorio, desde su fecha de inicio hasta hoy
 * (los de una sola vez, también si son futuros), con su estado.
 */
object CsvExport {
    const val HEADER = "tarea_id,titulo,notas,repeticion,dias_semana,aviso,fecha,hora,estado"

    /** Tope de días hacia atrás por recordatorio, para que un recordatorio muy antiguo no genere un archivo enorme. */
    private const val MAX_DAYS = 3 * 366

    private val Dates = DateTimeFormatter.ISO_LOCAL_DATE

    private val DayLetters = mapOf(
        DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "X",
        DayOfWeek.THURSDAY to "J", DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D",
    )

    fun build(tasks: List<TaskWithReminders>, completions: List<Completion>, today: LocalDate): String {
        val statuses = completions.associate { Triple(it.taskId, it.epochDay, it.minuteOfDay) to it.status }
        val sb = StringBuilder("﻿") // BOM: Excel reconoce así los acentos (UTF-8)
        sb.append(HEADER).append("\r\n")
        tasks.sortedBy { it.task.id }.forEach { tw ->
            val t = tw.task
            val start = LocalDate.ofEpochDay(t.epochDay)
            val last = if (t.repeat == Repeat.NONE) start else today
            val first = maxOf(start, last.minusDays(MAX_DAYS.toLong()))
            var day = first
            while (!day.isAfter(last)) {
                if (Occurrence.occursOn(t, day)) {
                    tw.reminders.sortedBy { it.minuteOfDay }.forEach { r ->
                        val status = statuses[Triple(t.id, day.toEpochDay(), r.minuteOfDay)] ?: Status.PENDING
                        sb.append(
                            row(
                                t.id.toString(), t.title, t.notes, repeatName(t), weekdays(t), alertName(t),
                                day.format(Dates), "%02d:%02d".format(r.minuteOfDay / 60, r.minuteOfDay % 60),
                                statusName(status, day, today),
                            ),
                        ).append("\r\n")
                    }
                }
                day = day.plusDays(1)
            }
        }
        return sb.toString()
    }

    private fun repeatName(t: Task) = when (t.repeat) {
        Repeat.DAILY -> "Diario"
        Repeat.WEEKLY -> "Semanal"
        Repeat.CUSTOM -> "Personalizado"
        else -> "Una vez"
    }

    private fun weekdays(t: Task): String =
        if (t.repeat == Repeat.WEEKLY || t.repeat == Repeat.CUSTOM) {
            DayOfWeek.entries.filter { (t.weekDays shr (it.value - 1)) and 1 == 1 }
                .joinToString(" ") { DayLetters.getValue(it) }
        } else ""

    private fun alertName(t: Task) = when (t.alertMode) {
        AlertMode.VIBRATE -> "Vibración"
        AlertMode.SILENT -> "Silencioso"
        else -> "Sonido"
    }

    /** Un recordatorio de un día pasado sin atender se marca "Sin atender"; el de hoy o futuro, "Pendiente". */
    private fun statusName(status: Int, day: LocalDate, today: LocalDate) = when (status) {
        Status.DONE -> "Hecho"
        Status.CANCELED -> "Cancelado"
        else -> if (day.isBefore(today)) "Sin atender" else "Pendiente"
    }

    private fun row(vararg fields: String) = fields.joinToString(",") { escape(it) }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
