package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.db.DatabaseSeeder
import com.pelayo.gimnasio.data.remote.MappedFood
import com.pelayo.gimnasio.data.remote.OffMapper
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.data.remote.OpenFoodFactsException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.SerializationException
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.domain.model.DayNutrition
import com.pelayo.gimnasio.domain.model.DietPlanItem
import com.pelayo.gimnasio.domain.model.DietPlanMealItem
import com.pelayo.gimnasio.domain.model.FoodCounts
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.FoodLogItem
import com.pelayo.gimnasio.domain.model.FoodUsage
import com.pelayo.gimnasio.domain.model.FoodUnit
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeIngredientItem
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.domain.model.RecipeUsage
import com.pelayo.gimnasio.util.normalizeForSearch
import com.pelayo.gimnasio.util.nowIsoString
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class NutritionRepository(
    private val db: GimnasioDb,
    private val api: OpenFoodFactsApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val q get() = db.nutritionQueries

    private val _rateLimitedUntil = MutableStateFlow<Instant?>(null)

    /**
     * Hasta cuándo ha cortado Open Food Facts por exceso de peticiones. Vive en el
     * repositorio, que es único, porque el límite es por IP: da igual desde qué
     * pantalla se pregunte, el corte es el mismo para toda la app.
     */
    val rateLimitedUntil: StateFlow<Instant?> = _rateLimitedUntil

    /**
     * Segundos que faltan para poder volver a preguntar; 0 si no hay corte. Se
     * redondea hacia arriba: recién cortado tiene que decir 60, no 59.
     */
    fun rateLimitSecondsLeft(): Int {
        val until = _rateLimitedUntil.value ?: return 0
        val millisLeft = (until - now()).inWholeMilliseconds
        if (millisLeft <= 0) return 0
        return ((millisLeft + 999) / 1000).toInt()
    }

    /**
     * El fallo que toca devolver sin llamar a la red mientras dure el corte:
     * insistir es lo que lo mantiene abierto.
     */
    private fun <T> failWhileRateLimited(): Result<T>? {
        val left = rateLimitSecondsLeft()
        if (left == 0) return null
        return Result.failure(
            OpenFoodFactsException(
                "Open Food Facts sigue sin admitir búsquedas: espera ${left}s. " +
                    "Mientras tanto puedes crear el alimento a mano.",
                retryAfterSeconds = left,
            ),
        )
    }

    private fun startCooldown(seconds: Int) {
        _rateLimitedUntil.value = now() + seconds.seconds
    }

    // --- Alimentos -----------------------------------------------------------

    suspend fun searchLocal(query: String, limit: Int = 50): List<FoodItem> = withContext(dispatcher) {
        // Normalizada como searchName (minúsculas, sin tildes) y sin comodines de
        // LIKE, que venían del teclado tal cual.
        val normalized = normalizeForSearch(query.trim()).replace("%", "").replace("_", "")
        q.searchFoods(normalized, limit.toLong()).executeAsList().map { it.toItem() }
    }

    /** Busca en Open Food Facts y cachea lo encontrado en la base local. */
    suspend fun searchOnline(query: String): Result<List<FoodItem>> = withContext(dispatcher) {
        failWhileRateLimited<List<FoodItem>>()?.let { return@withContext it }
        runCatching {
            val mapped = OffMapper.mapAll(api.search(query))
            // Una transacción para toda la tanda: 25 upserts sueltos son 25
            // transacciones implícitas, y un fallo a mitad dejaba la caché a medias.
            db.transaction {
                mapped.forEach { cacheFood(it) }
            }
            mapped.mapNotNull { food ->
                food.barcode?.let { q.selectFoodByBarcode(it).executeAsOneOrNull()?.toItem() }
            }
        }.mapFailureToMessage()
    }

    /** Busca por código de barras: primero en local, después en OFF. */
    suspend fun findByBarcode(barcode: String): Result<FoodItem?> = withContext(dispatcher) {
        // La caché local sigue funcionando aunque OFF haya cortado, así que se mira
        // antes: un producto ya escaneado se resuelve igual.
        q.selectFoodByBarcode(barcode).executeAsOneOrNull()?.let {
            return@withContext Result.success(it.toItem())
        }
        failWhileRateLimited<FoodItem?>()?.let { return@withContext it }
        runCatching {
            val product = api.productByBarcode(barcode) ?: return@runCatching null
            val mapped = OffMapper.map(product) ?: return@runCatching null
            cacheFood(mapped)
            q.selectFoodByBarcode(barcode).executeAsOneOrNull()?.toItem()
        }.mapFailureToMessage()
    }

    /**
     * Los fallos llegan como excepciones de Ktor con mensajes en inglés y poco
     * claros; aquí se convierten en algo que se pueda enseñar tal cual.
     *
     * Distinguir el fallo de formato del de red no es cosmético: cuando Open Food
     * Facts cambió el tipo de un campo, la app culpaba a la conexión del usuario y
     * la pista para arreglarlo se perdía.
     */
    private fun <T> Result<T>.mapFailureToMessage(): Result<T> {
        // La cancelación no es un fallo: se relanza para que la corrutina muera en
        // silencio en vez de enseñar "comprueba tu conexión" al cerrar la pantalla.
        exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return recoverCatching { error ->
            throw when (error) {
                // Apuntar el corte aquí es lo que hace que las demás pantallas dejen de
                // preguntar sin haberlo sufrido ellas.
                is OpenFoodFactsException -> error.also {
                    it.retryAfterSeconds?.let(::startCooldown)
                }

                is JsonConvertException, is SerializationException -> OpenFoodFactsException(
                    "Open Food Facts ha respondido algo que esta versión de la app no entiende. " +
                        "Puedes crear el alimento a mano mientras tanto.",
                )

                is HttpRequestTimeoutException, is ConnectTimeoutException -> OpenFoodFactsException(
                    "Open Food Facts está tardando demasiado en responder. Inténtalo en un rato.",
                )

                else -> OpenFoodFactsException(
                    "No se pudo conectar con Open Food Facts. Comprueba tu conexión a internet.",
                )
            }
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
            servingSizeG = food.servingSize,
            imageUrl = food.imageUrl,
            cachedAt = nowIsoString(),
            offBarcode = barcode,
            unit = food.unit.name,
            searchName = DatabaseSeeder.foodSearchNameFor(food.name, food.brand),
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
        servingSize: Double? = null,
        unit: FoodUnit = FoodUnit.G,
    ): Long = withContext(dispatcher) {
        db.transactionWithResult {
            q.insertCustomFood(
                name, brand, kcalPer100, proteinPer100, carbsPer100, fatPer100, fiberPer100,
                servingSize, unit.name, DatabaseSeeder.foodSearchNameFor(name, brand),
            )
            q.lastInsertRowId().executeAsOne()
        }
    }

    /** Dónde se está usando un alimento, para saber si se puede borrar. */
    suspend fun foodUsage(id: Long): FoodUsage = withContext(dispatcher) {
        FoodUsage(
            diaryEntries = q.countFoodLogEntriesForFood(id).executeAsOne().toInt(),
            recipeIngredients = q.countRecipeIngredientsForFood(id).executeAsOne().toInt(),
        )
    }

    /**
     * Borra el alimento y lo quita de los planes de dieta, que son plantillas.
     *
     * Devuelve false y no toca nada si está en el diario (historial que no se
     * reescribe) o en una receta (se quedaría sin poder calcularse). Además,
     * ambos lo referencian sin `ON DELETE`, así que borrar a las bravas
     * reventaba con un fallo de clave foránea, igual que pasó con las recetas.
     */
    suspend fun deleteFood(id: Long): Boolean = withContext(dispatcher) {
        db.transactionWithResult {
            val used = q.countFoodLogEntriesForFood(id).executeAsOne() > 0 ||
                q.countRecipeIngredientsForFood(id).executeAsOne() > 0
            if (used) {
                false
            } else {
                q.deleteDietPlanMealsForFood(id)
                q.deleteFood(id)
                true
            }
        }
    }

    /**
     * Corrige un alimento creado a mano. Solo para los propios: los de Open Food
     * Facts se pisan con cada búsqueda (`upsertFoodByBarcode`) y la corrección
     * duraría hasta la siguiente.
     */
    suspend fun updateCustomFood(
        id: Long,
        name: String,
        brand: String?,
        kcalPer100: Double,
        proteinPer100: Double,
        carbsPer100: Double,
        fatPer100: Double,
        fiberPer100: Double? = null,
        servingSize: Double? = null,
        unit: FoodUnit = FoodUnit.G,
    ) = withContext(dispatcher) {
        db.transaction {
            q.updateFood(
                name, brand, kcalPer100, proteinPer100, carbsPer100, fatPer100,
                fiberPer100, servingSize, DatabaseSeeder.foodSearchNameFor(name, brand), id,
            )
            q.updateFoodUnit(unit.name, id)
        }
    }

    /** Corrige a mano si el alimento va en gramos o en mililitros. */
    suspend fun updateFoodUnit(id: Long, unit: FoodUnit) =
        withContext(dispatcher) { q.updateFoodUnit(unit.name, id) }

    /** Lo que hay en el catálogo local: total y cuántos ha creado el usuario. */
    suspend fun foodCounts(): FoodCounts = withContext(dispatcher) {
        val row = q.countFoods().executeAsOne()
        FoodCounts(total = row.total.toInt(), custom = row.custom.toInt())
    }

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

    /**
     * Cambia la cantidad y la comida de una entrada ya apuntada. El alimento o la
     * receta no se tocan: para eso se borra y se añade otra.
     */
    suspend fun updateLogEntry(
        id: Long,
        mealType: MealType,
        grams: Double?,
        servings: Double?,
    ) = withContext(dispatcher) {
        q.updateFoodLogEntry(grams, servings, mealType.name, id)
    }

    suspend fun deleteLogEntry(id: Long) = withContext(dispatcher) { q.deleteFoodLogEntry(id) }

    /**
     * Copia al día las entradas de esa comida del día anterior. Idempotente como
     * "Aplicar a hoy": lo ya apuntado idéntico no se duplica. Devuelve cuántas
     * entradas nuevas se han copiado (0 también si ayer no hubo nada).
     */
    suspend fun repeatMealFromPreviousDay(date: LocalDate, mealType: MealType): Int =
        withContext(dispatcher) {
            val yesterday = date.minus(DatePeriod(days = 1))
            db.transactionWithResult {
                val source = q.selectFoodLogForDate(yesterday.toString()).executeAsList()
                    .filter { it.mealType == mealType.name }
                if (source.isEmpty()) return@transactionWithResult 0

                val existing = q.selectFoodLogForDate(date.toString()).executeAsList()
                val pending = source.filterNot { row ->
                    existing.any {
                        it.mealType == row.mealType && it.foodId == row.foodId &&
                            it.recipeId == row.recipeId && it.grams == row.grams &&
                            it.servings == row.servings
                    }
                }
                pending.forEach { row ->
                    q.insertFoodLogEntry(
                        date.toString(), row.mealType, row.foodId, row.recipeId,
                        row.grams, row.servings, nowIsoString(),
                    )
                }
                pending.size
            }
        }

    /** Reinserta una entrada borrada, para el "Deshacer" del snackbar. */
    suspend fun restoreLogEntry(date: LocalDate, item: FoodLogItem) = withContext(dispatcher) {
        q.insertFoodLogEntry(
            date.toString(), item.mealType.name, item.foodId, item.recipeId,
            item.grams, item.servings, nowIsoString(),
        )
    }

    suspend fun dayNutrition(date: LocalDate = today()): DayNutrition = withContext(dispatcher) {
        val rows = q.selectFoodLogForDate(date.toString()).executeAsList()
        // Un mapa para todas las recetas del día en vez de dos consultas por fila:
        // diez raciones de la misma receta eran veinte consultas.
        val perServingByRecipe = if (rows.any { it.recipeId != null }) recipeMacrosPerServing() else emptyMap()
        val entries = rows.map { row ->
            val mealType = MealType.valueOf(row.mealType)
            if (row.recipeId != null) {
                val servings = row.servings ?: 1.0
                val perServing = perServingByRecipe[row.recipeId] ?: Macros()
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
                    servings = servings,
                    recipeId = row.recipeId,
                )
            } else {
                val grams = row.grams ?: 0.0
                val unit = row.foodUnit.toFoodUnit()
                FoodLogItem(
                    id = row.id,
                    mealType = mealType,
                    name = row.foodName ?: "Alimento",
                    quantityLabel = "${grams.trim()} ${unit.labelEs}",
                    macros = Macros.forGrams(
                        grams = grams,
                        kcalPer100 = row.kcalPer100 ?: 0.0,
                        proteinPer100 = row.proteinPer100 ?: 0.0,
                        carbsPer100 = row.carbsPer100 ?: 0.0,
                        fatPer100 = row.fatPer100 ?: 0.0,
                    ),
                    isRecipe = false,
                    grams = grams,
                    unit = unit,
                    foodId = row.foodId,
                )
            }
        }

        DayNutrition(
            date = date,
            entriesByMeal = entries.groupBy { it.mealType },
            total = entries.fold(Macros()) { acc, item -> acc + item.macros },
        )
    }

    /**
     * Calorías de cada día con algo apuntado, entre dos fechas. Solo se recorren
     * los días que tienen registro (`selectFoodLogDates`), que en un mes son unos
     * pocos: calcularlo en SQL exigiría replicar en la consulta el escalado de
     * recetas por raciones.
     */
    suspend fun dailyCalories(from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        withContext(dispatcher) {
            q.selectFoodLogDates().executeAsList()
                .map { LocalDate.parse(it) }
                .filter { it >= from && it <= to }
                .associateWith { dayNutrition(it).total.kcal }
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

    /** Dónde se está usando una receta, para saber si se puede borrar. */
    suspend fun recipeUsage(id: Long): RecipeUsage = withContext(dispatcher) {
        RecipeUsage(
            diaryEntries = q.countRecipeLogEntries(id).executeAsOne().toInt(),
            dietPlanMeals = q.countRecipeDietPlanMeals(id).executeAsOne().toInt(),
        )
    }

    /**
     * Borra la receta con sus ingredientes y la quita de los planes de dieta, que
     * son plantillas y se pueden rehacer.
     *
     * Devuelve false y no toca nada si está apuntada en el diario: eso es historial
     * de lo que se comió y no se reescribe por borrar una receta. Además, la tabla
     * la referencia sin `ON DELETE`, así que borrarla a las bravas reventaba con un
     * fallo de clave foránea.
     */
    suspend fun deleteRecipe(id: Long): Boolean = withContext(dispatcher) {
        if (q.countRecipeLogEntries(id).executeAsOne() > 0) return@withContext false
        db.transaction {
            q.deleteDietPlanMealsForRecipe(id)
            q.deleteRecipe(id)
        }
        true
    }

    suspend fun addIngredient(recipeId: Long, foodId: Long, grams: Double) =
        withContext(dispatcher) { q.insertRecipeIngredient(recipeId, foodId, grams) }

    /** Cambia la cantidad de un ingrediente; el alimento no se toca. */
    suspend fun updateIngredient(id: Long, grams: Double) =
        withContext(dispatcher) { q.updateRecipeIngredient(grams, id) }

    suspend fun removeIngredient(id: Long) = withContext(dispatcher) { q.deleteRecipeIngredient(id) }

    suspend fun recipes(): List<RecipeItem> = withContext(dispatcher) {
        // Dos consultas en total, no dos por receta.
        val ingredientsByRecipe = q.selectAllRecipeIngredients().executeAsList().groupBy { it.recipeId }
        q.selectAllRecipes().executeAsList().map { recipe ->
            RecipeItem(
                id = recipe.id,
                name = recipe.name,
                servings = recipe.servings.toInt(),
                notes = recipe.notes,
                ingredients = ingredientsByRecipe[recipe.id].orEmpty().map { row ->
                    RecipeIngredientItem(
                        id = row.id,
                        foodId = row.foodId,
                        foodName = row.name,
                        grams = row.grams,
                        macros = Macros.forGrams(
                            row.grams, row.kcalPer100, row.proteinPer100, row.carbsPer100, row.fatPer100,
                        ),
                        unit = row.unit.toFoodUnit(),
                    )
                },
            )
        }
    }

    /** Macros de una ración de cada receta, en una sola consulta. */
    private fun recipeMacrosPerServing(): Map<Long, Macros> =
        q.selectRecipeMacroTotals().executeAsList().associate { row ->
            val portions = row.servings.toInt().coerceAtLeast(1)
            row.recipeId to Macros(
                row.kcal / portions,
                row.protein / portions,
                row.carbs / portions,
                row.fat / portions,
            )
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

    suspend fun addFoodToDietPlan(
        planId: Long,
        dayOfWeek: Int,
        mealType: MealType,
        foodId: Long,
        grams: Double,
    ) = withContext(dispatcher) {
        q.insertDietPlanMeal(planId, mealType.name, foodId, null, grams, null, dayOfWeek.toLong())
    }

    suspend fun addRecipeToDietPlan(
        planId: Long,
        dayOfWeek: Int,
        mealType: MealType,
        recipeId: Long,
        servings: Double,
    ) = withContext(dispatcher) {
        q.insertDietPlanMeal(planId, mealType.name, null, recipeId, null, servings, dayOfWeek.toLong())
    }

    /** Cambia cantidad, comida y día de una entrada del plan. */
    suspend fun updateDietPlanMeal(
        id: Long,
        dayOfWeek: Int,
        mealType: MealType,
        grams: Double?,
        servings: Double?,
    ) = withContext(dispatcher) {
        q.updateDietPlanMeal(grams, servings, mealType.name, dayOfWeek.toLong(), id)
    }

    suspend fun removeDietPlanMeal(id: Long) = withContext(dispatcher) { q.deleteDietPlanMeal(id) }

    /**
     * Copia el menú de un día al de otro dentro del mismo plan, reemplazando lo
     * que hubiera. Una semana entera se llena repitiendo días parecidos, y
     * teclearlos siete veces era el precio de que el plan fuese semanal.
     */
    suspend fun copyDietPlanDay(planId: Long, fromDay: Int, toDay: Int): Int =
        withContext(dispatcher) {
            if (fromDay == toDay) return@withContext 0
            db.transactionWithResult {
                val source = q.selectMealsForDietPlan(planId).executeAsList()
                    .filter { it.dayOfWeek == fromDay.toLong() }
                // Copiar un día vacío no vacía el destino: sería borrar sin decirlo.
                if (source.isEmpty()) return@transactionWithResult 0
                q.deleteDietPlanMealsForDay(planId, toDay.toLong())
                q.copyDietPlanDay(toDay.toLong(), planId, fromDay.toLong())
                source.size
            }
        }

    suspend fun dietPlans(): List<DietPlanItem> = withContext(dispatcher) {
        q.selectAllDietPlans().executeAsList().mapNotNull { dietPlan(it.id) }
    }

    suspend fun activeDietPlan(): DietPlanItem? = withContext(dispatcher) {
        q.selectActiveDietPlan().executeAsOneOrNull()?.let { dietPlan(it.id) }
    }

    suspend fun dietPlan(id: Long): DietPlanItem? = withContext(dispatcher) {
        val plan = q.selectDietPlanById(id).executeAsOneOrNull()
            ?: return@withContext null

        val mealRows = q.selectMealsForDietPlan(id).executeAsList()
        val perServingByRecipe =
            if (mealRows.any { it.recipeId != null }) recipeMacrosPerServing() else emptyMap()
        val meals = mealRows.map { row ->
            val mealType = MealType.valueOf(row.mealType)
            val dayOfWeek = row.dayOfWeek.toInt()
            if (row.recipeId != null) {
                val servings = row.servings ?: 1.0
                val perServing = perServingByRecipe[row.recipeId] ?: Macros()
                DietPlanMealItem(
                    id = row.id,
                    dayOfWeek = dayOfWeek,
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
                val unit = row.foodUnit.toFoodUnit()
                DietPlanMealItem(
                    id = row.id,
                    dayOfWeek = dayOfWeek,
                    mealType = mealType,
                    name = row.foodName ?: "Alimento",
                    quantityLabel = "${grams.trim()} ${unit.labelEs}",
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
                    unit = unit,
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

    /**
     * Copia al diario el menú que el plan tiene para ese día de la semana, para
     * no teclearlo cada vez. Un martes vuelca el martes del plan, no el plan entero.
     */
    suspend fun applyDietPlanToDay(planId: Long, date: LocalDate): Int = withContext(dispatcher) {
        val plan = dietPlan(planId) ?: return@withContext 0
        val meals = plan.mealsForDay(date.dayOfWeek.isoDayNumber)
        db.transactionWithResult {
            // Idempotente: dos toques a "Aplicar a hoy" no duplican el diario. Se
            // salta lo que ya esté apuntado idéntico ese día (misma comida, mismo
            // alimento o receta y misma cantidad).
            val existing = q.selectFoodLogForDate(date.toString()).executeAsList()
            val pending = meals.filterNot { meal ->
                existing.any {
                    it.mealType == meal.mealType.name &&
                        it.foodId == meal.foodId &&
                        it.recipeId == meal.recipeId &&
                        it.grams == meal.grams &&
                        it.servings == meal.servings
                }
            }
            pending.forEach { meal ->
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
            pending.size
        }
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
                    unit = row.unit.toFoodUnit(),
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
    servingSize = servingSizeG,
    imageUrl = imageUrl,
    isCustom = isCustom == 1L,
    unit = unit.toFoodUnit(),
)

/**
 * La unidad se guarda como texto y puede venir a null en los JOIN por la izquierda.
 * Cualquier cosa que no se reconozca son gramos, que es lo que era todo antes de
 * que la columna existiera.
 */
private fun String?.toFoodUnit(): FoodUnit =
    FoodUnit.entries.firstOrNull { it.name == this } ?: FoodUnit.G

private fun Double.trim(): String =
    if (this == toLong().toDouble()) toLong().toString() else (kotlin.math.round(this * 10) / 10.0).toString()
