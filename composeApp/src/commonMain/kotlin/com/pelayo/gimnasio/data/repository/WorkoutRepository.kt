package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.calc.EnergyCalculator
import com.pelayo.gimnasio.domain.calc.ExerciseFormatClassifier
import com.pelayo.gimnasio.domain.calc.MuscleBalanceAnalyzer
import com.pelayo.gimnasio.domain.calc.OneRepMaxCalculator
import com.pelayo.gimnasio.domain.calc.PastPerformance
import com.pelayo.gimnasio.domain.calc.ProgressionAdvisor
import com.pelayo.gimnasio.domain.calc.ProgressionSuggestion
import com.pelayo.gimnasio.domain.model.ActiveWorkout
import com.pelayo.gimnasio.domain.model.CalendarSession
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.PersonalRecords
import com.pelayo.gimnasio.domain.model.ProgressPoint
import com.pelayo.gimnasio.domain.calc.TimeProgressionSuggestion
import com.pelayo.gimnasio.domain.model.SessionExerciseSummary
import com.pelayo.gimnasio.domain.model.SessionRecord
import com.pelayo.gimnasio.domain.model.SetLogItem
import com.pelayo.gimnasio.util.oneDecimal
import com.pelayo.gimnasio.domain.model.WorkoutExerciseProgress
import com.pelayo.gimnasio.domain.model.WorkoutSummary
import com.pelayo.gimnasio.util.nowIsoString
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.withContext

