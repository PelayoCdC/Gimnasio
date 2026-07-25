package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.remote.MappedFood
import com.pelayo.gimnasio.data.remote.OffMapper
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.data.remote.OpenFoodFactsException
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.domain.model.DayNutrition
import com.pelayo.gimnasio.domain.model.DietPlanItem
import com.pelayo.gimnasio.domain.model.DietPlanMealItem
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.FoodLogItem
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeIngredientItem
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.util.nowIsoString
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class NutritionRepository(
    private val db: GimnasioDb,
    private val api: OpenFoodFactsApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.nutritionQueries

    // --- Alimentos -----------------------------------------------------------

    suspend fun searchLocal(query: String, limit: Int = 50): List<FoodItem> = withContext(dispatcher) {
        q.searchFoods(query.trim(), limit.toLong()).executeAsList().map { it.toItem() }
    }

    /** Busca en Open Food Facts y cachea lo encontrado en la base local. */
    suspend fun searchOnline(query: String): Result<List<FoodItem>> = withContext(dispatcher) {
        runCatching {
            val mapped = OffMapper.mapAll(api.search(query))
            mapped.forEach { cacheFood(it) }
            mapped.mapNotNull { food ->
                food.barcode?.let { q.selectFoodByBarcode(it).executeAsOneOrNull()?.toItem() }
            }
        }.mapFailureToMessage()
    }

    /** Busca por código de barras: primero en local, después en OFF. */
    suspend fun findByBarcode(barcode: String): Result<FoodItem?> = withContext(dispatcher) {
        q.selectFoodByBarcode(barcode).executeAsOneOrNull()?.let {
            return@withContext Result.success(it.toItem())
        }
        runCatching {
            val product = api.productByBarcode(barcode) ?: return@runCatching null
            val mapped = OffMapper.map(product) ?: return@runCatching null
            cacheFood(mapped)
            q.selectFoodByBarcode(barcode).executeAsOneOrNull()?.toItem()
        }.mapFailureToMessage()
    }

    /**
     * Los fallos de red llegan como excepciones de Ktor con mensajes en inglés y
     * poco claros; aquí se convierten en algo que se pueda enseñar tal cual.
     */
    private fun <T> Result<T>.mapFailureToMessage(): Result<T> = recoverCatching { error ->
        throw when (error) {
            is OpenFoodFactsException -> error
            else -> OpenFoodFactsException(
                "No se pudo conectar con Open Food Facts. Comprueba tu conexión a internet.",
            )
        }
    }

    private fun cacheFood(food: MappedFood) {
        val barcode = food.barcode ?: return
        q.upsertFoodByBarcode(
            name = food.name,
            brand = food.brand,
            kcalPer100 = food.kcalPer100,
            proteinPer100 = food.proteinPer100,
            carbsPer100 = food.carbsPer100,
            fatPer100 = food.fatPer100,
            fiberPer100 = food.fiberPer100,
            servingSizeG = food.servingSizeG,
            imageUrl = food.imageUrl,
            cachedAt = nowIsoString(),
            offBarcode = barcode,
        )
    }

    suspend fun createCustomFood(
        name: String,
        brand: String?,
        kcalPer100: Double,
        proteinPer100: Double,
        carbsPer100: Double,
        fatPer100: Double,
        fiberPer100: Double? = null,
        servingSizeG: Double? = null,
    ): Long = withContext(dispatcher) {
        db.transactionWithResult {
            q.insertCustomFood(
                name, brand, kcalPer100, proteinPer100, carbsPer100, fatPer100, fiberPer100, servingSizeG,
            )
            q.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun deleteFood(id: Long) = withContext(dispatcher) { q.deleteFood(id) }

    suspend fun food(id: Long): FoodItem? = withContext(dispatcher) {
        q.selectFoodById(id).executeAsOneOrNull()?.toItem()
    }

    // --- Diario --------------------------------------------------------------

    suspend fun logFood(
        date: LocalDate,
        mealType: MealType,
        foodId: Long,
        grams: Double,
    ) = withContext(dispatcher) {
        q.insertFoodLogEntry(date.toString(), mealType.name, foodId, null, grams, null, nowIsoString())
    }

    suspend fun logRecipe(
        date: LocalDate,
        mealType: MealType,
        recipeId: Long,
        servings: Double,
    ) = withContext(dispatcher) {
        q.insertFoodLogEntry(date.toString(), mealType.name, null, recipeId, null, servings, nowIsoString())
    }

    suspend fun deleteLogEntry(id: Long) = withContext(dispatcher) { q.deleteFoodLogEntry(id) }

    suspend fun dayNutrition(date: LocalDate = today()): DayNutrition = withContext(dispatcher) {
        val entries = q.selectFoodLogForDate(date.toString()).executeAsList().map { row ->
            val mealType = MealType.valueOf(row.mealType)
            if (row.recipeId != null) {
                val recipe = recipe(row.recipeId)
                val servings = row.servings ?: 1.0
                val perServing = recipe?.macrosPerServing ?: Macros()
                FoodLogItem(
                    id = row.id,
                    mealType = mealType,
                    name = row.recipeName ?: "Receta",
                    quantityLabel = if (servings == 1.0) "1 ración" else "${servings.trim()} raciones",
                    macros = Macros(
                        perServing.kcal * servings,
                        perServing.protein * servings,
                        perServing.carbs * servings,
                        perServing.fat * servings,
                    ),
                    isRecipe = true,
                )
            } else {
                val grams = row.grams ?: 0.0
                FoodLogItem(
                    id = row.id,
                    mealType = mealType,
                    name = row.foodName ?: "Alimento",
                    quantityLabel = "${grams.trim()} g",
                    macros = Macros.forGrams(
                        grams = grams,
                        kcalPer100 = row.kcalPer100 ?: 0.0,
                        proteinPer100 = row.proteinPer100 ?: 0.0,
                        carbsPer100 = row.carbsPer100 ?: 0.0,
                        fatPer100 = row.fatPer100 ?: 0.0,
                    ),
                    isRecipe = false,
                )
            }
        }

        DayNutrition(
            date = date,
            entriesByMeal = entries.groupBy { it.mealType },
            total = entries.fold(Macros()) { acc, item -> acc + item.macros },
        )
    }

    /** Ingesta frente a gasto del día (TDEE + entrenamientos de esa fecha). */
    suspend fun calorieBalance(
        date: LocalDate,
        tdeeKcal: Double?,
        exerciseKcal: Double,
    ): CalorieBalance = CalorieBalance(
        intakeKcal = dayNutrition(date).total.kcal,
        tdeeKcal = tdeeKcal,
        exerciseKcal = exerciseKcal,
    )

    // --- Recetas -------------------------------------------------------------

    suspend fun createRecipe(name: String, servings: Int, notes: String?): Long =
        withContext(dispatcher) {
            db.transactionWithResult {
                q.insertRecipe(name, servings.toLong(), notes)
                q.lastInsertRowId().executeAsOne()
            }
        }

    suspend fun updateRecipe(id: Long, name: String, servings: Int, notes: String?) =
        withContext(dispatcher) { q.updateRecipe(name, servings.toLong(), notes, id) }

    suspend fun deleteRecipe(id: Long) = withContext(dispatcher) { q.deleteRecipe(id) }

    suspend fun addIngredient(recipeId: Long, foodId: Long, grams: Double) =
        withContext(dispatcher) { q.insertRecipeIngredient(recipeId, foodId, grams) }

    suspend fun removeIngredient(id: Long) = withContext(dispatcher) { q.deleteRecipeIngredient(id) }

    suspend fun recipes(): List<RecipeItem> = withContext(dispatcher) {
        q.selectAllRecipes().executeAsList().mapNotNull { recipe(it.id) }
    }

    // --- Planes de dieta -----------------------------------------------------

    suspend fun createDietPlan(
        name: String,
        targetKcal: Double?,
        targetProteinG: Double?,
        targetCarbsG: Double?,
        targetFatG: Double?,
    ): Long = withContext(dispatcher) {
        db.transactionWithResult {
            q.insertDietPlan(name, targetKcal, targetProteinG, targetCarbsG, targetFatG, 0)
            val id = q.lastInsertRowId().executeAsOne()
            // El primero que se crea pasa a ser el activo.
            if (q.selectAllDietPlans().executeAsList().size == 1) q.setActiveDietPlan(id)
            id
        }
    }

    suspend fun updateDietPlan(
        id: Long,
        name: String,
        targetKcal: Double?,
        targetProteinG: Double?,
        targetCarbsG: Double?,
        targetFatG: Double?,
    ) = withContext(dispatcher) {
        q.updateDietPlan(name, targetKcal, targetProteinG, targetCarbsG, targetFatG, id)
    }

    suspend fun deleteDietPlan(id: Long) = withContext(dispatcher) { q.deleteDietPlan(id) }

    suspend fun setActiveDietPlan(id: Long) = withContext(dispatcher) { q.setActiveDietPlan(id) }

    suspend fun addFoodToDietPlan(planId: Long, mealType: MealType, foodId: Long, grams: Double) =
        withContext(dispatcher) {
            q.insertDietPlanMeal(planId, mealType.name, foodId, null, grams, null)
        }

    suspend fun addRecipeToDietPlan(planId: Long, mealType: MealType, recipeId: Long, servings: Double) =
        withContext(dispatcher) {
            q.insertDietPlanMeal(planId, mealType.name, null, recipeId, null, servings)
        }

    suspend fun removeDietPlanMeal(id: Long) = withContext(dispatcher) { q.deleteDietPlanMeal(id) }

    suspend fun dietPlans(): List<DietPlanItem> = withContext(dispatcher) {
        q.selectAllDietPlans().executeAsList().mapNotNull { dietPlan(it.id) }
    }

    suspend fun activeDietPlan(): DietPlanItem? = withContext(dispatcher) {
        q.selectActiveDietPlan().executeAsOneOrNull()?.let { dietPlan(it.id) }
    }

    suspend fun dietPlan(id: Long): DietPlanItem? = withContext(dispatcher) {
        val plan = q.selectAllDietPlans().executeAsList().firstOrNull { it.id == id }
            ?: return@withContext null

        val meals = q.selectMealsForDietPlan(id).executeAsList().map { row ->
            val mealType = MealType.valueOf(row.mealType)
            if (row.recipeId != null) {
                val servings = row.servings ?: 1.0
                val perServing = recipe(row.recipeId)?.macrosPerServing ?: Macros()
                DietPlanMealItem(
                    id = row.id,
                    mealType = mealType,
                    name = row.recipeName ?: "Receta",
                    quantityLabel = if (servings == 1.0) "1 ración" else "${servings.trim()} raciones",
                    macros = Macros(
                        perServing.kcal * servings,
                        perServing.protein * servings,
                        perServing.carbs * servings,
                        perServing.fat * servings,
                    ),
                    foodId = null,
                    recipeId = row.recipeId,
                    grams = null,
                    servings = servings,
                )
            } else {
                val grams = row.grams ?: 0.0
                DietPlanMealItem(
                    id = row.id,
                    mealType = mealType,
                    name = row.foodName ?: "Alimento",
                    quantityLabel = "${grams.trim()} g",
                    macros = Macros.forGrams(
                        grams = grams,
                        kcalPer100 = row.kcalPer100 ?: 0.0,
                        proteinPer100 = row.proteinPer100 ?: 0.0,
                        carbsPer100 = row.carbsPer100 ?: 0.0,
                        fatPer100 = row.fatPer100 ?: 0.0,
                    ),
                    foodId = row.foodId,
                    recipeId = null,
                    grams = grams,
                    servings = null,
                )
            }
        }

        DietPlanItem(
            id = plan.id,
            name = plan.name,
            targetKcal = plan.targetKcal,
            targetProteinG = plan.targetProteinG,
            targetCarbsG = plan.targetCarbsG,
            targetFatG = plan.targetFatG,
            isActive = plan.isActive == 1L,
            meals = meals,
        )
    }

    /** Copia el menú del plan al diario de un día, para no teclearlo cada vez. */
    suspend fun applyDietPlanToDay(planId: Long, date: LocalDate): Int = withContext(dispatcher) {
        val plan = dietPlan(planId) ?: return@withContext 0
        db.transaction {
            plan.meals.forEach { meal ->
                q.insertFoodLogEntry(
                    date.toString(),
                    meal.mealType.name,
                    meal.foodId,
                    meal.recipeId,
                    meal.grams,
                    meal.servings,
                    nowIsoString(),
                )
            }
        }
        plan.meals.size
    }

    suspend fun recipe(id: Long): RecipeItem? = withContext(dispatcher) {
        val recipe = q.selectRecipeById(id).executeAsOneOrNull() ?: return@withContext null
        RecipeItem(
            id = recipe.id,
            name = recipe.name,
            servings = recipe.servings.toInt(),
            notes = recipe.notes,
            ingredients = q.selectIngredientsForRecipe(id).executeAsList().map { row ->
                RecipeIngredientItem(
                    id = row.id,
                    foodId = row.foodId,
                    foodName = row.name,
                    grams = row.grams,
                    macros = Macros.forGrams(
                        row.grams, row.kcalPer100, row.proteinPer100, row.carbsPer100, row.fatPer100,
                    ),
                )
            },
        )
    }
}

private fun com.pelayo.gimnasio.db.Food.toItem() = FoodItem(
    id = id,
    barcode = offBarcode,
    name = name,
    brand = brand,
    kcalPer100 = kcalPer100,
    proteinPer100 = proteinPer100,
    carbsPer100 = carbsPer100,
    fatPer100 = fatPer100,
    fiberPer100 = fiberPer100,
    servingSizeG = servingSizeG,
    imageUrl = imageUrl,
    isCustom = isCustom == 1L,
)

private fun Double.trim(): String =
    if (this == toLong().toDouble()) toLong().toString() else ((this * 10).toLong() / 10.0).toString()
