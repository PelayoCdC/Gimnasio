package com.pelayo.gimnasio.data.backup

import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.util.nowIsoString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class ImportSummary(
    val routines: Int,
    val sessions: Int,
    val measurements: Int,
    val foodLogEntries: Int,
    val recipes: Int,
    val dietPlans: Int,
    /** Ejercicios del backup que no están en el catálogo local y se han omitido. */
    val skippedExercises: Int,
)

/**
 * Exporta e importa todos los datos del usuario en JSON.
 *
 * Los ejercicios y alimentos del catálogo se referencian por su identificador
 * externo (wgerId, código de barras) en vez de por el id local, porque los ids
 * de la base local no coinciden entre instalaciones.
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
        json.encodeToString(BackupData.serializer(), buildBackup())
    }

    private fun buildBackup(): BackupData {
        val customExercises = q.selectCustomExercises().executeAsList().map { exercise ->
            val muscles = db.catalogQueries.selectMusclesForExercise(exercise.id).executeAsList()
            BackupCustomExercise(
                ref = exercise.nameEs,
                nameEs = exercise.nameEs,
                descriptionEs = exercise.descriptionEs,
                metValue = exercise.metValue,
                exerciseType = exercise.exerciseType,
                primaryMuscleIds = muscles.filter { it.role == "PRIMARY" }.map { it.id },
                secondaryMuscleIds = muscles.filter { it.role == "SECONDARY" }.map { it.id },
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
                                    customRef = row.nameEs.takeIf { row.isCustom == 1L },
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
                            customRef = row.nameEs.takeIf { row.isCustom == 1L },
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

        val customFoods = q.selectCustomFoods().executeAsList().map { food ->
            BackupFood(
                ref = food.name,
                name = food.name,
                brand = food.brand,
                kcalPer100 = food.kcalPer100,
                proteinPer100 = food.proteinPer100,
                carbsPer100 = food.carbsPer100,
                fatPer100 = food.fatPer100,
                fiberPer100 = food.fiberPer100,
                servingSizeG = food.servingSizeG,
            )
        }

        val recipes = db.nutritionQueries.selectAllRecipes().executeAsList().map { recipe ->
            BackupRecipe(
                name = recipe.name,
                servings = recipe.servings.toInt(),
                notes = recipe.notes,
                ingredients = db.nutritionQueries.selectIngredientsForRecipe(recipe.id)
                    .executeAsList()
                    .map { ingredient ->
                        val food = q.selectFoodRef(ingredient.foodId).executeAsOne()
                        BackupRecipeIngredient(
                            barcode = food.offBarcode,
                            customRef = food.name.takeIf { food.isCustom == 1L },
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
                customRef = food?.name?.takeIf { food.isCustom == 1L },
                recipeName = entry.recipeId?.let { q.selectRecipeNameById(it).executeAsOneOrNull() },
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
                        customRef = meal.foodName?.takeIf { meal.isCustom == 1L },
                        recipeName = meal.recipeName,
                        grams = meal.grams,
                        servings = meal.servings,
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
            recipes = recipes,
            foodLog = foodLog,
            dietPlans = dietPlans,
        )
    }

    // --- Importación ---------------------------------------------------------

    /**
     * Restaura un backup **sustituyendo** los datos del usuario. El catálogo de
     * wger se conserva; los ejercicios del backup que no estén en él se omiten
     * y se cuentan en el resumen.
     */
    suspend fun importFromJson(content: String): Result<ImportSummary> = withContext(dispatcher) {
        runCatching {
            val backup = json.decodeFromString(BackupData.serializer(), content)
            require(backup.version <= BackupData.CURRENT_VERSION) {
                "El backup es de una versión más nueva de la app (${backup.version})."
            }

            var skipped = 0
            db.transaction {
                q.deleteAllUserData()

                backup.profile?.let {
                    db.profileQueries.upsertProfile(
                        it.name, it.sex, it.birthDate, it.heightCm,
                        it.activityFactor, it.goal, it.targetKcalOverride,
                    )
                }

                backup.measurements.forEach {
                    db.profileQueries.insertMeasurement(
                        it.date, it.weightKg, it.neckCm, it.waistCm, it.hipCm,
                        it.bodyFatPercent, it.notes,
                    )
                }

                // Los ejercicios propios se recrean primero: las rutinas los referencian.
                backup.customExercises.forEach { exercise ->
                    db.catalogQueries.insertCustomExercise(
                        exercise.nameEs, exercise.descriptionEs, exercise.metValue, exercise.exerciseType,
                    )
                    val id = db.catalogQueries.lastInsertRowId().executeAsOne()
                    exercise.primaryMuscleIds.forEach {
                        db.catalogQueries.insertExerciseMuscle(id, it, "PRIMARY")
                    }
                    exercise.secondaryMuscleIds.forEach {
                        db.catalogQueries.insertExerciseMuscle(id, it, "SECONDARY")
                    }
                }

                backup.customFoods.forEach { food ->
                    db.nutritionQueries.insertCustomFood(
                        food.name, food.brand, food.kcalPer100, food.proteinPer100,
                        food.carbsPer100, food.fatPer100, food.fiberPer100, food.servingSizeG,
                    )
                }

                backup.routines.forEach { routine ->
                    db.routinesQueries.insertRoutine(
                        routine.name, routine.description, routine.goal, routine.createdAt,
                        if (routine.isActive) 1 else 0, if (routine.isGenerated) 1 else 0,
                    )
                    val routineId = db.catalogQueries.lastInsertRowId().executeAsOne()
                    routine.days.forEach { day ->
                        db.routinesQueries.insertRoutineDay(
                            routineId, day.dayIndex.toLong(), day.name, day.weekday?.toLong(),
                        )
                        val dayId = db.catalogQueries.lastInsertRowId().executeAsOne()
                        day.exercises.forEach { item ->
                            val exerciseId = resolveExercise(item.exercise)
                            if (exerciseId == null) {
                                skipped++
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

                backup.recipes.forEach { recipe ->
                    db.nutritionQueries.insertRecipe(recipe.name, recipe.servings.toLong(), recipe.notes)
                    val recipeId = db.nutritionQueries.lastInsertRowId().executeAsOne()
                    recipe.ingredients.forEach { ingredient ->
                        resolveFood(ingredient.barcode, ingredient.customRef)?.let { foodId ->
                            db.nutritionQueries.insertRecipeIngredient(recipeId, foodId, ingredient.grams)
                        }
                    }
                }

                backup.sessions.forEach { session ->
                    db.workoutsQueries.insertSession(
                        routineId = null,
                        routineDayId = null,
                        date = session.date,
                        startTime = session.startTime,
                        durationMinutes = session.durationMinutes?.toLong(),
                        kcalBurned = session.kcalBurned,
                        notes = session.notes ?: session.dayName,
                        isCompleted = if (session.isCompleted) 1 else 0,
                    )
                    val sessionId = db.catalogQueries.lastInsertRowId().executeAsOne()
                    session.sets.forEach { set ->
                        val exerciseId = resolveExercise(set.exercise)
                        if (exerciseId == null) {
                            skipped++
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
                        val recipeId = meal.recipeName?.let {
                            q.selectRecipeIdByName(it).executeAsOneOrNull()
                        }
                        val foodId = if (recipeId == null) {
                            resolveFood(meal.barcode, meal.customRef)
                        } else {
                            null
                        }
                        // La tabla exige exactamente uno de los dos.
                        if (foodId != null || recipeId != null) {
                            db.nutritionQueries.insertDietPlanMeal(
                                planId, meal.mealType, foodId, recipeId, meal.grams, meal.servings,
                            )
                        }
                    }
                }

                backup.foodLog.forEach { entry ->
                    val foodId = if (entry.recipeName == null) {
                        resolveFood(entry.barcode, entry.customRef)
                    } else {
                        null
                    }
                    val recipeId = entry.recipeName?.let {
                        q.selectRecipeIdByName(it).executeAsOneOrNull()
                    }
                    // La tabla exige exactamente uno de los dos.
                    if (foodId != null || recipeId != null) {
                        db.nutritionQueries.insertFoodLogEntry(
                            entry.date, entry.mealType, foodId, recipeId,
                            entry.grams, entry.servings, entry.loggedAt,
                        )
                    }
                }
            }

            ImportSummary(
                routines = backup.routines.size,
                sessions = backup.sessions.size,
                measurements = backup.measurements.size,
                foodLogEntries = backup.foodLog.size,
                recipes = backup.recipes.size,
                dietPlans = backup.dietPlans.size,
                skippedExercises = skipped,
            )
        }
    }

    private fun resolveExercise(ref: BackupExerciseRef): Long? = when {
        ref.wgerId != null -> db.catalogQueries.selectExerciseIdByWgerId(ref.wgerId).executeAsOneOrNull()
        ref.customRef != null -> q.selectExerciseIdByName(ref.customRef).executeAsOneOrNull()
        else -> null
    }

    private fun resolveFood(barcode: String?, customRef: String?): Long? = when {
        barcode != null -> db.nutritionQueries.selectFoodByBarcode(barcode).executeAsOneOrNull()?.id
        customRef != null -> q.selectFoodIdByName(customRef).executeAsOneOrNull()
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
                            set.weightKg?.toString().orEmpty(),
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
                        it.weightKg,
                        it.neckCm?.toString().orEmpty(),
                        it.waistCm?.toString().orEmpty(),
                        it.hipCm?.toString().orEmpty(),
                        it.bodyFatPercent?.toString().orEmpty(),
                        it.notes.orEmpty().csv(),
                    ).joinToString(";"),
                )
            }
        }
    }

    /** Separador `;` y coma decimal es lo que espera Excel en español. */
    private fun String.csv(): String =
        if (contains(';') || contains('"') || contains('\n')) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
