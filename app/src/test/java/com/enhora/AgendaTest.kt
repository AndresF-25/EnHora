package com.enhora

import com.enhora.data.Agenda
import com.enhora.data.Completion
import com.enhora.data.Reminder
import com.enhora.data.Repeat
import com.enhora.data.Status
import com.enhora.data.Task
import com.enhora.data.TaskWithReminders
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AgendaTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun task(id: Long, day: LocalDate, repeat: Int = Repeat.NONE, vararg minutes: Int) =
        TaskWithReminders(
            Task(id = id, title = "t$id", epochDay = day.toEpochDay(), repeat = repeat),
            minutes.map { Reminder(taskId = id, minuteOfDay = it) },
        )

    @Test fun completionIsPerReminder() {
        val t = task(1, today, Repeat.NONE, 8 * 60, 20 * 60)
        val done = setOf(Completion(1, today.toEpochDay(), 8 * 60))
        val e = Agenda.entriesOn(listOf(t), done, today)
        assertEquals(listOf(true, false), e.map { it.done })
    }

    @Test fun overdueListsOnlyUncompletedPastEntries() {
        val yesterday = today.minusDays(1)
        val pending = task(1, yesterday, Repeat.NONE, 9 * 60)
        val finished = task(2, yesterday, Repeat.NONE, 10 * 60)
        val upcoming = task(3, today, Repeat.NONE, 9 * 60)
        val done = setOf(Completion(2, yesterday.toEpochDay(), 10 * 60))
        val o = Agenda.overdue(listOf(pending, finished, upcoming), done, today)
        assertEquals(listOf(1L), o.map { it.taskId })
    }

    @Test fun dailyTaskOverdueCountsEachMissedDay() {
        val t = task(1, today.minusDays(3), Repeat.DAILY, 9 * 60)
        assertEquals(3, Agenda.overdue(listOf(t), emptySet(), today).size)
    }

    @Test fun canceledIsResolvedButNotDone() {
        val t = task(1, today, Repeat.NONE, 8 * 60, 9 * 60, 10 * 60)
        val states = setOf(
            Completion(1, today.toEpochDay(), 8 * 60, Status.DONE),
            Completion(1, today.toEpochDay(), 9 * 60, Status.CANCELED),
        )
        val e = Agenda.entriesOn(listOf(t), states, today)
        assertEquals(listOf(Status.DONE, Status.CANCELED, Status.PENDING), e.map { it.status })
        assertEquals(listOf(true, false, false), e.map { it.done })
        assertEquals(listOf(true, true, false), e.map { it.resolved })
    }

    @Test fun canceledEntriesAreNotOverdue() {
        val yesterday = today.minusDays(1)
        val t = task(1, yesterday, Repeat.NONE, 9 * 60, 10 * 60)
        val states = setOf(Completion(1, yesterday.toEpochDay(), 9 * 60, Status.CANCELED))
        assertEquals(listOf(10 * 60), Agenda.overdue(listOf(t), states, today).map { it.minuteOfDay })
    }

    @Test fun summaryCountsCanceledAsResolved() {
        val t = task(1, today, Repeat.NONE, 9 * 60, 15 * 60)
        val states = setOf(Completion(1, today.toEpochDay(), 9 * 60, Status.CANCELED))
        val s = Agenda.summary(listOf(t), states, YearMonth.of(2026, 10)).getValue(today)
        assertEquals(0, s.done)
        assertEquals(1, s.resolved)
    }

    @Test fun summaryOnlyHasDaysWithTasks() {
        val t = task(1, today, Repeat.NONE, 9 * 60, 15 * 60)
        val done = setOf(Completion(1, today.toEpochDay(), 9 * 60))
        val s = Agenda.summary(listOf(t), done, YearMonth.of(2026, 10))
        assertEquals(1, s.size)
        assertEquals(2, s.getValue(today).total)
        assertEquals(1, s.getValue(today).done)
    }
}
