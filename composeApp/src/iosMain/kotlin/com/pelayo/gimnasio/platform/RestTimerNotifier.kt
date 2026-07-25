package com.pelayo.gimnasio.platform

/** Sin implementar en iOS todavía: el cronómetro funciona en pantalla, sin aviso en segundo plano. */
actual class RestTimerNotifier {
    actual fun canNotify(): Boolean = false
    actual fun canScheduleExactAlarms(): Boolean = false
    actual fun startRest(totalSeconds: Int, exerciseName: String?) = Unit
    actual fun updateCountdown(secondsRemaining: Int) = Unit
    actual fun cancelRest() = Unit
    actual fun openExactAlarmSettings() = Unit
}
