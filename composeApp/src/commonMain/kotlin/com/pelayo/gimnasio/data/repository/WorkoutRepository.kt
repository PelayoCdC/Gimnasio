package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.calc.EnergyCalculator
import com.pelayo.gimnasio.domain.calc.MuscleBalanceAnalyzer
import com.pelayo.gimnasio.domain.calc.PastPerformance
import com.pelayo.gimnasio.domain.calc.ProgressionAdvisor
import com.pelayo.gimnasio.domain.calc.ProgressionSuggestion
import com.pelayo.gimnasio.domain.model.ActiveWorkout
import com.pelayo.gimnasio.domain.model.CalendarSession
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.ProgressPoint
import com.pelayo.gimnasio.domain.model.SessionExerciseSummary
import com.pelayo.gimnasio.domain.model.SetLogItem
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
            val day = routines.selectRoutineDayById(routineDayId).executeAsOne()
            val existing = q.selectSessionsForDate(date.toString()).executeAsList()
                .firstOrNull { it.routineDayId == routineDayId && it.isCompleted == 0L }
            existing?.id ?: run {
                q.insertSession(
                    routineId = day.routineId,
                    routineDayId = routineDayId,
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

        val exercises = dayId?.let { routines.selectExercisesForDay(it).executeAsList() }.orEmpty().map { planned ->
            WorkoutExerciseProgress(
                exerciseId = planned.exerciseId,
                exerciseName = planned.nameEs,
                exerciseType = ExerciseType.valueOf(planned.exerciseType),
                metValue = planned.metValue,
                imageUrl = planned.mainImageUrl,
                targetSets = planned.targetSets.toInt(),
                targetRepsMin = planned.targetRepsMin?.toInt(),
                targetRepsMax = planned.targetRepsMax?.toInt(),
                targetTimeSeconds = planned.targetTimeSeconds?.toInt(),
                restSeconds = planned.restSeconds.toInt(),
                loggedSets = logged.filter { it.exerciseId == planned.exerciseId }.map { it.toItem() },
                previousSets = q.selectLastSetsForExercise(planned.exerciseId).executeAsList()
                    .filter { it.sessionId != sessionId }
                    .map { it.toItem() },
                progression = progressionSuggestion(
                    exerciseId = planned.exerciseId,
                    targetSets = planned.targetSets.toInt(),
                    targetRepsMax = planned.targetRepsMax?.toInt(),
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
                rpe = null,
            )
            db.catalogQueries.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun deleteSet(setId: Long) = withContext(dispatcher) { q.deleteSetLog(setId) }

    suspend fun updateSessionNotes(sessionId: Long, notes: String?) = withContext(dispatcher) {
        q.updateSessionNotes(notes?.takeIf { it.isNotBlank() }, sessionId)
    }

    /** Corrige una serie ya registrada, también en sesiones ya terminadas. */
    suspend fun updateSet(
        setId: Long,
        weightKg: Double?,
        reps: Int?,
        timeSeconds: Int?,
        isWarmup: Boolean = false,
    ) = withContext(dispatcher) {
        q.updateSetLog(weightKg, reps?.toLong(), timeSeconds?.toLong(), if (isWarmup) 1 else 0, null, setId)
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
        q.completeSession(session.durationMinutes ?: 0L, kcal, session.notes, sessionId)
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
            val sets = q.selectSetsForSession(sessionId).executeAsList()
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
                    met = exercise.metValue,
                    weightKg = bodyWeightKg,
                    sets = rows.size,
                    secondsPerSet = secondsPerSet,
                    restSeconds = restByExercise[exerciseId] ?: 90,
                )
            }
        }

    suspend fun deleteSession(sessionId: Long) = withContext(dispatcher) { q.deleteSession(sessionId) }

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

        val muscleGroups = byExercise
            .flatMap { db.catalogQueries.selectMusclesForExercise(it.exerciseId).executeAsList() }
            .filter { it.role == "PRIMARY" }
            .mapNotNull { com.pelayo.gimnasio.domain.model.MuscleGroups.byId(it.muscleGroupId)?.name }
            .distinct()

        // Mismo criterio que el análisis de rutinas: primario 1.0, secundario 0.5.
        val volumeByGroup = mutableMapOf<Long, Double>()
        byExercise.forEach { exercise ->
            val setCount = exercise.sets.count { !it.isWarmup }
            db.catalogQueries.selectMusclesForExercise(exercise.exerciseId).executeAsList()
                .forEach { muscle ->
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
        val sessions = q.selectRecentSetsForExercise(exerciseId).executeAsList()
            .groupBy { it.sessionKey }
            .entries
            .sortedByDescending { it.key }
            .take(ProgressionAdvisor.SESSIONS_REQUIRED)
            .map { (_, rows) ->
                PastPerformance(
                    weightKg = rows.firstOrNull()?.weightKg,
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

    /** Serie histórica de un ejercicio, un punto por sesión, para las gráficas. */
    suspend fun progressHistory(exerciseId: Long): List<ProgressPoint> = withContext(dispatcher) {
        q.selectSetsForExercise(exerciseId).executeAsList()
            .groupBy { it.date }
            .map { (date, rows) ->
                ProgressPoint(
                    date = LocalDate.parse(date),
                    maxWeight = rows.mapNotNull { it.weightKg }.maxOrNull() ?: 0.0,
                    volume = rows.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0L).toInt() },
                    maxReps = rows.mapNotNull { it.reps?.toInt() }.maxOrNull() ?: 0,
                    maxTimeSeconds = rows.mapNotNull { it.timeSeconds?.toInt() }.maxOrNull() ?: 0,
                )
            }
            .sortedBy { it.date }
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
)

private fun com.pelayo.gimnasio.db.SetLog.toItem() = SetLogItem(
    id = id,
    exerciseId = exerciseId,
    setNumber = setNumber.toInt(),
    weightKg = weightKg,
    reps = reps?.toInt(),
    timeSeconds = timeSeconds?.toInt(),
    isWarmup = isWarmup == 1L,
)
