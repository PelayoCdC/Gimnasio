package com.pelayo.gimnasio.domain.model

import kotlinx.datetime.LocalDate

data class FoodItem(
    val id: Long,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val fiberPer100: Double?,
    /** Ración en la unidad del alimento, gramos o mililitros. */
    val servingSize: Double?,
    val imageUrl: String?,
    val isCustom: Boolean,
    val unit: FoodUnit = FoodUnit.G,
)

/** Tamaño del catálogo local de alimentos, para la cabecera del catálogo. */
data class FoodCounts(val total: Int, val custom: Int)

/** Macros totales de una cantidad concreta de comida. */
data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(other: Macros) = Macros(
        kcal + other.kcal,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
    )

    companion object {
        /** Escala los valores por 100 g de un alimento a los gramos consumidos. */
        fun forGrams(
            grams: Double,
            kcalPer100: Double,
            proteinPer100: Double,
            carbsPer100: Double,
            fatPer100: Double,
        ): Macros {
            val factor = grams / 100.0
            return Macros(
                kcal = kcalPer100 * factor,
                protein = proteinPer100 * factor,
                carbs = carbsPer100 * factor,
                fat = fatPer100 * factor,
            )
        }
    }
}

data class FoodLogItem(
    val id: Long,
    val mealType: MealType,
    val name: String,
    val quantityLabel: String,
    val macros: Macros,
    val isRecipe: Boolean,
    /** Cantidad tal cual está guardada, para poder editarla sin releer la fila. */
    val grams: Double? = null,
    val servings: Double? = null,
    /** Unidad del alimento; null en las recetas, que van por raciones. */
    val unit: FoodUnit? = null,
    /** Referencias originales, para poder deshacer un borrado reinsertando. */
    val foodId: Long? = null,
    val recipeId: Long? = null,
)

data class DayNutrition(
    val date: LocalDate,
    val entriesByMeal: Map<MealType, List<FoodLogItem>>,
    val total: Macros,
) {
    fun mealTotal(meal: MealType): Macros =
        entriesByMeal[meal].orEmpty().fold(Macros()) { acc, item -> acc + item.macros }
}

/**
 * Balance calórico del día: lo ingerido frente al gasto.
 *
 * El gasto es el TDEE a secas: el factor de actividad ya incluye el entrenamiento
 * por definición ("Moderado (3-5 días/semana)"), así que sumarle encima las kcal
 * del entreno del día las contaba dos veces y sobreestimaba el gasto en
 * 200-400 kcal. [exerciseKcal] se conserva solo para enseñarla como detalle.
 */
data class CalorieBalance(
    val intakeKcal: Double,
    val tdeeKcal: Double?,
    val exerciseKcal: Double,
) {
    val expenditureKcal: Double? get() = tdeeKcal
    val balanceKcal: Double? get() = expenditureKcal?.let { intakeKcal - it }
}

data class RecipeItem(
    val id: Long,
    val name: String,
    val servings: Int,
    val notes: String?,
    val ingredients: List<RecipeIngredientItem>,
) {
    /** Macros de una ración. */
    val macrosPerServing: Macros
        get() {
            val total = ingredients.fold(Macros()) { acc, item -> acc + item.macros }
            val portions = servings.coerceAtLeast(1)
            return Macros(
                total.kcal / portions,
                total.protein / portions,
                total.carbs / portions,
                total.fat / portions,
            )
        }
}

/**
 * Dónde está usada una receta. El diario manda: si está ahí, no se puede borrar,
 * porque es el registro de lo que se comió.
 */
data class RecipeUsage(val diaryEntries: Int, val dietPlanMeals: Int) {
    val canDelete: Boolean get() = diaryEntries == 0
}

/**
 * Dónde está usado un alimento. Igual que con las recetas, el diario veta el
 * borrado; una receta también, porque se quedaría sin poder calcularse.
 */
data class FoodUsage(val diaryEntries: Int, val recipeIngredients: Int) {
    val canDelete: Boolean get() = diaryEntries == 0 && recipeIngredients == 0
}

data class RecipeIngredientItem(
    val id: Long,
    val foodId: Long,
    val foodName: String,
    val grams: Double,
    val macros: Macros,
    val unit: FoodUnit = FoodUnit.G,
)

data class DietPlanItem(
    val id: Long,
    val name: String,
    val targetKcal: Double?,
    val targetProteinG: Double?,
    val targetCarbsG: Double?,
    val targetFatG: Double?,
    val isActive: Boolean,
    val meals: List<DietPlanMealItem>,
) {
    fun mealsForDay(dayOfWeek: Int): List<DietPlanMealItem> =
        meals.filter { it.dayOfWeek == dayOfWeek }

    fun mealsFor(dayOfWeek: Int, type: MealType): List<DietPlanMealItem> =
        meals.filter { it.dayOfWeek == dayOfWeek && it.mealType == type }

    /**
     * Lo que suman realmente los alimentos de un día, que puede no cuadrar con el
     * objetivo. Los objetivos del plan son diarios, así que el total de la semana
     * no sirve para compararlos.
     */
    fun plannedMacros(dayOfWeek: Int): Macros =
        mealsForDay(dayOfWeek).fold(Macros()) { acc, meal -> acc + meal.macros }

    /** Días de la semana (ISO) con algo planificado. */
    val plannedDays: Set<Int> get() = meals.map { it.dayOfWeek }.toSet()
}

data class DietPlanMealItem(
    val id: Long,
    /** Día de la semana en ISO: lunes = 1. */
    val dayOfWeek: Int,
    val mealType: MealType,
    val name: String,
    val quantityLabel: String,
    val macros: Macros,
    val foodId: Long?,
    val recipeId: Long?,
    val grams: Double?,
    val servings: Double?,
    /** Unidad del alimento; null en las recetas, que van por raciones. */
    val unit: FoodUnit? = null,
)

/**
 * Reparto de macros a partir de un objetivo calórico. Se fija la proteína por
 * peso corporal, la grasa como porcentaje de las calorías y el resto va a
 * hidratos, que es como se plantea habitualmente una dieta.
 */
object MacroSplit {
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_FAT = 9.0

    /** Gramos de proteína por kg de peso corporal según objetivo. */
    fun proteinPerKg(goal: RoutineGoal): Double = when (goal) {
        RoutineGoal.FUERZA, RoutineGoal.HIPERTROFIA -> 2.0
        RoutineGoal.PERDIDA_GRASA -> 2.2
        RoutineGoal.RESISTENCIA -> 1.6
    }

    fun split(
        targetKcal: Double,
        bodyWeightKg: Double,
        goal: RoutineGoal,
        fatPercentOfKcal: Double = 0.25,
    ): Macros {
        val protein = proteinPerKg(goal) * bodyWeightKg
        val fat = targetKcal * fatPercentOfKcal / KCAL_PER_G_FAT
        val remaining = targetKcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT
        val carbs = (remaining / KCAL_PER_G_CARBS).coerceAtLeast(0.0)
        return Macros(kcal = targetKcal, protein = protein, carbs = carbs, fat = fat)
    }
}

val MealType.labelEs: String
    get() = when (this) {
        MealType.DESAYUNO -> "Desayuno"
        MealType.COMIDA -> "Comida"
        MealType.CENA -> "Cena"
        MealType.SNACK -> "Snack"
    }