class WorkoutRepository(
    private val db: GimnasioDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.workoutsQueries
    private val routines get() = db.routinesQueries

    /**
     * Abre una sesión para un día de rutina. Si ya hay una sesión sin terminar
     * de ese mismo día hoy, la reutiliza en vez de duplicarla.
     */
    suspend fun startSession(routineDayId: Long, date: LocalDate = today()): Long = withContext(dispatcher) {
        db.transactionWithResult {
            // Si el día ya no existe (rutina borrada con la pantalla abierta),
            // la sesión se abre como entrenamiento libre en vez de reventar.
            val day = routines.selectRoutineDayById(routineDayId).executeAsOneOrNull()
            val existing = q.selectSessionsForDate(date.toString()).executeAsList()
                .firstOrNull { it.routineDayId == routineDayId && it.isCompleted == 0L }
            existing?.id ?: run {
                q.insertSession(
                    routineId = day?.routineId,
                    routineDayId = day?.let { routineDayId },
                    date = date.toString(),
                    startTime = nowIsoString(),
                    durationMinutes = null,
                    kcalBurned = null,
                    notes = null,
                    isCompleted = 0,
                )
                db.catalogQueries.lastInsertRowId().executeAsOne()
            }
        }
    }

    suspend fun activeWorkout(sessionId: Long): ActiveWorkout? = withContext(dispatcher) {
        val session = q.selectSessionById(sessionId).executeAsOneOrNull() ?: return@withContext null
        val dayId = session.routineDayId
        val day = dayId?.let { routines.selectRoutineDayById(it).executeAsOneOrNull() }
        val logged = q.selectSetsForSession(sessionId).executeAsList()

        // El equipamiento se trae de una vez: hace falta para saber a qué ejercicios
        // no tiene sentido pedirles kilos.
        val equipmentByExercise = db.catalogQueries.selectAllExerciseEquipment().executeAsList()
            .groupBy({ it.exerciseId }, { it.equipmentId })

        val exercises = dayId?.let { routines.selectExercisesForDay(it).executeAsList() }.orEmpty().map { planned ->
            val previousRows = q.selectPreviousSetsForExercise(planned.exerciseId, sessionId).executeAsList()
            WorkoutExerciseProgress(
                exerciseId = planned.exerciseId,
                exerciseName = planned.nameEs,
                exerciseType = ExerciseType.valueOf(planned.exerciseType),
                isBodyweight = ExerciseFormatClassifier.isBodyweight(
                    name = planned.nameEs,
                    equipmentIds = equipmentByExercise[planned.exerciseId].orEmpty().toSet(),
                ),
                metValue = planned.metValue,
                imageUrl = planned.mainImageUrl,
                targetSets = planned.targetSets.toInt(),
                targetRepsMin = planned.targetRepsMin?.toInt(),
                targetRepsMax = planned.targetRepsMax?.toInt(),
                targetTimeSeconds = planned.targetTimeSeconds?.toInt(),
                restSeconds = planned.restSeconds.toInt(),
                loggedSets = logged.filter { it.exerciseId == planned.exerciseId }.map { it.toItem() },
                previousSets = previousRows.map { it.toItem() },
                previousDate = previousRows.firstOrNull()?.date?.let { LocalDate.parse(it) },
                progression = progressionSuggestion(
                    exerciseId = planned.exerciseId,
                    targetSets = planned.targetSets.toInt(),
                    targetRepsMax = planned.targetRepsMax?.toInt(),
                ),
                timeProgression = timeProgressionSuggestion(
                    exerciseId = planned.exerciseId,
                    exerciseType = ExerciseType.valueOf(planned.exerciseType),
                    targetSets = planned.targetSets.toInt(),
                    targetTimeSeconds = planned.targetTimeSeconds?.toInt(),
                ),
            )
        }

        ActiveWorkout(
            sessionId = sessionId,
            routineId = session.routineId,
            routineDayId = dayId,
            dayName = day?.name ?: "Entrenamiento libre",
            date = LocalDate.parse(session.date),
            exercises = exercises,
        )
    }

    /**
     * Solo las series registradas en la sesión, agrupadas por ejercicio.
     *
     * Es lo único que cambia mientras entrenas: el prerrelleno y la sugerencia
     * de subir peso dependen de sesiones *anteriores*, así que recargarlos con
     * cada serie apuntada disparaba una veintena de consultas para nada.
     */
    suspend fun loggedSetsBySession(sessionId: Long): Map<Long, List<SetLogItem>> =
        withContext(dispatcher) {
            q.selectSetsForSession(sessionId).executeAsList()
                .groupBy({ it.exerciseId }, { it.toItem() })
        }

    suspend fun logSet(
        sessionId: Long,
        exerciseId: Long,
        setNumber: Int,
        weightKg: Double?,
        reps: Int?,
        timeSeconds: Int?,
        isWarmup: Boolean = false,
        rpe: Double? = null,
    ): Long = withContext(dispatcher) {
        db.transactionWithResult {
            q.insertSetLog(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber.toLong(),
                weightKg = weightKg,
                reps = reps?.toLong(),
                timeSeconds = timeSeconds?.toLong(),
                isWarmup = if (isWarmup) 1 else 0,
                rpe = rpe,
            )
            db.catalogQueries.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun deleteSet(setId: Long) = withContext(dispatcher) { q.deleteSetLog(setId) }

    suspend fun updateSessionNotes(sessionId: Long, notes: String?) = withContext(dispatcher) {
        q.updateSessionNotes(notes?.takeIf { it.isNotBlank() }, sessionId)
    }

    /**
     * Corrige una serie ya registrada, también en sesiones ya terminadas. Los
     * campos que no se pasan (calentamiento, RPE) conservan su valor: corregir
     * un peso no debe convertir un calentamiento en serie efectiva.
     */
    suspend fun updateSet(
        setId: Long,
        weightKg: Double?,
        reps: Int?,
        timeSeconds: Int?,
        isWarmup: Boolean? = null,
        rpe: Double? = null,
    ) = withContext(dispatcher) {
        db.transaction {
            val current = q.selectSetLogById(setId).executeAsOneOrNull() ?: return@transaction
            q.updateSetLog(
                weightKg,
                reps?.toLong(),
                timeSeconds?.toLong(),
                if (isWarmup ?: (current.isWarmup == 1L)) 1 else 0,
                rpe ?: current.rpe,
                setId,
            )
        }
    }

    /**
     * Sesiones empezadas y no terminadas. Se quedarían huérfanas si no se
     * ofrece retomarlas o descartarlas.
     */
    suspend fun unfinishedSessions(): List<CalendarSession> = withContext(dispatcher) {
        q.selectUnfinishedSessions().executeAsList().map { session ->
            val day = session.routineDayId?.let {
                routines.selectRoutineDayById(it).executeAsOneOrNull()
            }
            CalendarSession(
                sessionId = session.id,
                date = LocalDate.parse(session.date),
                dayName = day?.name ?: "Entrenamiento libre",
                isCompleted = false,
                kcalBurned = session.kcalBurned,
                durationMinutes = session.durationMinutes?.toInt(),
            )
        }
    }

    /** Recalcula la duración y el gasto de una sesión ya cerrada tras corregirla. */
    suspend fun recalculateSession(sessionId: Long, bodyWeightKg: Double?) = withContext(dispatcher) {
        val session = q.selectSessionById(sessionId).executeAsOneOrNull() ?: return@withContext
        if (session.isCompleted != 1L) return@withContext
        val kcal = estimateSessionKcal(sessionId, bodyWeightKg)
        // La duración se conserva tal cual, aunque sea nula: recalcular el gasto
        // no debe inventarse un "0 minutos".
        q.completeSession(session.durationMinutes, kcal, session.notes, sessionId)
    }

    suspend fun completeSession(
        sessionId: Long,
        durationMinutes: Int,
        kcalBurned: Double?,
        notes: String?,
    ) = withContext(dispatcher) {
        q.completeSession(durationMinutes.toLong(), kcalBurned, notes, sessionId)
    }

    /**
     * Gasto calórico estimado de una sesión: suma el MET de cada ejercicio por
     * las series efectivamente registradas y el peso corporal del usuario.
     * Devuelve null si aún no hay ningún peso registrado.
     */
    suspend fun estimateSessionKcal(sessionId: Long, bodyWeightKg: Double?): Double? =
        withContext(dispatcher) {
            if (bodyWeightKg == null || bodyWeightKg <= 0) return@withContext null
            // Sin calentamientos, el mismo criterio que el recuento de series y el
            // volumen que se enseñan al lado.
            val sets = q.selectSetsForSession(sessionId).executeAsList()
                .filter { it.isWarmup == 0L }
            if (sets.isEmpty()) return@withContext 0.0

            val restByExercise = q.selectSessionById(sessionId).executeAsOneOrNull()
                ?.routineDayId
                ?.let { routines.selectExercisesForDay(it).executeAsList() }
                ?.associate { it.exerciseId to it.restSeconds.toInt() }
                .orEmpty()

            sets.groupBy { it.exerciseId }.entries.sumOf { (exerciseId, rows) ->
                val exercise = db.catalogQueries.selectExerciseById(exerciseId).executeAsOne()
                val secondsPerSet = rows.mapNotNull { it.timeSeconds?.toDouble() }.average()
                    .takeIf { !it.isNaN() } ?: EnergyCalculator.DEFAULT_SECONDS_PER_REP_SET
                EnergyCalculator.exerciseKcal(
                    // Un MET a 0 o negativo (un backup retocado, un ejercicio propio
                    // mal creado) no debe tumbar el cierre del entreno.
                    met = exercise.metValue.takeIf { it > 0 } ?: 3.5,
                    weightKg = bodyWeightKg,
                    sets = rows.size,
                    secondsPerSet = secondsPerSet,
                    restSeconds = restByExercise[exerciseId] ?: 90,
                )
            }
        }

    suspend fun deleteSession(sessionId: Long) = withContext(dispatcher) { q.deleteSession(sessionId) }

    /**
     * Cuándo se abrió la sesión (persistido al crearla). Es la referencia buena
     * para la duración: medir desde que se abre la *pantalla* reiniciaba el
     * cronómetro al retomar una sesión.
     */
    suspend fun sessionStartTime(sessionId: Long): String? = withContext(dispatcher) {
        q.selectSessionById(sessionId).executeAsOneOrNull()?.startTime
    }

    suspend fun summary(sessionId: Long): WorkoutSummary? = withContext(dispatcher) {
        val session = q.selectSessionById(sessionId).executeAsOneOrNull() ?: return@withContext null
        val day = session.routineDayId?.let { routines.selectRoutineDayById(it).executeAsOneOrNull() }
        val sets = q.selectSetsForSession(sessionId).executeAsList()

        val byExercise = sets.groupBy { it.exerciseId }.map { (exerciseId, rows) ->
            SessionExerciseSummary(
                exerciseId = exerciseId,
                exerciseName = rows.first().nameEs,
                sets = rows.map { it.toItem() },
            )
        }

        // Los músculos de cada ejercicio se consultan una sola vez y sirven para
        // las dos agregaciones de abajo.
        val musclesByExercise = byExercise.associate { exercise ->
            exercise.exerciseId to
                db.catalogQueries.selectMusclesForExercise(exercise.exerciseId).executeAsList()
        }

        val muscleGroups = musclesByExercise.values
            .flatten()
            .filter { it.role == "PRIMARY" }
            .mapNotNull { com.pelayo.gimnasio.domain.model.MuscleGroups.byId(it.muscleGroupId)?.name }
            .distinct()

        // Mismo criterio que el análisis de rutinas: primario 1.0, secundario 0.5.
        val volumeByGroup = mutableMapOf<Long, Double>()
        byExercise.forEach { exercise ->
            val setCount = exercise.sets.count { !it.isWarmup }
            musclesByExercise[exercise.exerciseId].orEmpty().forEach { muscle ->
                val weight = if (muscle.role == "PRIMARY") {
                    MuscleBalanceAnalyzer.PRIMARY_WEIGHT
                } else {
                    MuscleBalanceAnalyzer.SECONDARY_WEIGHT
                }
                volumeByGroup[muscle.muscleGroupId] =
                    (volumeByGroup[muscle.muscleGroupId] ?: 0.0) + setCount * weight
            }
        }

        WorkoutSummary(
            sessionId = sessionId,
            date = LocalDate.parse(session.date),
            dayName = day?.name ?: "Entrenamiento libre",
            durationMinutes = session.durationMinutes?.toInt(),
            kcalBurned = session.kcalBurned,
            totalSets = sets.count { it.isWarmup == 0L },
            totalVolume = byExercise.sumOf { it.volume },
            exercises = byExercise,
            muscleGroupsWorked = muscleGroups,
            notes = session.notes,
            volumeByMuscleGroup = volumeByGroup,
        )
    }

    /**
     * Comprueba si toca subir peso en un ejercicio, mirando las últimas sesiones
     * completadas. Devuelve null si aún no hay motivo para subir.
     */
    suspend fun progressionSuggestion(
        exerciseId: Long,
        targetSets: Int,
        targetRepsMax: Int?,
    ): ProgressionSuggestion? = withContext(dispatcher) {
        // La consulta ya viene ordenada por fecha descendente (y no por id: una
        // sesión corregida con fecha pasada no es "la última") y groupBy conserva
        // ese orden de aparición.
        val sessions = q.selectRecentSetsForExercise(exerciseId).executeAsList()
            .groupBy { it.sessionKey }
            .entries
            // Las que necesite la regla más exigente: la subida mira 2 y el deload 3.
            .take(maxOf(ProgressionAdvisor.SESSIONS_REQUIRED, ProgressionAdvisor.STALL_SESSIONS))
            .map { (_, rows) ->
                PastPerformance(
                    // El peso de trabajo es el mejor de la sesión: la primera serie
                    // puede ser una aproximación más ligera.
                    weightKg = rows.mapNotNull { it.weightKg }.maxOrNull(),
                    reps = rows.mapNotNull { it.reps?.toInt() },
                )
            }

        // Consideramos compuesto el ejercicio con dos o más músculos secundarios.
        val secondaryCount = db.catalogQueries.selectMusclesForExercise(exerciseId)
            .executeAsList()
            .count { it.role == "SECONDARY" }

        ProgressionAdvisor.suggest(
            recentSessions = sessions,
            targetSets = targetSets,
            targetRepsMax = targetRepsMax,
            isCompound = secondaryCount >= 2,
        )
    }

    /** La doble progresión de los ejercicios por tiempo; null si no es de tiempo. */
    private suspend fun timeProgressionSuggestion(
        exerciseId: Long,
        exerciseType: ExerciseType,
        targetSets: Int,
        targetTimeSeconds: Int?,
    ): TimeProgressionSuggestion? = withContext(dispatcher) {
        if (exerciseType != ExerciseType.TIME) return@withContext null
        val sessions = q.selectRecentSetsForExercise(exerciseId).executeAsList()
            .groupBy { it.sessionKey }
            .entries
            .take(ProgressionAdvisor.SESSIONS_REQUIRED)
            .map { (_, rows) -> rows.mapNotNull { it.timeSeconds?.toInt() } }
        ProgressionAdvisor.suggestTime(sessions, targetSets, targetTimeSeconds)
    }

    /**
     * Récords batidos en la sesión respecto a todo lo anterior a ella. Solo se
     * celebra cuando hay historial previo: la primera sesión de un ejercicio lo
     * sería todo y no diría nada.
     */
    suspend fun newRecordsInSession(sessionId: Long): List<SessionRecord> = withContext(dispatcher) {
        val session = q.selectSessionById(sessionId).executeAsOneOrNull()
            ?: return@withContext emptyList()
        if (session.isCompleted != 1L) return@withContext emptyList()

        val sessionSets = q.selectSetsForSession(sessionId).executeAsList()
            .filter { it.isWarmup == 0L }

        sessionSets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, rows) ->
            val prior = q.selectSetsForExercise(exerciseId).executeAsList()
                .filter { it.sessionId != sessionId }
            if (prior.isEmpty()) return@mapNotNull null

            val name = rows.first().nameEs
            val improvements = buildList {
                val bestWeight = rows.mapNotNull { it.weightKg }.maxOrNull()
                val priorWeight = prior.mapNotNull { it.weightKg }.maxOrNull()
                if (bestWeight != null && priorWeight != null && bestWeight > priorWeight) {
                    add("${bestWeight.oneDecimal()} kg (antes ${priorWeight.oneDecimal()})")
                }

                val best1RM = rows.mapNotNull {
                    OneRepMaxCalculator.estimate(it.weightKg, it.reps?.toInt())
                }.maxOrNull()
                val prior1RM = prior.mapNotNull {
                    OneRepMaxCalculator.estimate(it.weightKg, it.reps?.toInt())
                }.maxOrNull()
                if (best1RM != null && prior1RM != null && best1RM > prior1RM) {
                    add("1RM estimado ${best1RM.oneDecimal()} kg")
                }

                val bestTime = rows.mapNotNull { it.timeSeconds?.toInt() }.maxOrNull()
                val priorTime = prior.mapNotNull { it.timeSeconds?.toInt() }.maxOrNull()
                if (bestTime != null && priorTime != null && bestTime > priorTime) {
                    add("$bestTime s (antes $priorTime)")
                }
            }
            improvements.takeIf { it.isNotEmpty() }
                ?.let { SessionRecord(name, it.joinToString(" · ")) }
        }
    }

    /** Serie histórica de un ejercicio, un punto por sesión, para las gráficas. */
    suspend fun progressHistory(exerciseId: Long): List<ProgressPoint> = withContext(dispatcher) {
        q.selectSetsForExercise(exerciseId).executeAsList()
            // Por sesión y no por fecha: dos sesiones el mismo día son dos puntos.
            .groupBy { it.sessionId }
            .map { (_, rows) ->
                ProgressPoint(
                    date = LocalDate.parse(rows.first().date),
                    maxWeight = rows.mapNotNull { it.weightKg }.maxOrNull() ?: 0.0,
                    volume = rows.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0L).toInt() },
                    maxReps = rows.mapNotNull { it.reps?.toInt() }.maxOrNull() ?: 0,
                    maxTimeSeconds = rows.mapNotNull { it.timeSeconds?.toInt() }.maxOrNull() ?: 0,
                    best1RM = rows.mapNotNull {
                        OneRepMaxCalculator.estimate(it.weightKg, it.reps?.toInt())
                    }.maxOrNull(),
                )
            }
            .sortedBy { it.date }
    }

    /**
     * Mejores marcas históricas del ejercicio; null si nunca se ha entrenado.
     * Solo sesiones completadas y sin calentamientos, igual que la gráfica.
     */
    suspend fun personalRecords(exerciseId: Long): PersonalRecords? = withContext(dispatcher) {
        val rows = q.selectSetsForExercise(exerciseId).executeAsList()
        if (rows.isEmpty()) return@withContext null
        PersonalRecords(
            maxWeightKg = rows.mapNotNull { it.weightKg }.maxOrNull(),
            maxReps = rows.mapNotNull { it.reps?.toInt() }.maxOrNull(),
            maxTimeSeconds = rows.mapNotNull { it.timeSeconds?.toInt() }.maxOrNull(),
            bestEstimated1RM = rows.mapNotNull {
                OneRepMaxCalculator.estimate(it.weightKg, it.reps?.toInt())
            }.maxOrNull(),
            sessions = rows.distinctBy { it.sessionId }.size,
        )
    }

    suspend fun recentSessions(limit: Int = 10): List<WorkoutSummary> = withContext(dispatcher) {
        q.selectRecentSessions(limit.toLong()).executeAsList().mapNotNull { summary(it.id) }
    }

    /** Sesiones de un rango de fechas, agrupadas por día, para pintar el calendario. */
    suspend fun sessionsByDate(from: LocalDate, to: LocalDate): Map<LocalDate, List<CalendarSession>> =
        withContext(dispatcher) {
            q.selectSessionsInRange(from.toString(), to.toString()).executeAsList()
                .map { session ->
                    val day = session.routineDayId?.let {
                        routines.selectRoutineDayById(it).executeAsOneOrNull()
                    }
                    CalendarSession(
                        sessionId = session.id,
                        date = LocalDate.parse(session.date),
                        dayName = day?.name ?: "Entrenamiento libre",
                        isCompleted = session.isCompleted == 1L,
                        kcalBurned = session.kcalBurned,
                        durationMinutes = session.durationMinutes?.toInt(),
                    )
                }
                .groupBy { it.date }
        }

    suspend fun completedSessionDates(): List<LocalDate> = withContext(dispatcher) {
        q.selectCompletedSessionDates().executeAsList().map { LocalDate.parse(it) }
    }

    suspend fun completedSessionCount(): Long = withContext(dispatcher) {
        q.countCompletedSessions().executeAsOne()
    }

    // La consulta devuelve una sola columna, así que SQLDelight ya da el Long directamente.
    suspend fun lastCompletedRoutineDayId(): Long? = withContext(dispatcher) {
        q.selectLastCompletedRoutineDay().executeAsOneOrNull()
    }

    /** Kcal quemadas en entrenos terminados de una fecha concreta. */
    suspend fun kcalBurnedOn(date: LocalDate): Double = withContext(dispatcher) {
        q.selectSessionsForDate(date.toString()).executeAsList()
            .filter { it.isCompleted == 1L }
            .sumOf { it.kcalBurned ?: 0.0 }
    }
}

private fun com.pelayo.gimnasio.db.SelectSetsForSession.toItem() = SetLogItem(
    id = id,
    exerciseId = exerciseId,
    setNumber = setNumber.toInt(),
    weightKg = weightKg,
    reps = reps?.toInt(),
    timeSeconds = timeSeconds?.toInt(),
    isWarmup = isWarmup == 1L,
    rpe = rpe,
)

private fun com.pelayo.gimnasio.db.SetLog.toItem() = SetLogItem(
    id = id,
    exerciseId = exerciseId,
    setNumber = setNumber.toInt(),
    weightKg = weightKg,
    reps = reps?.toInt(),
    timeSeconds = timeSeconds?.toInt(),
    isWarmup = isWarmup == 1L,
    rpe = rpe,
)

private fun com.pelayo.gimnasio.db.SelectPreviousSetsForExercise.toItem() = SetLogItem(
    id = id,
    exerciseId = exerciseId,
    setNumber = setNumber.toInt(),
    weightKg = weightKg,
    reps = reps?.toInt(),
    timeSeconds = timeSeconds?.toInt(),
    isWarmup = isWarmup == 1L,
    rpe = rpe,
)
