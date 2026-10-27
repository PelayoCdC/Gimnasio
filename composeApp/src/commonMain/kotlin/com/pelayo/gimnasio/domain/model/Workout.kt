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
    /** Esfuerzo percibido (6-10); opcional, alimenta el consejo de deload. */
    val rpe: Double? = null,
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
    /** Sin carga externa: la pantalla no pide kilos, aunque deja añadirlos. */
    val isBodyweight: Boolean = false,
    val metValue: Double,
    val imageUrl: String?,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetTimeSeconds: Int?,
    val restSeconds: Int,
    /** Series ya registradas en la sesión en curso. */
    val loggedSets: List<SetLogItem>,
    /** Lo que se hizo la última vez, para prerrellenar y para tenerlo delante. */
    val previousSets: List<SetLogItem>,
    /** Cuándo fue esa última vez; null si es la primera. */
    val previousDate: LocalDate? = null,
    /** Aviso de que toca subir peso, si procede. */
    val progression: com.pelayo.gimnasio.domain.calc.ProgressionSuggestion? = null,
    /** El equivalente para los ejercicios que se miden en segundos. */
    val timeProgression: com.pelayo.gimnasio.domain.calc.TimeProgressionSuggestion? = null,
) {
    /** Series efectivas registradas: los calentamientos no cuentan para el objetivo. */
    val effectiveSets: Int get() = loggedSets.count { !it.isWarmup }

    val isComplete: Boolean get() = effectiveSets >= targetSets
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

    /** Volumen efectivo: los calentamientos no cuentan, como en el resto del resumen. */
    val volume: Double get() = sets.filterNot { it.isWarmup }.sumOf { it.volume }
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
    /** Mejor 1RM estimado (Epley) de la sesión; null si no hay peso y reps. */
    val best1RM: Double? = null,
)

/** Un récord batido en una sesión concreta, para celebrarlo en el resumen. */
data class SessionRecord(
    val exerciseName: String,
    /** "102,5 kg (antes 100)", "1RM est. 120 kg", "75 s (antes 60)"… */
    val description: String,
)

/** Mejores marcas históricas de un ejercicio (sesiones completadas, sin calentamientos). */
data class PersonalRecords(
    val maxWeightKg: Double?,
    val maxReps: Int?,
    val maxTimeSeconds: Int?,
    val bestEstimated1RM: Double?,
    val sessions: Int,
)

fun Double.formatKg(): String =
    if (this == toLong().toDouble()) toLong().toString() else (kotlin.math.round(this * 10) / 10.0).toString()
