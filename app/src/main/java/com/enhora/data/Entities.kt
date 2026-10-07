package com.enhora.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

object Repeat {
    const val NONE = 0
    const val DAILY = 1
    /** Cada semana, el mismo día de la semana que la fecha de inicio. */
    const val WEEKLY = 2
    /** Días de la semana elegidos por el usuario (`weekDays`). */
    const val CUSTOM = 3
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Fecha de inicio (LocalDate.toEpochDay). */
    val epochDay: Long,
    val repeat: Int = Repeat.NONE,
    /** Bitmask de días para WEEKLY: bit 0 = lunes ... bit 6 = domingo. */
    val weekDays: Int = 0,
    /** Cómo avisa: [AlertMode.SOUND], [AlertMode.VIBRATE] o [AlertMode.SILENT]. */
    @ColumnInfo(defaultValue = "0") val alertMode: Int = AlertMode.SOUND,
)

/** Forma de aviso de un recordatorio. */
object AlertMode {
    const val SOUND = 0
    const val VIBRATE = 1
    const val SILENT = 2
}

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("taskId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val minuteOfDay: Int,
)

/** Marca un recordatorio concreto (tarea + día + hora) como completado. */
@Entity(
    tableName = "completions",
    primaryKeys = ["taskId", "epochDay", "minuteOfDay"],
    foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class Completion(
    val taskId: Long,
    val epochDay: Long,
    val minuteOfDay: Int,
    /** [Status.DONE] o [Status.CANCELED]; un recordatorio sin fila sigue pendiente. */
    @ColumnInfo(defaultValue = "1") val status: Int = Status.DONE,
)

/** Estado de un recordatorio en un día concreto. */
object Status {
    const val PENDING = 0
    const val DONE = 1
    const val CANCELED = 2
}

/** Aplazamiento pendiente de un recordatorio; se guarda para sobrevivir a un reinicio del móvil. */
@Entity(
    tableName = "snoozes",
    foreignKeys = [ForeignKey(entity = Reminder::class, parentColumns = ["id"], childColumns = ["reminderId"], onDelete = ForeignKey.CASCADE)],
)
data class Snooze(
    @PrimaryKey val reminderId: Long,
    /** Día (epochDay) del recordatorio aplazado. */
    val epochDay: Long,
    /** Momento (epoch millis) en que debe volver a avisar. */
    val atMillis: Long,
)

data class TaskWithReminders(
    @Embedded val task: Task,
    @Relation(parentColumn = "id", entityColumn = "taskId") val reminders: List<Reminder>,
)
