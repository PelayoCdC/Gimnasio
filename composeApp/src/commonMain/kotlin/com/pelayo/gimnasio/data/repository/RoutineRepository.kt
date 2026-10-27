package com.pelayo.gimnasio.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pelayo.gimnasio.data.backup.RoutineMarkdown
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
import com.pelayo.gimnasio.domain.model.RoutineImportResult
import com.pelayo.gimnasio.domain.model.RoutineSummary
import com.pelayo.gimnasio.domain.model.TrainingPrescription
import com.pelayo.gimnasio.util.normalizeForSearch
import com.pelayo.gimnasio.util.nowIsoString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoutineRepository(
    private val db: GimnasioDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.routinesQueries

    fun routines(): Flow<List<RoutineSummary>> =
        q.selectAllRoutines().asFlow().mapToList(dispatcher).map { rows ->
            val counts = q.countRoutineContents().executeAsList().associateBy { it.routineId }
            rows.map { routine ->
                RoutineSummary(
                    id = routine.id,
                    name = routine.name,
                    description = routine.description,
                    goal = RoutineGoal.valueOf(routine.goal),
                    isActive = routine.isActive == 1L,
                    dayCount = counts[routine.id]?.dayCount?.toInt() ?: 0,
                    exerciseCount = counts[routine.id]?.exerciseCount?.toInt() ?: 0,
                )
            }
        }
            // Sin esto, el map de arriba (que consulta la DB) corre en el contexto
            // del colector, que es el hilo principal.
            .flowOn(dispatcher)

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
                insertExerciseRow(dayId, exerciseId, position, p)
            }
        }

    /**
     * Inserta la fila de un ejercicio con su prescripción. Para llamar dentro de
     * una transacción ya abierta. Si el ejercicio ya no existe (borrado con la
     * pantalla abierta), no inserta nada en vez de reventar.
     */
    private fun insertExerciseRow(dayId: Long, exerciseId: Long, position: Long, p: TrainingPrescription) {
        val exercise = db.catalogQueries.selectExerciseById(exerciseId).executeAsOneOrNull() ?: return
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

    /**
     * Aplica de una vez el orden que dejó el arrastre: una escritura por fila en
     * una sola transacción, en vez de un baile de ±1 por cada posición cruzada.
     * Los ids que no estén en la lista (carrera con un borrado) se ignoran.
     */
    suspend fun reorderRoutineExercises(dayId: Long, orderedIds: List<Long>) =
        withContext(dispatcher) {
            db.transaction {
                val current = q.selectExercisesForDay(dayId).executeAsList().map { it.id }.toSet()
                orderedIds.filter { it in current }.forEachIndexed { position, id ->
                    q.updateRoutineExercisePosition(position.toLong(), id)
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

    /**
     * Persiste una rutina propuesta por el generador y devuelve su id. Todo en
     * una transacción: un fallo (o cerrar la pantalla) a mitad no debe dejar una
     * rutina a medio construir que haya que limpiar a mano.
     */
    suspend fun saveGenerated(generated: GeneratedRoutine): Long = withContext(dispatcher) {
        db.transactionWithResult {
            q.insertRoutine(
                generated.name, generated.description, generated.goal.name,
                nowIsoString(), 0, 1,
            )
            val routineId = db.catalogQueries.lastInsertRowId().executeAsOne()
            if (q.selectAllRoutines().executeAsList().size == 1) q.setActiveRoutine(routineId)
            generated.days.forEachIndexed { index, day ->
                q.insertRoutineDay(routineId, index.toLong(), day.name, day.weekday?.toLong())
                val dayId = db.catalogQueries.lastInsertRowId().executeAsOne()
                day.exerciseIds.forEachIndexed { position, exerciseId ->
                    insertExerciseRow(dayId, exerciseId, position.toLong(), generated.prescription)
                }
            }
            routineId
        }
    }

    /**
     * Pone las mismas series a todos los ejercicios de la rutina. Es un ajuste de
     * brocha gorda a propósito: se elige de una vez y luego se retoca el ejercicio
     * que lo necesite.
     */
    suspend fun setSetsForRoutine(routineId: Long, sets: Int) = withContext(dispatcher) {
        db.transaction {
            q.selectDaysForRoutine(routineId).executeAsList().forEach { day ->
                q.updateSetsForDay(sets.toLong(), day.id)
            }
        }
    }

    /** La rutina en Markdown, lista para compartir o para dársela a un agente. */
    suspend fun exportRoutineMarkdown(routineId: Long): String? = withContext(dispatcher) {
        routineDetail(routineId)?.let { RoutineMarkdown.export(it) }
    }

    /**
     * Crea una rutina a partir del texto. Nunca sobreescribe una existente: si el
     * nombre choca, se desambigua, porque lo que llega de fuera no debería poder
     * pisar lo que el usuario ya tiene.
     *
     * Los ejercicios se reconocen por nombre contra el catálogo local (normalizado,
     * en español o en inglés, y con una pasada por "contiene" para los nombres
     * ligeramente distintos). Lo que no se reconoce se salta y se cuenta.
     */
    suspend fun importRoutineMarkdown(text: String): RoutineImportResult? = withContext(dispatcher) {
        val parsed = RoutineMarkdown.parse(text) ?: return@withContext null

        val catalog = db.catalogQueries.selectExerciseNames().executeAsList()
        val byName = mutableMapOf<String, ExerciseMatch>()
        // El primero que reclama un nombre se lo queda: `putIfAbsent` no existe en
        // el código común, solo en la JVM.
        fun claim(name: String, match: ExerciseMatch) {
            if (name.isNotBlank() && name !in byName) byName[name] = match
        }
        catalog.forEach { row ->
            val match = ExerciseMatch(row.id, row.exerciseType == ExerciseType.TIME.name)
            claim(row.searchName ?: normalizeForSearch(row.nameEs), match)
            claim(normalizeForSearch(row.nameEs), match)
            row.nameEn?.let { claim(normalizeForSearch(it), match) }
        }

        val prescription = TrainingPrescription.forGoal(parsed.goal)
        val skipped = mutableListOf<String>()
        var imported = 0

        // Una sola transacción para toda la rutina: un fallo a mitad no debe
        // dejar media rutina importada.
        val routineId = db.transactionWithResult {
            val existing = q.selectAllRoutines().executeAsList().map { it.name }.toSet()
            q.insertRoutine(
                uniqueName(parsed.name, existing), parsed.description, parsed.goal.name,
                nowIsoString(), 0, 0,
            )
            val routineId = db.catalogQueries.lastInsertRowId().executeAsOne()
            if (q.selectAllRoutines().executeAsList().size == 1) q.setActiveRoutine(routineId)

            parsed.days.forEachIndexed { dayIndex, day ->
                q.insertRoutineDay(routineId, dayIndex.toLong(), day.name, day.weekday?.toLong())
                val dayId = db.catalogQueries.lastInsertRowId().executeAsOne()
                var position = 0L
                day.exercises.forEach { exercise ->
                    val match = findExercise(exercise.name, byName)
                    if (match == null) {
                        skipped += exercise.name
                        return@forEach
                    }
                    val isTimed = match.isTimed || exercise.timeSeconds != null
                    val sets = exercise.sets ?: prescription.sets
                    val repsMin = exercise.repsMin ?: prescription.repsMin
                    val repsMax = exercise.repsMax ?: prescription.repsMax
                    q.insertRoutineExercise(
                        routineDayId = dayId,
                        exerciseId = match.id,
                        position = position,
                        targetSets = sets.toLong(),
                        targetRepsMin = if (isTimed) null else repsMin.toLong(),
                        targetRepsMax = if (isTimed) null else repsMax.toLong(),
                        targetTimeSeconds = if (isTimed) {
                            (exercise.timeSeconds ?: 60).toLong()
                        } else {
                            null
                        },
                        targetWeight = exercise.weightKg,
                        restSeconds = (exercise.restSeconds ?: prescription.restSeconds).toLong(),
                        notes = exercise.notes,
                    )
                    position++
                    imported++
                }
            }
            routineId
        }

        RoutineImportResult(
            routineId = routineId,
            name = parsed.name,
            days = parsed.days.size,
            exercises = imported,
            skippedExercises = skipped,
        )
    }

    private data class ExerciseMatch(val id: Long, val isTimed: Boolean)

    /**
     * Busca por nombre exacto normalizado y, si no lo encuentra, por el nombre
     * más parecido que lo contenga: un agente puede escribir "Press banca" donde el
     * catálogo dice "Press de banca con barra".
     */
    private fun findExercise(name: String, byName: Map<String, ExerciseMatch>): ExerciseMatch? {
        val normalized = normalizeForSearch(name)
        if (normalized.isBlank()) return null
        byName[normalized]?.let { return it }
        // De los candidatos por "contiene" gana el de longitud más parecida (el
        // más específico), y se exige un solapamiento mínimo del 60 %: "Remo con
        // barra a una mano" no debe caer en un escueto "Remo".
        return byName.entries
            .filter { it.key.contains(normalized) || normalized.contains(it.key) }
            .filter { minOf(it.key.length, normalized.length) * 10 >= maxOf(it.key.length, normalized.length) * 6 }
            .minByOrNull { kotlin.math.abs(it.key.length - normalized.length) }
            ?.value
    }

    private fun uniqueName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        var index = 2
        while ("$name ($index)" in existing) index++
        return "$name ($index)"
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
