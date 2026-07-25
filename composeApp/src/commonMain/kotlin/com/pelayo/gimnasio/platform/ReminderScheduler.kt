package com.pelayo.gimnasio.platform

/** Recordatorio de entrenamiento en un día concreto de la semana. */
data class WorkoutReminder(
    /** 1..7 ISO (lunes = 1). */
    val weekday: Int,
    val hour: Int,
    val minute: Int,
    val dayName: String,
)

/**
 * Programa avisos recurrentes. En Android se apoya en WorkManager, que sobrevive
 * a reinicios del móvil; en iOS todavía no hace nada.
 */
expect class ReminderScheduler {
    fun isSupported(): Boolean

    /** Sustituye todos los recordatorios de entrenamiento por los indicados. */
    fun scheduleWorkoutReminders(reminders: List<WorkoutReminder>)

    /** Aviso semanal para pesarse; [weekday] en formato ISO. */
    fun scheduleWeighInReminder(weekday: Int, hour: Int, minute: Int)

    fun cancelWorkoutReminders()

    fun cancelWeighInReminder()
}
