package com.pelayo.gimnasio.platform

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

actual class ReminderScheduler(private val context: Context) {

    companion object {
        const val WORKOUT_TAG = "recordatorio-entreno"
        const val WEIGH_IN_NAME = "recordatorio-pesaje"
        const val KEY_TITLE = "titulo"
        const val KEY_TEXT = "texto"
    }

    private val workManager get() = WorkManager.getInstance(context)

    actual fun isSupported(): Boolean = true

    actual fun scheduleWorkoutReminders(reminders: List<WorkoutReminder>) {
        cancelWorkoutReminders()
        reminders.forEach { reminder ->
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(reminder.weekday, reminder.hour, reminder.minute), TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_TITLE, "Hoy toca entrenar")
                        .putString(KEY_TEXT, reminder.dayName)
                        .build(),
                )
                .addTag(WORKOUT_TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "$WORKOUT_TAG-${reminder.weekday}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    actual fun scheduleWeighInReminder(weekday: Int, hour: Int, minute: Int) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(weekday, hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_TITLE, "Toca pesarse")
                    .putString(KEY_TEXT, "Registra tu peso para mantener las métricas al día")
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            WEIGH_IN_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    actual fun cancelWorkoutReminders() {
        workManager.cancelAllWorkByTag(WORKOUT_TAG)
    }

    actual fun cancelWeighInReminder() {
        workManager.cancelUniqueWork(WEIGH_IN_NAME)
    }

    /**
     * Milisegundos hasta la próxima vez que toque ese día y hora. Si hoy es el día
     * pero la hora ya pasó, se va a la semana siguiente.
     */
    private fun delayUntil(isoWeekday: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Calendar usa domingo = 1; el modelo usa lunes = 1.
            set(Calendar.DAY_OF_WEEK, if (isoWeekday == 7) Calendar.SUNDAY else isoWeekday + 1)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
