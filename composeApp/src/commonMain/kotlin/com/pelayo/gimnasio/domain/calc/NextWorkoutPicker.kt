package com.pelayo.gimnasio.domain.calc

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** Día de rutina reducido a lo que necesita la elección del próximo entreno. */
data class ScheduledDay(
    val id: Long,
    val dayIndex: Int,
    val name: String,
    /** 1..7 ISO (lunes = 1); null si la rutina es flexible. */
    val weekday: Int?,
)

sealed interface NextWorkout {
    /** Hoy toca este día porque está asignado a este día de la semana. */
    data class Scheduled(val day: ScheduledDay) : NextWorkout

    /** La rutina no tiene días fijos: toca el siguiente de la rotación. */
    data class NextInRotation(val day: ScheduledDay) : NextWorkout

    /** La rutina tiene días fijos y hoy no toca ninguno. */
    data class RestDay(val nextDay: ScheduledDay?) : NextWorkout

    data object NoRoutine : NextWorkout
}

/**
 * Decide qué entrenamiento proponer hoy. Si la rutina asigna días de la semana,
 * manda el calendario; si no, se rota en orden a partir del último día completado.
 */
object NextWorkoutPicker {

    fun pick(
        days: List<ScheduledDay>,
        today: LocalDate,
        lastCompletedDayId: Long?,
    ): NextWorkout {
        if (days.isEmpty()) return NextWorkout.NoRoutine

        val ordered = days.sortedBy { it.dayIndex }
        val scheduled = ordered.filter { it.weekday != null }

        if (scheduled.isNotEmpty()) {
            val todayWeekday = today.dayOfWeek.isoDayNumber
            val match = scheduled.firstOrNull { it.weekday == todayWeekday }
            if (match != null) return NextWorkout.Scheduled(match)

            // Los días sin asignar no desaparecen por que otros sí lo estén: si hoy
            // no toca ninguno fijo, se proponen en rotación, como en una rutina flexible.
            val flexible = ordered.filter { it.weekday == null }
            if (flexible.isEmpty()) {
                return NextWorkout.RestDay(nextScheduledAfter(scheduled, todayWeekday))
            }
            return NextWorkout.NextInRotation(nextInRotation(flexible, lastCompletedDayId))
        }

        // Rotación libre: el siguiente al último completado, volviendo al principio.
        return NextWorkout.NextInRotation(nextInRotation(ordered, lastCompletedDayId))
    }

    private fun nextInRotation(days: List<ScheduledDay>, lastCompletedDayId: Long?): ScheduledDay {
        val lastIndex = days.indexOfFirst { it.id == lastCompletedDayId }
        return if (lastIndex == -1) days.first() else days[(lastIndex + 1) % days.size]
    }

    /** Siguiente día asignado de la semana, mirando hacia delante y dando la vuelta. */
    private fun nextScheduledAfter(scheduled: List<ScheduledDay>, todayWeekday: Int): ScheduledDay? {
        val byWeekday = scheduled.sortedBy { it.weekday }
        return byWeekday.firstOrNull { it.weekday!! > todayWeekday } ?: byWeekday.firstOrNull()
    }
}
