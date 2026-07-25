package com.pelayo.gimnasio.domain.calc

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class StreakInfo(
    /** Semanas seguidas cumpliendo el objetivo, contando hasta la semana pasada. */
    val weekStreak: Int,
    /** Entrenamientos de la semana en curso. */
    val sessionsThisWeek: Int,
    /** Objetivo semanal, tomado de los días de la rutina activa. */
    val weeklyTarget: Int,
    /** Días desde el último entrenamiento; null si no hay ninguno. */
    val daysSinceLastWorkout: Int?,
    val totalSessions: Int,
) {
    val weekCompleted: Boolean get() = weeklyTarget > 0 && sessionsThisWeek >= weeklyTarget
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
)

/**
 * Calcula rachas por semanas, no por días seguidos: en un plan de gimnasio los
 * días de descanso son parte del programa, así que encadenar días penalizaría
 * a quien entrena bien. Una semana cuenta si se alcanzó el número de sesiones
 * que marca la rutina activa.
 */
object StreakCalculator {

    fun calculate(
        sessionDates: List<LocalDate>,
        today: LocalDate,
        weeklyTarget: Int,
    ): StreakInfo {
        val distinctDates = sessionDates.distinct().sorted()
        val startOfThisWeek = today.startOfWeek()

        val sessionsThisWeek = distinctDates.count { it >= startOfThisWeek && it <= today }

        var streak = 0
        if (weeklyTarget > 0) {
            // Se cuenta hacia atrás desde la semana pasada: la actual aún puede completarse.
            var weekStart = startOfThisWeek.minus(DatePeriod(days = 7))
            while (true) {
                val weekEnd = weekStart.plusDays(6)
                val count = distinctDates.count { it >= weekStart && it <= weekEnd }
                if (count >= weeklyTarget) {
                    streak++
                    weekStart = weekStart.minus(DatePeriod(days = 7))
                } else {
                    break
                }
            }
            // La semana en curso, si ya está cumplida, suma también.
            if (sessionsThisWeek >= weeklyTarget) streak++
        }

        return StreakInfo(
            weekStreak = streak,
            sessionsThisWeek = sessionsThisWeek,
            weeklyTarget = weeklyTarget,
            daysSinceLastWorkout = distinctDates.lastOrNull()?.let { it.daysUntil(today) },
            totalSessions = sessionDates.size,
        )
    }

    /** Logros sencillos por volumen de entrenamientos y constancia. */
    fun achievements(streak: StreakInfo): List<Achievement> = listOf(
        Achievement(
            id = "first",
            title = "El primer paso",
            description = "Completa tu primer entrenamiento",
            unlocked = streak.totalSessions >= 1,
        ),
        Achievement(
            id = "ten",
            title = "Cogiendo el ritmo",
            description = "10 entrenamientos completados",
            unlocked = streak.totalSessions >= 10,
        ),
        Achievement(
            id = "fifty",
            title = "Esto va en serio",
            description = "50 entrenamientos completados",
            unlocked = streak.totalSessions >= 50,
        ),
        Achievement(
            id = "hundred",
            title = "Veterano",
            description = "100 entrenamientos completados",
            unlocked = streak.totalSessions >= 100,
        ),
        Achievement(
            id = "month",
            title = "Un mes sin fallar",
            description = "4 semanas seguidas cumpliendo tu rutina",
            unlocked = streak.weekStreak >= 4,
        ),
        Achievement(
            id = "quarter",
            title = "Constancia de hierro",
            description = "12 semanas seguidas cumpliendo tu rutina",
            unlocked = streak.weekStreak >= 12,
        ),
    )

    /** Lunes de la semana a la que pertenece la fecha. */
    private fun LocalDate.startOfWeek(): LocalDate =
        minus(DatePeriod(days = dayOfWeek.isoDayNumber - 1))

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))
}
