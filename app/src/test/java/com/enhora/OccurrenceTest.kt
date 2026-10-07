package com.enhora

import com.enhora.data.Occurrence
import com.enhora.data.Repeat
import com.enhora.data.Slot
import com.enhora.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test

class OccurrenceTest {
    private val monday = LocalDate.of(2026, 10, 5)

    @Test fun slots() {
        assertEquals(Slot.MORNING, Slot.of(8 * 60))
        assertEquals(Slot.AFTERNOON, Slot.of(15 * 60))
        assertEquals(Slot.NIGHT, Slot.of(23 * 60))
        assertEquals(Slot.NIGHT, Slot.of(2 * 60))
    }

    @Test fun oneShotPastHasNoNext() {
        val t = Task(title = "x", epochDay = monday.toEpochDay())
        assertNull(Occurrence.next(t, 8 * 60, monday.atTime(9, 0)))
        assertEquals(monday.atTime(8, 0), Occurrence.next(t, 8 * 60, monday.atTime(7, 0)))
    }

    @Test fun dailyRollsToTomorrow() {
        val t = Task(title = "x", epochDay = monday.toEpochDay(), repeat = Repeat.DAILY)
        assertEquals(monday.plusDays(1).atTime(8, 0), Occurrence.next(t, 8 * 60, monday.atTime(9, 0)))
    }

    @Test fun customRepeatsOnlyOnChosenDays() {
        val monAndFri = (1 shl 0) or (1 shl 4)
        val t = Task(title = "x", epochDay = monday.toEpochDay(), repeat = Repeat.CUSTOM, weekDays = monAndFri)
        assertEquals(true, Occurrence.occursOn(t, monday))
        assertEquals(false, Occurrence.occursOn(t, monday.plusDays(2)))
        assertEquals(true, Occurrence.occursOn(t, monday.plusDays(4)))
        assertEquals(false, Occurrence.occursOn(t, monday.minusDays(3)))
        assertEquals(LocalDateTime.of(2026, 10, 9, 8, 0), Occurrence.next(t, 8 * 60, monday.atTime(9, 0)))
    }

    @Test fun weeklyPicksNextSelectedDay() {
        val wed = 1 shl 2
        val t = Task(title = "x", epochDay = monday.toEpochDay(), repeat = Repeat.WEEKLY, weekDays = wed)
        assertEquals(LocalDateTime.of(2026, 10, 7, 8, 0), Occurrence.next(t, 8 * 60, monday.atTime(9, 0)))
    }
}
