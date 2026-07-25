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
    }
}

val RoutineGoal.labelEs: String
    get() = when (this) {
        RoutineGoal.FUERZA -> "Fuerza"
        RoutineGoal.HIPERTROFIA -> "Hipertrofia"
        RoutineGoal.RESISTENCIA -> "Resistencia"
        RoutineGoal.PERDIDA_GRASA -> "Pérdida de grasa"
    }

val weekdayNamesEs = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
