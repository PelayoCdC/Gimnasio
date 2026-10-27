package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.RoutineGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRepositoryTest {

    private class Fixture {
        val db = createTestDatabase()
        val routines = RoutineRepository(db, Dispatchers.Unconfined)
        val workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L), listOf(5L))
        val plankId = db.insertTestExercise(2, "Plancha", listOf(6L), exerciseType = "TIME")
    }

    private suspend fun Fixture.routineWithBench(): Long {
        val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = routines.addDay(routineId, "Día 1", null)
        routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
        return dayId
    }

    @Test
    fun registra_series_y_las_recupera_en_la_sesion_activa() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, weightKg = 60.0, reps = 10, timeSeconds = null)
        f.workouts.logSet(sessionId, f.benchId, 2, weightKg = 62.5, reps = 8, timeSeconds = null)

        val active = assertNotNull(f.workouts.activeWorkout(sessionId))
        assertEquals("Día 1", active.dayName)
        assertEquals(LocalDate(2026, 7, 25), active.date)

        val exercise = active.exercises.single()
        assertEquals(2, exercise.loggedSets.size)
        assertEquals(4, exercise.targetSets)
        assertTrue(!exercise.isComplete, "solo 2 de 4 series")
        assertEquals("62.5 kg × 8", exercise.loggedSets[1].label)
    }

    @Test
    fun empezar_dos_veces_el_mismo_dia_reutiliza_la_sesion_sin_terminar() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val date = LocalDate(2026, 7, 25)

        val first = f.workouts.startSession(dayId, date)
        val second = f.workouts.startSession(dayId, date)

        assertEquals(first, second, "no debe duplicar la sesión en curso")
    }

    @Test
    fun terminar_sesion_genera_resumen_con_volumen_y_musculos() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 60.0, 10, null)
        f.workouts.logSet(sessionId, f.benchId, 2, 60.0, 8, null)

        f.workouts.completeSession(sessionId, durationMinutes = 47, kcalBurned = 210.0, notes = null)

        val summary = assertNotNull(f.workouts.summary(sessionId))
        assertEquals(47, summary.durationMinutes)
        assertEquals(210.0, summary.kcalBurned)
        assertEquals(2, summary.totalSets)
        assertEquals(60.0 * 10 + 60.0 * 8, summary.totalVolume)
        // Solo cuenta el músculo primario (pectoral), no el tríceps secundario.
        assertEquals(listOf("Pecho"), summary.muscleGroupsWorked)
    }

    @Test
    fun el_resumen_reparte_el_volumen_por_grupo_muscular() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        // 3 series de press: pectoral primario (4) y tríceps secundario (5).
        repeat(3) { index -> f.workouts.logSet(sessionId, f.benchId, index + 1, 60.0, 10, null) }
        f.workouts.completeSession(sessionId, 40, null, null)

        val volume = f.workouts.summary(sessionId)!!.volumeByMuscleGroup

        assertEquals(3.0, volume[MuscleGroups.PECHO.id], "3 series como primario")
        assertEquals(1.5, volume[MuscleGroups.TRICEPS.id], "3 series como secundario cuentan la mitad")
        assertNull(volume[MuscleGroups.CUADRICEPS.id], "lo no trabajado no aparece")
    }

    @Test
    fun las_series_de_calentamiento_no_suman_volumen_muscular() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 20.0, 15, null, isWarmup = true)
        f.workouts.logSet(sessionId, f.benchId, 2, 60.0, 10, null)
        f.workouts.completeSession(sessionId, 40, null, null)

        val summary = f.workouts.summary(sessionId)!!
        assertEquals(1, summary.totalSets)
        assertEquals(1.0, summary.volumeByMuscleGroup[MuscleGroups.PECHO.id])
    }

    @Test
    fun la_sesion_anterior_se_ofrece_como_prerrelleno() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        val first = f.workouts.startSession(dayId, LocalDate(2026, 7, 20))
        f.workouts.logSet(first, f.benchId, 1, 60.0, 10, null)
        f.workouts.completeSession(first, 40, null, null)

        val second = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        val exercise = f.workouts.activeWorkout(second)!!.exercises.single()

        assertTrue(exercise.loggedSets.isEmpty())
        assertEquals(1, exercise.previousSets.size)
        assertEquals(60.0, exercise.previousSets.single().weightKg)
    }

    @Test
    fun el_historial_agrupa_por_sesion_y_ordena_por_fecha() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        val older = f.workouts.startSession(dayId, LocalDate(2026, 7, 10))
        f.workouts.logSet(older, f.benchId, 1, 55.0, 10, null)
        f.workouts.logSet(older, f.benchId, 2, 57.5, 8, null)
        f.workouts.completeSession(older, 40, null, null)

        val newer = f.workouts.startSession(dayId, LocalDate(2026, 7, 20))
        f.workouts.logSet(newer, f.benchId, 1, 60.0, 10, null)
        f.workouts.completeSession(newer, 40, null, null)

        val history = f.workouts.progressHistory(f.benchId)
        assertEquals(2, history.size)
        assertEquals(LocalDate(2026, 7, 10), history.first().date)
        assertEquals(57.5, history.first().maxWeight)
        assertEquals(55.0 * 10 + 57.5 * 8, history.first().volume)
        assertEquals(60.0, history.last().maxWeight)
    }

    @Test
    fun las_sesiones_sin_terminar_no_entran_en_el_historial() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 60.0, 10, null)

        assertTrue(f.workouts.progressHistory(f.benchId).isEmpty())

        f.workouts.completeSession(sessionId, 30, null, null)
        assertEquals(1, f.workouts.progressHistory(f.benchId).size)
    }

    @Test
    fun borrar_una_serie_la_quita_de_la_sesion() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        val setId = f.workouts.logSet(sessionId, f.benchId, 1, 60.0, 10, null)

        f.workouts.deleteSet(setId)

        assertTrue(f.workouts.activeWorkout(sessionId)!!.exercises.single().loggedSets.isEmpty())
    }

    @Test
    fun borrar_la_sesion_arrastra_sus_series() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 60.0, 10, null)

        f.workouts.deleteSession(sessionId)

        assertNull(f.workouts.activeWorkout(sessionId))
        assertTrue(f.db.workoutsQueries.selectSetsForSession(sessionId).executeAsList().isEmpty())
    }

    @Test
    fun tras_dos_sesiones_al_tope_sugiere_subir_peso_en_la_siguiente() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        // Dos sesiones completas a 60 kg alcanzando el tope (4×12 de hipertrofia).
        listOf(LocalDate(2026, 7, 10), LocalDate(2026, 7, 17)).forEach { date ->
            val id = f.workouts.startSession(dayId, date)
            repeat(4) { index -> f.workouts.logSet(id, f.benchId, index + 1, 60.0, 12, null) }
            f.workouts.completeSession(id, 45, null, null)
        }

        val third = f.workouts.startSession(dayId, LocalDate(2026, 7, 24))
        val exercise = f.workouts.activeWorkout(third)!!.exercises.single()

        val suggestion = assertNotNull(exercise.progression)
        // El press de banca tiene un solo músculo secundario → aislamiento (+2.5).
        assertEquals(62.5, suggestion.newWeightKg)
        assertEquals(60.0, suggestion.previousWeightKg)
    }

    @Test
    fun si_no_se_llega_al_tope_no_sugiere_subir() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        listOf(LocalDate(2026, 7, 10), LocalDate(2026, 7, 17)).forEach { date ->
            val id = f.workouts.startSession(dayId, date)
            repeat(4) { index -> f.workouts.logSet(id, f.benchId, index + 1, 60.0, 9, null) }
            f.workouts.completeSession(id, 45, null, null)
        }

        val third = f.workouts.startSession(dayId, LocalDate(2026, 7, 24))
        assertNull(f.workouts.activeWorkout(third)!!.exercises.single().progression)
    }

    @Test
    fun un_compuesto_sube_de_cinco_en_cinco() = runTest {
        val f = Fixture()
        // Tres músculos secundarios lo hacen compuesto.
        val squatId = f.db.insertTestExercise(9, "Sentadilla", listOf(10L), listOf(8L, 11L, 6L))
        val routineId = f.routines.createRoutine("Pierna", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.routines.addDay(routineId, "Pierna", null)
        f.routines.addExerciseToDay(dayId, squatId, RoutineGoal.HIPERTROFIA)

        listOf(LocalDate(2026, 7, 10), LocalDate(2026, 7, 17)).forEach { date ->
            val id = f.workouts.startSession(dayId, date)
            repeat(4) { index -> f.workouts.logSet(id, squatId, index + 1, 80.0, 12, null) }
            f.workouts.completeSession(id, 45, null, null)
        }

        val third = f.workouts.startSession(dayId, LocalDate(2026, 7, 24))
        val suggestion = assertNotNull(f.workouts.activeWorkout(third)!!.exercises.single().progression)
        assertEquals(85.0, suggestion.newWeightKg)
    }

    @Test
    fun ejercicio_por_tiempo_registra_segundos() = runTest {
        val f = Fixture()
        val routineId = f.routines.createRoutine("Core", null, RoutineGoal.RESISTENCIA)
        val dayId = f.routines.addDay(routineId, "Core", null)
        f.routines.addExerciseToDay(dayId, f.plankId, RoutineGoal.RESISTENCIA)

        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.plankId, 1, weightKg = null, reps = null, timeSeconds = 45)

        val set = f.workouts.activeWorkout(sessionId)!!.exercises.single().loggedSets.single()
        assertEquals("45 s", set.label)
        assertEquals(0.0, set.volume)
    }

    @Test
    fun el_calentamiento_no_suma_volumen_ni_kcal() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 40.0, 10, null, isWarmup = true)
        f.workouts.logSet(sessionId, f.benchId, 2, 80.0, 10, null)
        f.workouts.completeSession(sessionId, 45, null, null)

        val summary = assertNotNull(f.workouts.summary(sessionId))
        assertEquals(1, summary.totalSets)
        // Solo la serie efectiva: 80 × 10.
        assertEquals(800.0, summary.totalVolume)

        // Las kcal usan el mismo criterio: con solo el calentamiento, cero.
        val warmupOnly = f.workouts.startSession(dayId, LocalDate(2026, 7, 26))
        f.workouts.logSet(warmupOnly, f.benchId, 1, 40.0, 10, null, isWarmup = true)
        assertEquals(0.0, f.workouts.estimateSessionKcal(warmupOnly, bodyWeightKg = 80.0))
    }

    @Test
    fun corregir_una_serie_conserva_el_calentamiento_y_el_rpe() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 40.0, 10, null, isWarmup = true)
        val setId = f.workouts.activeWorkout(sessionId)!!.exercises.single().loggedSets.single().id

        // Corregir el peso no debe convertirlo en serie efectiva.
        f.workouts.updateSet(setId, weightKg = 45.0, reps = 10, timeSeconds = null)

        val set = f.workouts.activeWorkout(sessionId)!!.exercises.single().loggedSets.single()
        assertTrue(set.isWarmup, "el flag de calentamiento debe sobrevivir a la corrección")
        assertEquals(45.0, set.weightKg)
    }

    @Test
    fun la_sesion_anterior_y_su_fecha_salen_de_la_misma_sesion() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        // Sesión vieja por fecha pero insertada la última (id mayor).
        val recent = f.workouts.startSession(dayId, LocalDate(2026, 7, 20))
        f.workouts.logSet(recent, f.benchId, 1, 80.0, 10, null)
        f.workouts.completeSession(recent, 45, null, null)

        val backdated = f.workouts.startSession(dayId, LocalDate(2026, 7, 1))
        f.workouts.logSet(backdated, f.benchId, 1, 60.0, 10, null)
        f.workouts.completeSession(backdated, 45, null, null)

        val today = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        val exercise = f.workouts.activeWorkout(today)!!.exercises.single()
        // Manda la fecha, no el id: lo último de verdad es lo del día 20.
        assertEquals(LocalDate(2026, 7, 20), exercise.previousDate)
        assertEquals(80.0, exercise.previousSets.single().weightKg)
    }

    @Test
    fun los_records_de_sesion_solo_se_celebran_con_historial_previo() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()

        // Primera sesión: todo sería récord, así que no se celebra nada.
        val first = f.workouts.startSession(dayId, LocalDate(2026, 7, 10))
        f.workouts.logSet(first, f.benchId, 1, 80.0, 10, null)
        f.workouts.completeSession(first, 45, null, null)
        assertTrue(f.workouts.newRecordsInSession(first).isEmpty())

        // Segunda con más peso: récord de peso (y de 1RM).
        val second = f.workouts.startSession(dayId, LocalDate(2026, 7, 17))
        f.workouts.logSet(second, f.benchId, 1, 85.0, 10, null)
        f.workouts.completeSession(second, 45, null, null)
        val records = f.workouts.newRecordsInSession(second)
        assertEquals(1, records.size)
        assertEquals("Press de banca", records.single().exerciseName)
        assertTrue("85" in records.single().description)

        // Tercera más floja: sin récords.
        val third = f.workouts.startSession(dayId, LocalDate(2026, 7, 24))
        f.workouts.logSet(third, f.benchId, 1, 70.0, 8, null)
        f.workouts.completeSession(third, 45, null, null)
        assertTrue(f.workouts.newRecordsInSession(third).isEmpty())
    }

    @Test
    fun los_records_del_ejercicio_incluyen_el_1rm_estimado() = runTest {
        val f = Fixture()
        val dayId = f.routineWithBench()
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 100.0, 10, null)
        f.workouts.completeSession(sessionId, 45, null, null)

        val records = assertNotNull(f.workouts.personalRecords(f.benchId))
        assertEquals(100.0, records.maxWeightKg)
        assertEquals(10, records.maxReps)
        assertEquals(133.0, records.bestEstimated1RM!!, absoluteTolerance = 0.5)
        assertEquals(1, records.sessions)
    }
}
