package com.pelayo.gimnasio.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.calc.BalanceReport
import com.pelayo.gimnasio.domain.calc.GeneratedRoutine
import com.pelayo.gimnasio.domain.calc.MuscleBalanceAnalyzer
import com.pelayo.gimnasio.domain.calc.MuscleVolumeEntry
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.MuscleRole
import com.pelayo.gimnasio.domain.model.RoutineDayInfo
import com.pelayo.gimnasio.domain.model.RoutineDetail
import com.pelayo.gimnasio.domain.model.RoutineExerciseItem
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.RoutineSummary
import com.pelayo.gimnasio.domain.model.TrainingPrescription
import com.pelayo.gimnasio.util.nowIsoString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoutineRepository(
    private val db: GimnasioDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.routinesQueries

    fun routines(): Flow<List<RoutineSummary>> =
        q.selectAllRoutines().asFlow().mapToList(dispatcher).map { rows ->
            rows.map { routine ->
                val days = q.selectDaysForRoutine(routine.id).executeAsList()
                RoutineSummary(
                    id = routine.id,
                    name = routine.name,
                    description = routine.description,
                    goal = RoutineGoal.valueOf(routine.goal),
                    isActive = routine.isActive == 1L,
                    dayCount = days.size,
                    exerciseCount = days.sumOf { q.selectExercisesForDay(it.id).executeAsList().size },
                )
            }
        }

    suspend fun createRoutine(
        name: String,
        description: String?,
        goal: RoutineGoal,
        isGenerated: Boolean = false,
    ): Long = withContext(dispatcher) {
        db.transactionWithResult {
            q.insertRoutine(name, description, goal.name, nowIsoString(), 0, if (isGenerated) 1 else 0)
            val id = db.catalogQueries.lastInsertRowId().executeAsOne()
            // La primera rutina creada pasa a ser la activa automáticamente.
            if (q.selectAllRoutines().executeAsList().size == 1) q.setActiveRoutine(id)
            id
        }
    }

    suspend fun updateRoutine(id: Long, name: String, description: String?, goal: RoutineGoal) =
        withContext(dispatcher) { q.updateRoutine(name, description, goal.name, id) }

    suspend fun deleteRoutine(id: Long) = withContext(dispatcher) { q.deleteRoutine(id) }

    suspend fun setActive(id: Long) = withContext(dispatcher) { q.setActiveRoutine(id) }

    suspend fun addDay(routineId: Long, name: String, weekday: Int?): Long = withContext(dispatcher) {
        db.transactionWithResult {
            val nextIndex = q.selectDaysForRoutine(routineId).executeAsList().size.toLong()
            q.insertRoutineDay(routineId, nextIndex, name, weekday?.toLong())
            db.catalogQueries.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun updateDay(dayId: Long, name: String, weekday: Int?, dayIndex: Int) =
        withContext(dispatcher) { q.updateRoutineDay(name, weekday?.toLong(), dayIndex.toLong(), dayId) }

    suspend fun deleteDay(dayId: Long) = withContext(dispatcher) { q.deleteRoutineDay(dayId) }

    /** Añade un ejercicio al día usando la prescripción por defecto del objetivo de la rutina. */
    suspend fun addExerciseToDay(dayId: Long, exerciseId: Long, goal: RoutineGoal) =
        addExerciseToDay(dayId, exerciseId, TrainingPrescription.forGoal(goal))

    /** Añade un ejercicio al día con una prescripción concreta (la del generador, ajustada al nivel). */
    suspend fun addExerciseToDay(dayId: Long, exerciseId: Long, p: TrainingPrescription) =
        withContext(dispatcher) {
            db.transaction {
                val position = q.selectExercisesForDay(dayId).executeAsList().size.toLong()
                val exercise = db.catalogQueries.selectExerciseById(exerciseId).executeAsOne()
                val isTimed = exercise.exerciseType == ExerciseType.TIME.name
                q.insertRoutineExercise(
                    routineDayId = dayId,
                    exerciseId = exerciseId,
                    position = position,
                    targetSets = p.sets.toLong(),
                    targetRepsMin = if (isTimed) null else p.repsMin.toLong(),
                    targetRepsMax = if (isTimed) null else p.repsMax.toLong(),
                    targetTimeSeconds = if (isTimed) 60L else null,
                    targetWeight = null,
                    restSeconds = p.restSeconds.toLong(),
                    notes = null,
                )
            }
        }

    suspend fun updateRoutineExercise(
        id: Long,
        targetSets: Int,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
        targetTimeSeconds: Int?,
        targetWeight: Double?,
        restSeconds: Int,
        notes: String?,
        position: Int,
    ) = withContext(dispatcher) {
        q.updateRoutineExercise(
            targetSets = targetSets.toLong(),
            targetRepsMin = targetRepsMin?.toLong(),
            targetRepsMax = targetRepsMax?.toLong(),
            targetTimeSeconds = targetTimeSeconds?.toLong(),
            targetWeight = targetWeight,
            restSeconds = restSeconds.toLong(),
            notes = notes,
            position = position.toLong(),
            id = id,
        )
    }

    /**
     * Sustituye el ejercicio de una fila de rutina por otro, conservando su
     * posición, sus series y su descanso. Las repeticiones se rehacen desde el
     * objetivo porque el ejercicio nuevo puede ser de tiempo y el viejo de
     * repeticiones (o al revés), y entonces los objetivos no encajarían.
     */
    suspend fun replaceRoutineExercise(
        routineExerciseId: Long,
        newExerciseId: Long,
        goal: RoutineGoal,
    ) = withContext(dispatcher) {
        db.transaction {
            val exercise = db.catalogQueries.selectExerciseById(newExerciseId).executeAsOneOrNull()
                ?: return@transaction
            val p = TrainingPrescription.forGoal(goal)
            val isTimed = exercise.exerciseType == ExerciseType.TIME.name
            q.swapRoutineExercise(
                exerciseId = newExerciseId,
                targetRepsMin = if (isTimed) null else p.repsMin.toLong(),
                targetRepsMax = if (isTimed) null else p.repsMax.toLong(),
                targetTimeSeconds = if (isTimed) 60L else null,
                id = routineExerciseId,
            )
        }
    }

    suspend fun removeRoutineExercise(id: Long) = withContext(dispatcher) {
        db.transaction {
            val dayId = q.selectRoutineDayIdForExercise(id).executeAsOneOrNull()
            q.deleteRoutineExercise(id)
            // Sin esto quedarían huecos en las posiciones y el reordenado se descuadraría.
            dayId?.let { renumber(it) }
        }
    }

    /**
     * Mueve un ejercicio una posición arriba (-1) o abajo (+1) dentro de su día.
     * No hace nada si ya está en el extremo.
     */
    suspend fun moveRoutineExercise(routineExerciseId: Long, delta: Int) = withContext(dispatcher) {
        db.transaction {
            val dayId = q.selectRoutineDayIdForExercise(routineExerciseId).executeAsOneOrNull()
                ?: return@transaction
            val ordered = q.selectExercisesForDay(dayId).executeAsList()
            val index = ordered.indexOfFirst { it.id == routineExerciseId }
            val target = index + delta
            if (index == -1 || target !in ordered.indices) return@transaction

            val reordered = ordered.toMutableList()
            reordered.add(target, reordered.removeAt(index))
            reordered.forEachIndexed { position, item ->
                q.updateRoutineExercisePosition(position.toLong(), item.id)
            }
        }
    }

    /** Reasigna posiciones 0..n-1 respetando el orden actual. */
    private fun renumber(dayId: Long) {
        q.selectExercisesForDay(dayId).executeAsList().forEachIndexed { position, item ->
            q.updateRoutineExercisePosition(position.toLong(), item.id)
        }
    }

    suspend fun routineDetail(routineId: Long): RoutineDetail? = withContext(dispatcher) {
        val routine = q.selectRoutineById(routineId).executeAsOneOrNull() ?: return@withContext null
        val days = q.selectDaysForRoutine(routineId).executeAsList().map { day ->
            RoutineDayInfo(
                id = day.id,
                routineId = day.routineId,
                dayIndex = day.dayIndex.toInt(),
                name = day.name,
                weekday = day.weekday?.toInt(),
                exercises = q.selectExercisesForDay(day.id).executeAsList().map { row ->
                    RoutineExerciseItem(
                        id = row.id,
                        exerciseId = row.exerciseId,
                        exerciseName = row.nameEs,
                        imageUrl = row.mainImageUrl,
                        exerciseType = ExerciseType.valueOf(row.exerciseType),
                        metValue = row.metValue,
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
        }
        RoutineDetail(
            id = routine.id,
            name = routine.name,
            description = routine.description,
            goal = RoutineGoal.valueOf(routine.goal),
            isActive = routine.isActive == 1L,
            days = days,
        )
    }

    /**
     * Copia una rutina entera con sus días y ejercicios. La copia nace inactiva,
     * para no desbancar a la que se esté siguiendo.
     */
    suspend fun duplicateRoutine(routineId: Long): Long? = withContext(dispatcher) {
        db.transactionWithResult {
            val original = q.selectRoutineById(routineId).executeAsOneOrNull()
                ?: return@transactionWithResult null

            q.insertRoutine(
                name = nextCopyName(original.name),
                description = original.description,
                goal = original.goal,
                createdAt = nowIsoString(),
                isActive = 0,
                isGenerated = original.isGenerated,
            )
            val newRoutineId = db.catalogQueries.lastInsertRowId().executeAsOne()

            q.selectDaysForRoutine(routineId).executeAsList().forEach { day ->
                q.insertRoutineDay(newRoutineId, day.dayIndex, day.name, day.weekday)
                val newDayId = db.catalogQueries.lastInsertRowId().executeAsOne()

                q.selectExercisesForDay(day.id).executeAsList().forEach { item ->
                    q.insertRoutineExercise(
                        routineDayId = newDayId,
                        exerciseId = item.exerciseId,
                        position = item.position,
                        targetSets = item.targetSets,
                        targetRepsMin = item.targetRepsMin,
                        targetRepsMax = item.targetRepsMax,
                        targetTimeSeconds = item.targetTimeSeconds,
                        targetWeight = item.targetWeight,
                        restSeconds = item.restSeconds,
                        notes = item.notes,
                    )
                }
            }
            newRoutineId
        }
    }

    /**
     * Copia un día completo, con sus ejercicios y sus series objetivo, al final de
     * otra rutina. Devuelve el id del día nuevo, o null si el día o la rutina de
     * destino ya no existen.
     *
     * El día de la semana solo se conserva si en el destino está libre: dos días
     * el mismo martes harían que los recordatorios avisaran dos veces.
     */
    suspend fun copyDayToRoutine(dayId: Long, targetRoutineId: Long): Long? = withContext(dispatcher) {
        db.transactionWithResult {
            val day = q.selectRoutineDayById(dayId).executeAsOneOrNull()
                ?: return@transactionWithResult null
            q.selectRoutineById(targetRoutineId).executeAsOneOrNull()
                ?: return@transactionWithResult null

            val existing = q.selectDaysForRoutine(targetRoutineId).executeAsList()
            val weekday = day.weekday?.takeIf { candidate -> existing.none { it.weekday == candidate } }

            q.insertRoutineDay(
                routineId = targetRoutineId,
                dayIndex = existing.size.toLong(),
                name = nextCopyDayName(day.name, existing.map { it.name }.toSet()),
                weekday = weekday,
            )
            val newDayId = db.catalogQueries.lastInsertRowId().executeAsOne()

            q.selectExercisesForDay(dayId).executeAsList().forEach { item ->
                q.insertRoutineExercise(
                    routineDayId = newDayId,
                    exerciseId = item.exerciseId,
                    position = item.position,
                    targetSets = item.targetSets,
                    targetRepsMin = item.targetRepsMin,
                    targetRepsMax = item.targetRepsMax,
                    targetTimeSeconds = item.targetTimeSeconds,
                    targetWeight = item.targetWeight,
                    restSeconds = item.restSeconds,
                    notes = item.notes,
                )
            }
            newDayId
        }
    }

    /** Solo desambigua si el destino ya tiene un día con ese nombre. */
    private fun nextCopyDayName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val base = "$name (copia)"
        if (base !in existing) return base
        var index = 2
        while ("$name (copia $index)" in existing) index++
        return "$name (copia $index)"
    }

    /** "Torso" → "Torso (copia)", y si ya existe → "Torso (copia 2)". */
    private fun nextCopyName(originalName: String): String {
        val existing = q.selectAllRoutines().executeAsList().map { it.name }.toSet()
        val base = "$originalName (copia)"
        if (base !in existing) return base
        var index = 2
        while ("$originalName (copia $index)" in existing) index++
        return "$originalName (copia $index)"
    }

    /** Detalle de la rutina marcada como activa, si hay alguna. */
    suspend fun activeRoutineDetail(): RoutineDetail? = withContext(dispatcher) {
        val active = q.selectActiveRoutine().executeAsOneOrNull() ?: return@withContext null
        routineDetail(active.id)
    }

    suspend fun isCatalogEmpty(): Boolean = withContext(dispatcher) {
        db.catalogQueries.countExercises().executeAsOne() == 0L
    }

    /** Persiste una rutina propuesta por el generador y devuelve su id. */
    suspend fun saveGenerated(generated: GeneratedRoutine): Long = withContext(dispatcher) {
        val routineId = createRoutine(
            name = generated.name,
            description = generated.description,
            goal = generated.goal,
            isGenerated = true,
        )
        generated.days.forEachIndexed { index, day ->
            val dayId = addDay(routineId, day.name, weekday = null)
            day.exerciseIds.forEach { exerciseId ->
                addExerciseToDay(dayId, exerciseId, generated.prescription)
            }
            // dayIndex ya lo asigna addDay por orden de inserción.
            check(index >= 0)
        }
        routineId
    }

    /** Volumen semanal de la rutina agregado por grupo muscular, con sus avisos. */
    suspend fun balanceReport(routineId: Long): BalanceReport = withContext(dispatcher) {
        val entries = q.selectRoutineMuscleVolume(routineId).executeAsList().map { row ->
            MuscleVolumeEntry(
                sets = row.targetSets.toInt(),
                role = MuscleRole.valueOf(row.role),
                muscleGroupId = row.muscleGroupId,
            )
        }
        MuscleBalanceAnalyzer.analyze(entries)
    }
}
