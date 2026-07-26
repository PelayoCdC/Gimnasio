package com.pelayo.gimnasio.domain.model

import kotlinx.datetime.LocalDate

data class SetLogItem(
    val id: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val timeSeconds: Int?,
    val isWarmup: Boolean,
) {
    /** Volumen de la serie (kg × reps); 0 si es un ejercicio por tiempo. */
    val volume: Double get() = (weightKg ?: 0.0) * (reps ?: 0)

    val label: String
        get() = when {
            timeSeconds != null -> "$timeSeconds s"
            weightKg != null && reps != null -> "${weightKg.formatKg()} kg × $reps"
            reps != null -> "$reps reps"
            else -> "—"
        }
}

data class WorkoutExerciseProgress(
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val metValue: Double,
    val imageUrl: String?,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetTimeSeconds: Int?,
    val restSeconds: Int,
    /** Series ya registradas en la sesión en curso. */
    val loggedSets: List<SetLogItem>,
    /** Lo que se hizo la última vez, para prerrellenar. */
    val previousSets: List<SetLogItem>,
    /** Aviso de que toca subir peso, si procede. */
    val progression: com.pelayo.gimnasio.domain.calc.ProgressionSuggestion? = null,
) {
    val isComplete: Boolean get() = loggedSets.size >= targetSets
}

data class ActiveWorkout(
    val sessionId: Long,
    val routineId: Long?,
    val routineDayId: Long?,
    val dayName: String,
    val date: LocalDate,
    val exercises: List<WorkoutExerciseProgress>,
)

data class WorkoutSummary(
    val sessionId: Long,
    val date: LocalDate,
    val dayName: String,
    val durationMinutes: Int?,
    val kcalBurned: Double?,
    val totalSets: Int,
    val totalVolume: Double,
    val exercises: List<SessionExerciseSummary>,
    val muscleGroupsWorked: List<String>,
    /** Cómo fue el día: sueño, molestias, cambios de agarre… */
    val notes: String? = null,
    /** Series efectivas por grupo muscular en esta sesión, para el mapa muscular. */
    val volumeByMuscleGroup: Map<Long, Double> = emptyMap(),
)

data class SessionExerciseSummary(
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<SetLogItem>,
) {
    val bestSet: SetLogItem? get() = sets.maxByOrNull { it.volume.takeIf { v -> v > 0 } ?: (it.timeSeconds ?: 0).toDouble() }
    val volume: Double get() = sets.sumOf { it.volume }
}

/** Entrada mínima de una sesión para pintarla en el calendario. */
data class CalendarSession(
    val sessionId: Long,
    val date: LocalDate,
    val dayName: String,
    val isCompleted: Boolean,
    val kcalBurned: Double?,
    val durationMinutes: Int?,
)

/** Un punto de la gráfica de progreso de un ejercicio. */
data class ProgressPoint(
    val date: LocalDate,
    val maxWeight: Double,
    val volume: Double,
    val maxReps: Int,
    val maxTimeSeconds: Int,
)

fun Double.formatKg(): String =
    if (this == toLong().toDouble()) toLong().toString() else ((this * 10).toLong() / 10.0).toString()
