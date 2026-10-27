package com.pelayo.gimnasio.data.backup

import com.pelayo.gimnasio.data.db.DatabaseSeeder
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.util.nowIsoString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class ImportSummary(
    val routines: Int,
    val sessions: Int,
    val measurements: Int,
    /** Entradas del diario realmente insertadas, no las que traía el JSON. */
    val foodLogEntries: Int,
    val recipes: Int,
    val dietPlans: Int,
    /** Ejercicios del backup que no están en el catálogo local y se han omitido. */
    val skippedExercises: Int,
    /**
     * Entradas del diario, ingredientes y comidas de plan cuyo alimento no se pudo
     * resolver (típicamente copias anteriores a la versión 5, que no traían los
     * alimentos de Open Food Facts).
     */
    val skippedFoodItems: Int = 0,
)

/**
 * Exporta e importa todos los datos del usuario en JSON.
 *
 * Los ejercicios y alimentos del catálogo se referencian por su identificador
 * externo (wgerId, código de barras) en vez de por el id local, porque los ids
 * de la base local no coinciden entre instalaciones. Lo creado a mano (ejercicios,
 * alimentos, recetas) se referencia por una `ref` propia del fichero, estable
 * aunque haya nombres repetidos.
 */
class BackupService(
    private val db: GimnasioDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.backupQueries

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- Exportación ---------------------------------------------------------

    suspend fun exportToJson(): String = withContext(dispatcher) {
        // En transacción para que una escritura concurrente no parta la copia.
        json.encodeToString(BackupData.serializer(), db.transactionWithResult { buildBackup() })
    }

    private fun buildBackup(): BackupData {
        val customExerciseRows = q.selectCustomExercises().executeAsList()
        val exerciseRefById = customExerciseRows.withIndex().associate { (i, e) -> e.id to "e$i" }
        val customExercises = customExerciseRows.mapIndexed { i, exercise ->
            val muscles = db.catalogQueries.selectMusclesForExercise(exercise.id).executeAsList()
            BackupCustomExercise(
                ref = "e$i",
                nameEs = exercise.nameEs,
                descriptionEs = exercise.descriptionEs,
                metValue = exercise.metValue,
                exerciseType = exercise.exerciseType,
                primaryMuscleIds = muscles.filter { it.role == "PRIMARY" }.map { it.id },
                secondaryMuscleIds = muscles.filter { it.role == "SECONDARY" }.map { it.id },
                equipmentIds = db.catalogQueries.selectEquipmentForExercise(exercise.id)
                    .executeAsList().map { it.id },
                imageUrls = db.catalogQueries.selectImagesForExercise(exercise.id)
                    .executeAsList().map { it.url },
                categoryId = exercise.categoryId,
                videoUrl = exercise.videoUrl,
            )
        }

        val routines = db.routinesQueries.selectAllRoutines().executeAsList().map { routine ->
            BackupRoutine(
                name = routine.name,
                description = routine.description,
                goal = routine.goal,
                createdAt = routine.createdAt,
                isActive = routine.isActive == 1L,
                isGenerated = routine.isGenerated == 1L,
                days = db.routinesQueries.selectDaysForRoutine(routine.id).executeAsList().map { day ->
                    BackupRoutineDay(
                        dayIndex = day.dayIndex.toInt(),
                        name = day.name,
                        weekday = day.weekday?.toInt(),
                        exercises = q.selectAllRoutineExercisesForDay(day.id).executeAsList().map { row ->
                            BackupRoutineExercise(
                                exercise = BackupExerciseRef(
                                    wgerId = row.wgerId,
                                    customRef = exerciseRefById[row.exerciseId],
                                ),
                                position = row.position.toInt(),
                                targetSets = row.targetSets.toInt(),
                                targetRepsMin = row.targetRepsMin?.toInt(),
                                targetRepsMax = row.targetRepsMax?.toInt(),
                                targetTimeSeconds = row.targetTimeSeconds?.toInt(),
                                targetWeight = row.targetWeight,
                                restSeconds = row.restSeconds.toInt(),
                                notes = row.notes,
                            )
                        },
                    )
                },
            )
        }

        val sessions = q.selectAllSessions().executeAsList().map { session ->
            BackupSession(
                routineName = session.routineId?.let {
                    q.selectRoutineNameById(it).executeAsOneOrNull()
                },
                dayName = session.routineDayId?.let {
                    db.routinesQueries.selectRoutineDayById(it).executeAsOneOrNull()?.name
                },
                date = session.date,
                startTime = session.startTime,
                durationMinutes = session.durationMinutes?.toInt(),
                kcalBurned = session.kcalBurned,
                notes = session.notes,
                isCompleted = session.isCompleted == 1L,
                sets = q.selectSetsForSessionWithRef(session.id).executeAsList().map { row ->
                    BackupSet(
                        exercise = BackupExerciseRef(
                            wgerId = row.wgerId,
                            customRef = exerciseRefById[row.exerciseId],
                        ),
                        setNumber = row.setNumber.toInt(),
                        weightKg = row.weightKg,
                        reps = row.reps?.toInt(),
                        timeSeconds = row.timeSeconds?.toInt(),
                        isWarmup = row.isWarmup == 1L,
                        rpe = row.rpe,
                    )
                },
            )
        }

        val customFoodRows = q.selectCustomFoods().executeAsList()
        val foodRefById = customFoodRows.withIndex().associate { (i, f) -> f.id to "f$i" }
        val customFoods = customFoodRows.mapIndexed { i, food ->
            BackupFood(
                ref = "f$i",
                name = food.name,
                brand = food.brand,
                kcalPer100 = food.kcalPer100,
                proteinPer100 = food.proteinPer100,
                carbsPer100 = food.carbsPer100,
                fatPer100 = food.fatPer100,
                fiberPer100 = food.fiberPer100,
                servingSizeG = food.servingSizeG,
                unit = food.unit,
            )
        }

        val cachedFoods = q.selectReferencedOffFoods().executeAsList().map { food ->
            BackupCachedFood(
                barcode = food.offBarcode!!,
                name = food.name,
                brand = food.brand,
                kcalPer100 = food.kcalPer100,
                proteinPer100 = food.proteinPer100,
                carbsPer100 = food.carbsPer100,
                fatPer100 = food.fatPer100,
                fiberPer100 = food.fiberPer100,
                servingSizeG = food.servingSizeG,
                imageUrl = food.imageUrl,
                unit = food.unit,
            )
        }

        val recipeRows = db.nutritionQueries.selectAllRecipes().executeAsList()
        val recipeRefById = recipeRows.withIndex().associate { (i, r) -> r.id to "r$i" }
        val recipes = recipeRows.mapIndexed { i, recipe ->
            BackupRecipe(
                ref = "r$i",
                name = recipe.name,
                servings = recipe.servings.toInt(),
                notes = recipe.notes,
                ingredients = db.nutritionQueries.selectIngredientsForRecipe(recipe.id)
                    .executeAsList()
                    .mapNotNull { ingredient ->
                        val food = q.selectFoodRef(ingredient.foodId).executeAsOneOrNull()
                            ?: return@mapNotNull null
                        BackupRecipeIngredient(
                            barcode = food.offBarcode,
                            customRef = foodRefById[ingredient.foodId],
                            grams = ingredient.grams,
                        )
                    },
            )
        }

        val foodLog = q.selectAllFoodLogEntries().executeAsList().map { entry ->
            val food = entry.foodId?.let { q.selectFoodRef(it).executeAsOneOrNull() }
            BackupFoodLogEntry(
                date = entry.date,
                mealType = entry.mealType,
                barcode = food?.offBarcode,
                customRef = entry.foodId?.let { foodRefById[it] },
                recipeName = entry.recipeId?.let { q.selectRecipeNameById(it).executeAsOneOrNull() },
                recipeRef = entry.recipeId?.let { recipeRefById[it] },
                grams = entry.grams,
                servings = entry.servings,
                loggedAt = entry.loggedAt,
            )
        }

        val dietPlans = q.selectAllDietPlansForBackup().executeAsList().map { plan ->
            BackupDietPlan(
                name = plan.name,
                targetKcal = plan.targetKcal,
                targetProteinG = plan.targetProteinG,
                targetCarbsG = plan.targetCarbsG,
                targetFatG = plan.targetFatG,
                isActive = plan.isActive == 1L,
                meals = q.selectDietPlanMealsForBackup(plan.id).executeAsList().map { meal ->
                    BackupDietPlanMeal(
                        mealType = meal.mealType,
                        barcode = meal.offBarcode,
                        customRef = meal.foodId?.let { foodRefById[it] },
                        recipeName = meal.recipeName,
                        recipeRef = meal.recipeId?.let { recipeRefById[it] },
                        grams = meal.grams,
                        servings = meal.servings,
                        dayOfWeek = meal.dayOfWeek.toInt(),
                    )
                },
            )
        }

        return BackupData(
            exportedAt = nowIsoString(),
            profile = db.profileQueries.selectProfile().executeAsOneOrNull()?.let {
                BackupProfile(
                    name = it.name,
                    sex = it.sex,
                    birthDate = it.birthDate,
                    heightCm = it.heightCm,
                    activityFactor = it.activityFactor,
                    goal = it.goal,
                    targetKcalOverride = it.targetKcalOverride,
                    targetWeightKg = it.targetWeightKg,
                )
            },
            measurements = db.profileQueries.selectMeasurements().executeAsList().map {
                BackupMeasurement(
                    date = it.date,
                    weightKg = it.weightKg,
                    neckCm = it.neckCm,
                    waistCm = it.waistCm,
                    hipCm = it.hipCm,
                    bodyFatPercent = it.bodyFatPercent,
                    notes = it.notes,
                )
            },
            customExercises = customExercises,
            routines = routines,
            sessions = sessions,
            customFoods = customFoods,
            cachedFoods = cachedFoods,
            recipes = recipes,
            foodLog = foodLog,
            dietPlans = dietPlans,
        )
    }

    // --- Importación ---------------------------------------------------------

    /**
     * Restaura un backup **sustituyendo** los datos del usuario. El catálogo de
     * wger se conserva; los ejercicios del backup que no estén en él se omiten
     * y se cuentan en el resumen, igual que las entradas de comida cuyo alimento
     * no se pueda resolver.
     */
    suspend fun importFromJson(content: String): Result<ImportSummary> = withContext(dispatcher) {
        runCatching {
            val backup = json.decodeFromString(BackupData.serializer(), content)
            require(backup.version >= 1) {
                "El fichero no parece una copia de seguridad de esta app."
            }
            require(backup.version <= BackupData.CURRENT_VERSION) {
                "El backup es de una versión más nueva de la app (${backup.version})."
            }

            var skippedExercises = 0
            var insertedFoodLog = 0
            var skippedFoodItems = 0
            db.transaction {
                q.deleteAllUserData()

                backup.profile?.let {
                    db.profileQueries.upsertProfile(
                        it.name, it.sex, it.birthDate, it.heightCm,
                        it.activityFactor, it.goal, it.targetKcalOverride, it.targetWeightKg,
                    )
                }

                backup.measurements.forEach {
                    db.profileQueries.insertMeasurement(
                        it.date, it.weightKg, it.neckCm, it.waistCm, it.hipCm,
                        it.bodyFatPercent, it.notes,
                    )
                }

                // Ids válidos para no violar las foráneas con un backup de otra
                // versión del mapa de músculos o con material que ya no exista.
                val validMuscleIds = db.catalogQueries.selectAllMuscles()
                    .executeAsList().map { it.id }.toSet()
                val validEquipmentIds = db.catalogQueries.selectAllEquipment()
                    .executeAsList().map { it.id }.toSet()

                // Los ejercicios propios se recrean primero: las rutinas los referencian.
                // claim y no put: en copias viejas la ref era el nombre y podía
                // repetirse; el primero gana, como hacía la consulta con LIMIT 1.
                val exerciseIdByRef = mutableMapOf<String, Long>()
                backup.customExercises.forEach { exercise ->
                    db.catalogQueries.insertCustomExercise(
                        nameEs = exercise.nameEs,
                        descriptionEs = exercise.descriptionEs,
                        categoryId = exercise.categoryId,
                        videoUrl = exercise.videoUrl,
                        metValue = exercise.metValue,
                        exerciseType = exercise.exerciseType,
                        searchName = DatabaseSeeder.searchNameFor(exercise.nameEs, null),
                    )
                    val id = db.catalogQueries.lastInsertRowId().executeAsOne()
                    exercise.primaryMuscleIds.filter { it in validMuscleIds }.forEach {
                        db.catalogQueries.insertExerciseMuscle(id, it, "PRIMARY")
                    }
                    exercise.secondaryMuscleIds.filter { it in validMuscleIds }.forEach {
                        db.catalogQueries.insertExerciseMuscle(id, it, "SECONDARY")
                    }
                    exercise.equipmentIds.filter { it in validEquipmentIds }.forEach {
                        db.catalogQueries.insertExerciseEquipment(id, it)
                    }
                    exercise.imageUrls.forEachIndexed { index, url ->
                        db.catalogQueries.insertExerciseImage(id, url, if (index == 0) 1 else 0)
                    }
                    exerciseIdByRef.claim(exercise.ref, id)
                }

                val foodIdByRef = mutableMapOf<String, Long>()
                backup.customFoods.forEach { food ->
                    db.nutritionQueries.insertCustomFood(
                        food.name, food.brand, food.kcalPer100, food.proteinPer100,
                        food.carbsPer100, food.fatPer100, food.fiberPer100, food.servingSizeG,
                        food.unit, DatabaseSeeder.foodSearchNameFor(food.name, food.brand),
                    )
                    val id = db.nutritionQueries.lastInsertRowId().executeAsOne()
                    foodIdByRef.claim(food.ref, id)
                }

                backup.cachedFoods.forEach { food ->
                    q.insertOffFoodIfMissing(
                        food.barcode, food.name, food.brand, food.kcalPer100,
                        food.proteinPer100, food.carbsPer100, food.fatPer100,
                        food.fiberPer100, food.servingSizeG, food.imageUrl, food.unit,
                        DatabaseSeeder.foodSearchNameFor(food.name, food.brand),
                    )
                }

                val routineIdByName = mutableMapOf<String, Long>()
                val dayIdByRoutineAndName = mutableMapOf<Pair<String, String>, Long>()
                backup.routines.forEach { routine ->
                    db.routinesQueries.insertRoutine(
                        routine.name, routine.description, routine.goal, routine.createdAt,
                        if (routine.isActive) 1 else 0, if (routine.isGenerated) 1 else 0,
                    )
                    val routineId = db.catalogQueries.lastInsertRowId().executeAsOne()
                    routineIdByName.claim(routine.name, routineId)
                    routine.days.forEach { day ->
                        db.routinesQueries.insertRoutineDay(
                            routineId, day.dayIndex.toLong(), day.name, day.weekday?.toLong(),
                        )
                        val dayId = db.catalogQueries.lastInsertRowId().executeAsOne()
                        dayIdByRoutineAndName.claim(routine.name to day.name, dayId)
                        day.exercises.forEach { item ->
                            val exerciseId = resolveExercise(item.exercise, exerciseIdByRef)
                            if (exerciseId == null) {
                                skippedExercises++
                            } else {
                                db.routinesQueries.insertRoutineExercise(
                                    dayId, exerciseId, item.position.toLong(),
                                    item.targetSets.toLong(), item.targetRepsMin?.toLong(),
                                    item.targetRepsMax?.toLong(), item.targetTimeSeconds?.toLong(),
                                    item.targetWeight, item.restSeconds.toLong(), item.notes,
                                )
                            }
                        }
                    }
                }

                val recipeIdByRef = mutableMapOf<String, Long>()
                val recipeIdByName = mutableMapOf<String, Long>()
                backup.recipes.forEach { recipe ->
                    db.nutritionQueries.insertRecipe(recipe.name, recipe.servings.toLong(), recipe.notes)
                    val recipeId = db.nutritionQueries.lastInsertRowId().executeAsOne()
                    recipe.ref?.let { recipeIdByRef.claim(it, recipeId) }
                    recipeIdByName.claim(recipe.name, recipeId)
                    recipe.ingredients.forEach { ingredient ->
                        val foodId = resolveFood(ingredient.barcode, ingredient.customRef, foodIdByRef)
                        if (foodId == null) {
                            skippedFoodItems++
                        } else {
                            db.nutritionQueries.insertRecipeIngredient(recipeId, foodId, ingredient.grams)
                        }
                    }
                }

                backup.sessions.forEach { session ->
                    // La rutina y el día se reenganchan por nombre, que es como
                    // viajan en el fichero: los ids no valen entre instalaciones.
                    val routineId = session.routineName?.let { routineIdByName[it] }
                    val dayId = if (routineId != null && session.dayName != null) {
                        dayIdByRoutineAndName[session.routineName to session.dayName]
                    } else {
                        null
                    }
                    db.workoutsQueries.insertSession(
                        routineId = routineId,
                        routineDayId = dayId,
                        date = session.date,
                        startTime = session.startTime,
                        durationMinutes = session.durationMinutes?.toLong(),
                        kcalBurned = session.kcalBurned,
                        notes = session.notes,
                        isCompleted = if (session.isCompleted) 1 else 0,
                    )
                    val sessionId = db.catalogQueries.lastInsertRowId().executeAsOne()
                    session.sets.forEach { set ->
                        val exerciseId = resolveExercise(set.exercise, exerciseIdByRef)
                        if (exerciseId == null) {
                            skippedExercises++
                        } else {
                            db.workoutsQueries.insertSetLog(
                                sessionId, exerciseId, set.setNumber.toLong(), set.weightKg,
                                set.reps?.toLong(), set.timeSeconds?.toLong(),
                                if (set.isWarmup) 1 else 0, set.rpe,
                            )
                        }
                    }
                }

                // Después de recetas y alimentos, porque los referencia.
                backup.dietPlans.forEach { plan ->
                    q.insertDietPlanForBackup(
                        plan.name, plan.targetKcal, plan.targetProteinG,
                        plan.targetCarbsG, plan.targetFatG, if (plan.isActive) 1 else 0,
                    )
                    val planId = db.catalogQueries.lastInsertRowId().executeAsOne()
                    plan.meals.forEach { meal ->
                        val recipeId = meal.recipeRef?.let { recipeIdByRef[it] }
                            ?: meal.recipeName?.let { recipeIdByName[it] }
                        val foodId = if (recipeId == null) {
                            resolveFood(meal.barcode, meal.customRef, foodIdByRef)
                        } else {
                            null
                        }
                        // La tabla exige exactamente uno de los dos.
                        if (foodId != null || recipeId != null) {
                            // Sin día es una copia de la versión 2, de cuando el plan
                            // era de un día suelto: se repite en toda la semana.
                            val days = meal.dayOfWeek?.let { listOf(it) } ?: (1..7).toList()
                            days.forEach { day ->
                                db.nutritionQueries.insertDietPlanMeal(
                                    planId, meal.mealType, foodId, recipeId,
                                    meal.grams, meal.servings, day.toLong(),
                                )
                            }
                        } else {
                            skippedFoodItems++
                        }
                    }
                }

                backup.foodLog.forEach { entry ->
                    val recipeId = entry.recipeRef?.let { recipeIdByRef[it] }
                        ?: entry.recipeName?.let { recipeIdByName[it] }
                    val foodId = if (recipeId == null) {
                        resolveFood(entry.barcode, entry.customRef, foodIdByRef)
                    } else {
                        null
                    }
                    // La tabla exige exactamente uno de los dos.
                    if (foodId != null || recipeId != null) {
                        db.nutritionQueries.insertFoodLogEntry(
                            entry.date, entry.mealType, foodId, recipeId,
                            entry.grams, entry.servings, entry.loggedAt,
                        )
                        insertedFoodLog++
                    } else {
                        skippedFoodItems++
                    }
                }
            }

            ImportSummary(
                routines = backup.routines.size,
                sessions = backup.sessions.size,
                measurements = backup.measurements.size,
                foodLogEntries = insertedFoodLog,
                recipes = backup.recipes.size,
                dietPlans = backup.dietPlans.size,
                skippedExercises = skippedExercises,
                skippedFoodItems = skippedFoodItems,
            )
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * Se queda con el primero, como hacía la consulta con LIMIT 1: en copias
     * viejas la ref era el nombre y podía repetirse. (`putIfAbsent` no existe en
     * el código común, solo en la JVM.)
     */
    private fun <K, V> MutableMap<K, V>.claim(key: K, value: V) {
        if (key !in this) this[key] = value
    }

    private fun resolveExercise(ref: BackupExerciseRef, byRef: Map<String, Long>): Long? = when {
        ref.wgerId != null -> db.catalogQueries.selectExerciseIdByWgerId(ref.wgerId).executeAsOneOrNull()
        // El mapa cubre las copias nuevas y las viejas (donde la ref era el nombre);
        // la consulta queda de red de seguridad para ficheros retocados a mano.
        ref.customRef != null -> byRef[ref.customRef]
            ?: q.selectExerciseIdByName(ref.customRef).executeAsOneOrNull()
        else -> null
    }

    private fun resolveFood(barcode: String?, customRef: String?, byRef: Map<String, Long>): Long? = when {
        barcode != null -> db.nutritionQueries.selectFoodByBarcode(barcode).executeAsOneOrNull()?.id
        customRef != null -> byRef[customRef]
            ?: q.selectFoodIdByName(customRef).executeAsOneOrNull()
        else -> null
    }

    // --- CSV ------------------------------------------------------------------

    /** Historial de series en CSV, para abrirlo en una hoja de cálculo. */
    suspend fun exportSetsCsv(): String = withContext(dispatcher) {
        buildString {
            appendLine("fecha;ejercicio;serie;peso_kg;repeticiones;segundos;calentamiento")
            q.selectAllSessions().executeAsList().forEach { session ->
                q.selectSetsForSessionWithRef(session.id).executeAsList().forEach { set ->
                    appendLine(
                        listOf(
                            session.date,
                            set.nameEs.csv(),
                            set.setNumber,
                            set.weightKg.csvNum(),
                            set.reps?.toString().orEmpty(),
                            set.timeSeconds?.toString().orEmpty(),
                            if (set.isWarmup == 1L) "sí" else "no",
                        ).joinToString(";"),
                    )
                }
            }
        }
    }

    suspend fun exportMeasurementsCsv(): String = withContext(dispatcher) {
        buildString {
            appendLine("fecha;peso_kg;cuello_cm;cintura_cm;cadera_cm;grasa_pct;notas")
            db.profileQueries.selectMeasurements().executeAsList().forEach {
                appendLine(
                    listOf(
                        it.date,
                        it.weightKg.csvNum(),
                        it.neckCm.csvNum(),
                        it.waistCm.csvNum(),
                        it.hipCm.csvNum(),
                        it.bodyFatPercent.csvNum(),
                        it.notes.orEmpty().csv(),
                    ).joinToString(";"),
                )
            }
        }
    }

    /** Separador `;` y coma decimal es lo que espera Excel en español. */
    private fun Double?.csvNum(): String = this?.toString()?.replace('.', ',').orEmpty()

    private fun String.csv(): String =
        if (contains(';') || contains('"') || contains('\n')) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
