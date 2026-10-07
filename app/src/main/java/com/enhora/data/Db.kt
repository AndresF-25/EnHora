package com.enhora.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskWithReminders>>

    @Transaction @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskWithReminders>

    @Transaction @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskWithReminders?

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminder(id: Long): Reminder?

    @Insert suspend fun insertTask(task: Task): Long
    @Update suspend fun updateTask(task: Task)
    @Insert suspend fun insertReminders(reminders: List<Reminder>): List<Long>

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun deleteReminders(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Query("SELECT * FROM completions")
    fun observeCompletions(): Flow<List<Completion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCompletion(completion: Completion)

    @Query("DELETE FROM completions WHERE taskId = :taskId AND epochDay = :epochDay AND minuteOfDay = :minuteOfDay")
    suspend fun clearCompletion(taskId: Long, epochDay: Long, minuteOfDay: Int)

    @Query("SELECT * FROM completions")
    suspend fun getCompletions(): List<Completion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnooze(snooze: Snooze)

    @Query("DELETE FROM snoozes WHERE reminderId = :reminderId")
    suspend fun deleteSnooze(reminderId: Long)

    @Query("SELECT * FROM snoozes")
    suspend fun getSnoozes(): List<Snooze>

    @Query("SELECT COUNT(*) FROM completions WHERE taskId = :taskId AND epochDay = :epochDay AND minuteOfDay = :minuteOfDay")
    suspend fun isCompleted(taskId: Long, epochDay: Long, minuteOfDay: Int): Int
}

@Database(
    entities = [Task::class, Reminder::class, Completion::class, Snooze::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        /** Añade el estado (hecho / cancelado) a los recordatorios resueltos; los existentes quedan como hechos. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completions ADD COLUMN status INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Modo de aviso por tarea y aplazamientos guardados (para que sobrevivan a un reinicio). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN alertMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS snoozes (reminderId INTEGER NOT NULL, epochDay INTEGER NOT NULL, " +
                        "atMillis INTEGER NOT NULL, PRIMARY KEY(reminderId), " +
                        "FOREIGN KEY(reminderId) REFERENCES reminders(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enhora.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
