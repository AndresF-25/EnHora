package com.enhora.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import com.enhora.data.Slot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val EsLocale: Locale = Locale("es")

/** Hora en formato de 12 h con AM / PM ("9:05 AM", "12:00 PM"). */
fun formatTime(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, minuteOfDay % 60, if (h < 12) "AM" else "PM")
}

fun formatLongDate(day: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", EsLocale)).replaceFirstChar { it.uppercase() }

fun formatShortDate(day: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern("EEE d MMM", EsLocale)).replaceFirstChar { it.uppercase() }

fun slotIcon(slot: Slot): ImageVector = when (slot) {
    Slot.MORNING -> Icons.Default.WbSunny
    Slot.AFTERNOON -> Icons.Default.WbTwilight
    Slot.NIGHT -> Icons.Default.NightsStay
}
