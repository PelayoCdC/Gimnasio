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
    val servingSizeG: Double?,
    val imageUrl: String?,
    val isCustom: Boolean,
)

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
 * Balance calórico del día: lo ingerido frente al gasto (basal + actividad
 * diaria + entrenamientos registrados).
 */
data class CalorieBalance(
    val intakeKcal: Double,
    val tdeeKcal: Double?,
    val exerciseKcal: Double,
) {
    val expenditureKcal: Double? get() = tdeeKcal?.let { it + exerciseKcal }
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

data class RecipeIngredientItem(
    val id: Long,
    val foodId: Long,
    val foodName: String,
    val grams: Double,
    val macros: Macros,
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
    /** Lo que suman realmente los alimentos del plan, que puede no cuadrar con el objetivo. */
    val plannedMacros: Macros
        get() = meals.fold(Macros()) { acc, meal -> acc + meal.macros }

    fun mealsFor(type: MealType): List<DietPlanMealItem> = meals.filter { it.mealType == type }
}

data class DietPlanMealItem(
    val id: Long,
    val mealType: MealType,
    val name: String,
    val quantityLabel: String,
    val macros: Macros,
    val foodId: Long?,
    val recipeId: Long?,
    val grams: Double?,
    val servings: Double?,
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
