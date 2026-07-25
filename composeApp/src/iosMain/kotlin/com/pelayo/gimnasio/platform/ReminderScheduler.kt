package com.pelayo.gimnasio.platform

/** Sin implementar en iOS: haría falta UNUserNotificationCenter. */
actual class ReminderScheduler {
    actual fun isSupported(): Boolean = false
    actual fun scheduleWorkoutReminders(reminders: List<WorkoutReminder>) = Unit
    actual fun scheduleWeighInReminder(weekday: Int, hour: Int, minute: Int) = Unit
    actual fun cancelWorkoutReminders() = Unit
    actual fun cancelWeighInReminder() = Unit
}
