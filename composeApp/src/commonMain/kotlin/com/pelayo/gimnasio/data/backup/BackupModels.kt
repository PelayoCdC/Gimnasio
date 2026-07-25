package com.pelayo.gimnasio.data.backup

import kotlinx.serialization.Serializable

/**
 * Copia completa de los datos del usuario. No incluye el catálogo de ejercicios
 * (se puede volver a descargar de wger) ni los alimentos cacheados de Open Food
 * Facts; sí los ejercicios y alimentos creados a mano, que no se pueden recuperar
 * de ninguna otra parte.
 *
 * Los ejercicios se referencian por `wgerId` cuando vienen del catálogo, porque
 * los ids locales cambian entre instalaciones.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val profile: BackupProfile? = null,
    val measurements: List<BackupMeasurement> = emptyList(),
    val customExercises: List<BackupCustomExercise> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val customFoods: List<BackupFood> = emptyList(),
    val recipes: List<BackupRecipe> = emptyList(),
    val foodLog: List<BackupFoodLogEntry> = emptyList(),
    val dietPlans: List<BackupDietPlan> = emptyList(),
) {
    companion object {
        /**
         * 1: versión inicial.
         * 2: añade los planes de dieta. Las copias de la 1 se siguen leyendo;
         *    simplemente no traen planes.
         */
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class BackupProfile(
    val name: String?,
    val sex: String,
    val birthDate: String?,
    val heightCm: Double?,
    val activityFactor: Double,
    val goal: String?,
    val targetKcalOverride: Double?,
)

@Serializable
data class BackupMeasurement(
    val date: String,
    val weightKg: Double,
    val neckCm: Double?,
    val waistCm: Double?,
    val hipCm: Double?,
    val bodyFatPercent: Double?,
    val notes: String?,
)

@Serializable
data class BackupCustomExercise(
    /** Clave con la que lo referencian rutinas y series dentro del backup. */
    val ref: String,
    val nameEs: String,
    val descriptionEs: String?,
    val metValue: Double,
    val exerciseType: String,
    val primaryMuscleIds: List<Long>,
    val secondaryMuscleIds: List<Long>,
)

/** Referencia a un ejercicio: por wgerId si es del catálogo, o por ref si es propio. */
@Serializable
data class BackupExerciseRef(
    val wgerId: Long? = null,
    val customRef: String? = null,
)

@Serializable
data class BackupRoutine(
    val name: String,
    val description: String?,
    val goal: String,
    val createdAt: String,
    val isActive: Boolean,
    val isGenerated: Boolean,
    val days: List<BackupRoutineDay>,
)

@Serializable
data class BackupRoutineDay(
    val dayIndex: Int,
    val name: String,
    val weekday: Int?,
    val exercises: List<BackupRoutineExercise>,
)

@Serializable
data class BackupRoutineExercise(
    val exercise: BackupExerciseRef,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetTimeSeconds: Int?,
    val targetWeight: Double?,
    val restSeconds: Int,
    val notes: String?,
)

@Serializable
data class BackupSession(
    val routineName: String?,
    val dayName: String?,
    val date: String,
    val startTime: String?,
    val durationMinutes: Int?,
    val kcalBurned: Double?,
    val notes: String?,
    val isCompleted: Boolean,
    val sets: List<BackupSet>,
)

@Serializable
data class BackupSet(
    val exercise: BackupExerciseRef,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val timeSeconds: Int?,
    val isWarmup: Boolean,
    val rpe: Double?,
)

@Serializable
data class BackupFood(
    val ref: String,
    val name: String,
    val brand: String?,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val fiberPer100: Double?,
    val servingSizeG: Double?,
)

@Serializable
data class BackupRecipe(
    val name: String,
    val servings: Int,
    val notes: String?,
    val ingredients: List<BackupRecipeIngredient>,
)

@Serializable
data class BackupRecipeIngredient(
    /** Código de barras si el alimento venía de Open Food Facts; si no, ref del propio. */
    val barcode: String?,
    val customRef: String?,
    val grams: Double,
)

@Serializable
data class BackupDietPlan(
    val name: String,
    val targetKcal: Double?,
    val targetProteinG: Double?,
    val targetCarbsG: Double?,
    val targetFatG: Double?,
    val isActive: Boolean,
    val meals: List<BackupDietPlanMeal>,
)

@Serializable
data class BackupDietPlanMeal(
    val mealType: String,
    /** Igual que en el diario: código de barras, alimento propio o receta. */
    val barcode: String? = null,
    val customRef: String? = null,
    val recipeName: String? = null,
    val grams: Double? = null,
    val servings: Double? = null,
)

@Serializable
data class BackupFoodLogEntry(
    val date: String,
    val mealType: String,
    val barcode: String?,
    val customRef: String?,
    val recipeName: String?,
    val grams: Double?,
    val servings: Double?,
    val loggedAt: String,
)
