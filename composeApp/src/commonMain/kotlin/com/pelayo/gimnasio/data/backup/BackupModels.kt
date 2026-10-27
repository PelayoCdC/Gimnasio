package com.pelayo.gimnasio.data.backup

import kotlinx.serialization.Serializable

/**
 * Copia completa de los datos del usuario. No incluye el catálogo de ejercicios
 * (se puede volver a descargar de wger); sí los ejercicios y alimentos creados a
 * mano, que no se pueden recuperar de ninguna otra parte, y los alimentos de
 * Open Food Facts que el diario, las recetas o los planes referencian, porque en
 * una instalación limpia la caché está vacía y sin ellos esas entradas se
 * perderían al restaurar.
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
    /** Alimentos de Open Food Facts referenciados. Antes de la versión 5 no viajaban. */
    val cachedFoods: List<BackupCachedFood> = emptyList(),
    val recipes: List<BackupRecipe> = emptyList(),
    val foodLog: List<BackupFoodLogEntry> = emptyList(),
    val dietPlans: List<BackupDietPlan> = emptyList(),
) {
    companion object {
        /**
         * 1: versión inicial.
         * 2: añade los planes de dieta. Las copias de la 1 se siguen leyendo;
         *    simplemente no traen planes.
         * 3: el plan de dieta pasa a ser semanal y cada comida lleva su día. Las
         *    copias de la 2 se siguen leyendo: su menú se repite en los siete días.
         * 4: los alimentos propios llevan su unidad (g o ml). Lo de antes eran gramos.
         * 5: viajan los alimentos de Open Food Facts referenciados (antes esas
         *    entradas se descartaban al restaurar en limpio); las sesiones vuelven
         *    a engancharse a su rutina y día; las `ref` internas dejan de ser el
         *    nombre, para que dos "Ensalada" no colapsen en una; los ejercicios
         *    propios llevan material, imágenes, categoría y vídeo.
         */
        const val CURRENT_VERSION = 5
    }
}

/**
 * Alimento cacheado de Open Food Facts. Solo los referenciados por el diario,
 * las recetas o los planes; el resto de la caché se puede volver a bajar.
 */
@Serializable
data class BackupCachedFood(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val kcalPer100: Double = 0.0,
    val proteinPer100: Double = 0.0,
    val carbsPer100: Double = 0.0,
    val fatPer100: Double = 0.0,
    val fiberPer100: Double? = null,
    val servingSizeG: Double? = null,
    val imageUrl: String? = null,
    val unit: String = "G",
)

@Serializable
data class BackupProfile(
    val name: String?,
    val sex: String,
    val birthDate: String?,
    val heightCm: Double?,
    val activityFactor: Double,
    val goal: String?,
    val targetKcalOverride: Double?,
    /** Desde que existe la proyección de peso; las copias anteriores no lo traen. */
    val targetWeightKg: Double? = null,
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
    /**
     * Clave con la que lo referencian rutinas y series dentro del backup. Desde
     * la versión 5 es una clave sintética ("e0", "e1"…); antes era el nombre, que
     * colapsaba duplicados.
     */
    val ref: String,
    val nameEs: String,
    val descriptionEs: String?,
    val metValue: Double,
    val exerciseType: String,
    val primaryMuscleIds: List<Long>,
    val secondaryMuscleIds: List<Long>,
    /** Desde la versión 5. Las copias anteriores no los traían y se pierden. */
    val equipmentIds: List<Long> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val categoryId: Long? = null,
    val videoUrl: String? = null,
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
    /** Como en los ejercicios: sintética desde la versión 5, antes el nombre. */
    val ref: String,
    val name: String,
    val brand: String?,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val fiberPer100: Double?,
    val servingSizeG: Double?,
    /** 'G' o 'ML'. Las copias anteriores a la versión 4 no lo traen: eran gramos. */
    val unit: String = "G",
)

@Serializable
data class BackupRecipe(
    val name: String,
    val servings: Int,
    val notes: String?,
    val ingredients: List<BackupRecipeIngredient>,
    /**
     * Clave con la que la referencian el diario y los planes desde la versión 5.
     * Las copias anteriores referencian por nombre, con el riesgo de que dos
     * recetas homónimas colapsen en una.
     */
    val ref: String? = null,
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
    /**
     * Día de la semana en ISO (lunes = 1). Las copias de la versión 2 no lo traen
     * porque el plan era de un día suelto: al restaurarlas se replica en los siete,
     * que es lo mismo que hace la migración del esquema.
     */
    val dayOfWeek: Int? = null,
    /** Ref de la receta desde la versión 5; `recipeName` queda para copias viejas. */
    val recipeRef: String? = null,
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
    /** Ref de la receta desde la versión 5; `recipeName` queda para copias viejas. */
    val recipeRef: String? = null,
)
