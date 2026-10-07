package com.enhora.data

import java.time.LocalDate
import java.time.LocalDateTime

enum class Slot(val label: String, val range: String, val defaultMinute: Int) {
    MORNING("Mañana", "5:00 AM – 11:59 AM", 8 * 60),
    AFTERNOON("Tarde", "12:00 PM – 6:59 PM", 14 * 60),
    NIGHT("Noche", "7:00 PM – 4:59 AM", 20 * 60);

    companion object {
        fun of(minuteOfDay: Int): Slot = when (minuteOfDay / 60) {
            in 5..11 -> MORNING
            in 12..18 -> AFTERNOON
            else -> NIGHT
        }
    }
}

object Occurrence {
    fun occursOn(task: Task, day: LocalDate): Boolean {
        val start = LocalDate.ofEpochDay(task.epochDay)
        return when (task.repeat) {
            Repeat.DAILY -> !day.isBefore(start)
            Repeat.WEEKLY, Repeat.CUSTOM -> !day.isBefore(start) &&
                (task.weekDays shr (day.dayOfWeek.value - 1)) and 1 == 1
            else -> day == start
        }
    }

    /** Próximo disparo estrictamente posterior a [from], o null si no hay más. */
    fun next(task: Task, minuteOfDay: Int, from: LocalDateTime): LocalDateTime? {
        val start = LocalDate.ofEpochDay(task.epochDay)
        var day = maxOf(from.toLocalDate(), start)
        val limit = if (task.repeat == Repeat.NONE) 1 else 8
        repeat(limit) {
            if (occursOn(task, day)) {
                val dt = day.atTime(minuteOfDay / 60, minuteOfDay % 60)
                if (dt.isAfter(from)) return dt
            }
            day = day.plusDays(1)
        }
        return null
    }
}
