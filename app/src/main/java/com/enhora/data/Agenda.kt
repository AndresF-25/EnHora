package com.enhora.data

import java.time.LocalDate
import java.time.YearMonth

/** Una ocurrencia concreta de un recordatorio en un día. */
data class Entry(
    val taskId: Long,
    val title: String,
    val day: LocalDate,
    val minuteOfDay: Int,
    val status: Int = Status.PENDING,
) {
    val done get() = status == Status.DONE
    val canceled get() = status == Status.CANCELED
    /** Atendido por el usuario: completado o cancelado. */
    val resolved get() = status != Status.PENDING
}

/** [done] cuenta los completados y [resolved] también los cancelados. */
data class DaySummary(val total: Int, val done: Int, val resolved: Int = done)

object Agenda {
    fun entriesOn(tasks: List<TaskWithReminders>, completions: Set<Completion>, day: LocalDate): List<Entry> {
        val epoch = day.toEpochDay()
        val statuses = completions.filter { it.epochDay == epoch }
            .associate { (it.taskId to it.minuteOfDay) to it.status }
        return tasks.filter { Occurrence.occursOn(it.task, day) }
            .flatMap { tw ->
                tw.reminders.map {
                    Entry(
                        tw.task.id, tw.task.title, day, it.minuteOfDay,
                        statuses[tw.task.id to it.minuteOfDay] ?: Status.PENDING,
                    )
                }
            }
            .sortedBy { it.minuteOfDay }
    }

    /** Ocurrencias sin atender de los [days] días anteriores a [today], de la más antigua a la más reciente. */
    fun overdue(
        tasks: List<TaskWithReminders>,
        completions: Set<Completion>,
        today: LocalDate,
        days: Int = 14,
    ): List<Entry> = (days downTo 1).flatMap { back ->
        entriesOn(tasks, completions, today.minusDays(back.toLong())).filter { !it.resolved }
    }

    /** Totales por día del mes; solo incluye los días con al menos una tarea. */
    fun summary(
        tasks: List<TaskWithReminders>,
        completions: Set<Completion>,
        month: YearMonth,
    ): Map<LocalDate, DaySummary> = (1..month.lengthOfMonth()).mapNotNull { d ->
        val day = month.atDay(d)
        val entries = entriesOn(tasks, completions, day)
        if (entries.isEmpty()) null
        else day to DaySummary(entries.size, entries.count { it.done }, entries.count { it.resolved })
    }.toMap()
}
