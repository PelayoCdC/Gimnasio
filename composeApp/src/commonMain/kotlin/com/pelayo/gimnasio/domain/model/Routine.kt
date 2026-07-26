package com.pelayo.gimnasio.domain.model

data class RoutineSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val goal: RoutineGoal,
    val isActive: Boolean,
    val dayCount: Int,
    val exerciseCount: Int,
)

data class RoutineDayInfo(
    val id: Long,
    val routineId: Long,
    val dayIndex: Int,
    val name: String,
    val weekday: Int?,
    val exercises: List<RoutineExerciseItem>,
)

data class RoutineExerciseItem(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val imageUrl: String?,
    val exerciseType: ExerciseType,
    val metValue: Double,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetTimeSeconds: Int?,
    val targetWeight: Double?,
    val restSeconds: Int,
    val notes: String?,
) {
    /** Texto corto del objetivo: "4 × 8-12" o "3 × 45 s". */
    val targetLabel: String
        get() = when {
            exerciseType == ExerciseType.TIME && targetTimeSeconds != null ->
                "$targetSets × $targetTimeSeconds s"
            targetRepsMin != null && targetRepsMax != null && targetRepsMin != targetRepsMax ->
                "$targetSets × $targetRepsMin-$targetRepsMax"
            targetRepsMin != null -> "$targetSets × $targetRepsMin"
            else -> "$targetSets series"
        }
}

data class RoutineDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val goal: RoutineGoal,
    val isActive: Boolean,
    val days: List<RoutineDayInfo>,
)

/** Valores por defecto de series/reps/descanso según el objetivo de la rutina. */
data class TrainingPrescription(
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int,
) {
    companion object {
        fun forGoal(goal: RoutineGoal): TrainingPrescription = when (goal) {
            RoutineGoal.FUERZA -> TrainingPrescription(4, 3, 6, 180)
            RoutineGoal.HIPERTROFIA -> TrainingPrescription(4, 8, 12, 90)
            RoutineGoal.RESISTENCIA -> TrainingPrescription(3, 12, 20, 60)
            RoutineGoal.PERDIDA_GRASA -> TrainingPrescription(3, 12, 20, 60)
        }

        /**
         * Ajusta la prescripción al nivel: quien empieza necesita menos series y,
         * si busca fuerza, series más largas — entrenar a 3 repeticiones exige una
         * técnica que todavía no tiene. Quien va sobrado aguanta una serie más en
         * los objetivos de carga.
         */
        fun forGoal(goal: RoutineGoal, level: ExperienceLevel): TrainingPrescription {
            val base = forGoal(goal)
            return when (level) {
                ExperienceLevel.PRINCIPIANTE -> when (goal) {
                    RoutineGoal.FUERZA -> base.copy(sets = 3, repsMin = 5, repsMax = 8, restSeconds = 150)
                    else -> base.copy(sets = 3)
                }
                ExperienceLevel.INTERMEDIO -> base
                ExperienceLevel.AVANZADO -> when (goal) {
                    RoutineGoal.FUERZA, RoutineGoal.HIPERTROFIA -> base.copy(sets = base.sets + 1)
                    else -> base
                }
            }
        }
    }
}

val RoutineGoal.labelEs: String
    get() = when (this) {
        RoutineGoal.FUERZA -> "Fuerza"
        RoutineGoal.HIPERTROFIA -> "Hipertrofia"
        RoutineGoal.RESISTENCIA -> "Resistencia"
        RoutineGoal.PERDIDA_GRASA -> "Pérdida de grasa"
    }

val ExperienceLevel.labelEs: String
    get() = when (this) {
        ExperienceLevel.PRINCIPIANTE -> "Principiante"
        ExperienceLevel.INTERMEDIO -> "Intermedio"
        ExperienceLevel.AVANZADO -> "Avanzado"
    }

/** Explica en la propia pantalla qué significa cada nivel, para que no se elija a ojo. */
val ExperienceLevel.hintEs: String
    get() = when (this) {
        ExperienceLevel.PRINCIPIANTE -> "Menos de 6 meses entrenando: ejercicios sencillos y seguros de ejecutar."
        ExperienceLevel.INTERMEDIO -> "Ya dominas los básicos con barra y mancuernas."
        ExperienceLevel.AVANZADO -> "Sin restricciones: entran también los movimientos más técnicos."
    }

val ExerciseComplexity.labelEs: String
    get() = when (this) {
        ExerciseComplexity.BASICO -> "Básico"
        ExerciseComplexity.INTERMEDIO -> "Técnico"
        ExerciseComplexity.AVANZADO -> "Muy técnico"
    }

val weekdayNamesEs = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
