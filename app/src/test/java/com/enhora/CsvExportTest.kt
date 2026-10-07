package com.enhora

import com.enhora.data.AlertMode
import com.enhora.data.Completion
import com.enhora.data.CsvExport
import com.enhora.data.Reminder
import com.enhora.data.Repeat
import com.enhora.data.Status
import com.enhora.data.Task
import com.enhora.data.TaskWithReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun tw(task: Task, vararg minutes: Int) =
        TaskWithReminders(task, minutes.map { Reminder(taskId = task.id, minuteOfDay = it) })

    private fun lines(csv: String) = csv.removePrefix("﻿").trimEnd().split("\r\n")

    @Test fun headerAndOneShotRow() {
        val t = Task(id = 1, title = "Pagar luz", epochDay = today.plusDays(2).toEpochDay())
        val l = lines(CsvExport.build(listOf(tw(t, 9 * 60 + 5)), emptyList(), today))
        assertEquals(CsvExport.HEADER, l[0])
        assertEquals("1,Pagar luz,,Una vez,,Sonido,2026-10-09,09:05,Pendiente", l[1])
        assertEquals(2, l.size)
    }

    @Test fun dailyListsEachPastDayWithStatus() {
        val t = Task(id = 2, title = "Agua", epochDay = today.minusDays(2).toEpochDay(), repeat = Repeat.DAILY)
        val states = listOf(
            Completion(2, today.minusDays(2).toEpochDay(), 8 * 60, Status.DONE),
            Completion(2, today.minusDays(1).toEpochDay(), 8 * 60, Status.CANCELED),
        )
        val l = lines(CsvExport.build(listOf(tw(t, 8 * 60)), states, today))
        assertEquals(listOf("Hecho", "Cancelado", "Pendiente"), l.drop(1).map { it.substringAfterLast(",") })
    }

    @Test fun missedPastReminderIsUnattended() {
        val t = Task(id = 3, title = "x", epochDay = today.minusDays(1).toEpochDay())
        val l = lines(CsvExport.build(listOf(tw(t, 60)), emptyList(), today))
        assertTrue(l[1].endsWith(",Sin atender"))
    }

    @Test fun escapesCommasQuotesAndShowsWeekdays() {
        val monAndFri = (1 shl 0) or (1 shl 4)
        val t = Task(
            id = 4, title = "Hola, \"mundo\"", notes = "línea1\nlínea2", epochDay = today.minusDays(2).toEpochDay(), // lunes 5
            repeat = Repeat.CUSTOM, weekDays = monAndFri, alertMode = AlertMode.SILENT,
        )
        val csv = CsvExport.build(listOf(tw(t, 600)), emptyList(), today)
        assertTrue(csv.contains("\"Hola, \"\"mundo\"\"\""))
        assertTrue(csv.contains("\"línea1\nlínea2\""))
        assertTrue(csv.contains(",Personalizado,L V,Silencioso,"))
    }
}
