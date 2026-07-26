package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.model.RoutineGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Corregir un entrenamiento ya cerrado: un peso mal apuntado envenena las
 * gráficas y la sugerencia de subir peso, así que tiene que poder arreglarse.
 */
class SessionEditingTest {

    private class Fixture {
        val db = createTestDatabase()
        val routines = RoutineRepository(db, Dispatchers.Unconfined)
        val workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L), metValue = 6.0)
    }

    private suspend fun Fixture.finishedSession(vararg weights: Double): Long {
        val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = routines.addDay(routineId, "Día 1", null)
        routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
        val sessionId = workouts.startSession(dayId, LocalDate(2026, 7, 25))
        weights.forEachIndexed { index, weight ->
            workouts.logSet(sessionId, benchId, index + 1, weight, 10, null)
        }
        workouts.completeSession(sessionId, 45, kcalBurned = 100.0, notes = null)
        return sessionId
    }

    @Test
    fun corregir_una_serie_actualiza_el_volumen_del_resumen() = runTest {
        val f = Fixture()
        val sessionId = f.finishedSession(100.0) // dedazo: eran 10 kg

        val setId = f.workouts.summary(sessionId)!!.exercises.single().sets.single().id
        f.workouts.updateSet(setId, weightKg = 10.0, reps = 10, timeSeconds = null)

        val summary = assertNotNull(f.workouts.summary(sessionId))
        assertEquals(100.0, summary.totalVolume, "10 kg × 10 reps")
        assertEquals("10 kg × 10", summary.exercises.single().sets.single().label)
    }

    @Test
    fun corregir_una_serie_saca_el_dato_malo_del_historial() = runTest {
        val f = Fixture()
        val sessionId = f.finishedSession(100.0)
        assertEquals(100.0, f.workouts.progressHistory(f.benchId).single().maxWeight)

        val setId = f.workouts.summary(sessionId)!!.exercises.single().sets.single().id
        f.workouts.updateSet(setId, weightKg = 10.0, reps = 10, timeSeconds = null)

        assertEquals(10.0, f.workouts.progressHistory(f.benchId).single().maxWeight)
    }

    @Test
    fun borrar_una_serie_de_una_sesion_cerrada_la_quita_del_resumen() = runTest {
        val f = Fixture()
        val sessionId = f.finishedSession(60.0, 62.5)

        val setId = f.workouts.summary(sessionId)!!.exercises.single().sets.first().id
        f.workouts.deleteSet(setId)

        val summary = assertNotNull(f.workouts.summary(sessionId))
        assertEquals(1, summary.totalSets)
        assertEquals(625.0, summary.totalVolume)
    }

    @Test
    fun recalcular_ajusta_el_gasto_congelado_al_terminar() = runTest {
        val f = Fixture()
        val sessionId = f.finishedSession(60.0, 60.0, 60.0)
        assertEquals(100.0, f.workouts.summary(sessionId)!!.kcalBurned, "valor congelado")

        f.workouts.recalculateSession(sessionId, bodyWeightKg = 80.0)

        // 3 series × (40 s + 45 s) = 255 s; MET 6 × 80 kg × 0.070833 h ≈ 34 kcal.
        val kcal = assertNotNull(f.workouts.summary(sessionId)!!.kcalBurned)
        assertTrue(kotlin.math.abs(34.0 - kcal) < 0.5, "obtenido $kcal")
    }

    @Test
    fun recalcular_no_toca_las_sesiones_sin_terminar() = runTest {
        val f = Fixture()
        val routineId = f.routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.routines.addDay(routineId, "Día 1", null)
        f.routines.addExerciseToDay(dayId, f.benchId, RoutineGoal.HIPERTROFIA)
        val sessionId = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.logSet(sessionId, f.benchId, 1, 60.0, 10, null)

        f.workouts.recalculateSession(sessionId, bodyWeightKg = 80.0)

        assertNull(f.workouts.summary(sessionId)!!.kcalBurned)
    }

    @Test
    fun borrar_la_sesion_la_saca_del_historial_y_del_recuento() = runTest {
        val f = Fixture()
        val sessionId = f.finishedSession(60.0)
        assertEquals(1L, f.workouts.completedSessionCount())

        f.workouts.deleteSession(sessionId)

        assertEquals(0L, f.workouts.completedSessionCount())
        assertTrue(f.workouts.progressHistory(f.benchId).isEmpty())
        assertNull(f.workouts.summary(sessionId))
    }

    @Test
    fun las_sesiones_sin_terminar_se_pueden_listar_para_retomarlas() = runTest {
        val f = Fixture()
        val routineId = f.routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.routines.addDay(routineId, "Día 1 – Torso", null)
        f.routines.addExerciseToDay(dayId, f.benchId, RoutineGoal.HIPERTROFIA)

        val abandoned = f.workouts.startSession(dayId, LocalDate(2026, 7, 20))
        f.workouts.logSet(abandoned, f.benchId, 1, 60.0, 10, null)
        val finished = f.workouts.startSession(dayId, LocalDate(2026, 7, 25))
        f.workouts.completeSession(finished, 40, null, null)

        val pending = f.workouts.unfinishedSessions()

        assertEquals(1, pending.size)
        assertEquals(abandoned, pending.single().sessionId)
        assertEquals("Día 1 – Torso", pending.single().dayName)
        assertEquals(LocalDate(2026, 7, 20), pending.single().date)
    }

    @Test
    fun sin_sesiones_a_medias_la_lista_esta_vacia() = runTest {
        val f = Fixture()
        f.finishedSession(60.0)
        assertTrue(f.workouts.unfinishedSessions().isEmpty())
    }
}
