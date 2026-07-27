package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.model.RoutineGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Copiar un día de una rutina a otra, y sustituir un ejercicio por otro en su sitio. */
class CopyDayTest {

    private class Fixture {
        val db = createTestDatabase()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val bench = db.insertTestExercise(1, "Press de banca", listOf(4L), listOf(5L))
        val fly = db.insertTestExercise(2, "Aperturas", listOf(4L))
        val plank = db.insertTestExercise(3, "Plancha", listOf(14L), exerciseType = "TIME")
    }

    private suspend fun Fixture.routineWithDay(
        name: String,
        dayName: String = "Empuje",
        weekday: Int? = null,
    ): Pair<Long, Long> {
        val routineId = repo.createRoutine(name, null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, dayName, weekday)
        return routineId to dayId
    }

    @Test
    fun copia_el_dia_con_sus_ejercicios_y_sus_series() = runTest {
        val f = Fixture()
        val (_, dayId) = f.routineWithDay("Rutina A")
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.FUERZA)
        f.repo.addExerciseToDay(dayId, f.fly, RoutineGoal.FUERZA)
        val (targetId, _) = f.routineWithDay("Rutina B", dayName = "Pierna")

        val newDayId = assertNotNull(f.repo.copyDayToRoutine(dayId, targetId))

        val target = assertNotNull(f.repo.routineDetail(targetId))
        assertEquals(listOf("Pierna", "Empuje"), target.days.map { it.name })
        val copied = target.days.first { it.id == newDayId }
        assertEquals(listOf("Press de banca", "Aperturas"), copied.exercises.map { it.exerciseName })
        // Las series viajan tal cual: la copia sirve para no volver a configurarlas.
        assertTrue(copied.exercises.all { it.targetSets == 4 && it.targetRepsMin == 3 })
        assertEquals(listOf(0, 1), copied.exercises.map { it.position })
    }

    @Test
    fun el_dia_original_se_queda_donde_estaba() = runTest {
        val f = Fixture()
        val (sourceId, dayId) = f.routineWithDay("Rutina A")
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)
        val (targetId, _) = f.routineWithDay("Rutina B")

        f.repo.copyDayToRoutine(dayId, targetId)

        val source = assertNotNull(f.repo.routineDetail(sourceId))
        assertEquals(1, source.days.size)
        assertEquals(listOf("Press de banca"), source.days.single().exercises.map { it.exerciseName })
    }

    @Test
    fun conserva_el_dia_de_la_semana_si_esta_libre_en_el_destino() = runTest {
        val f = Fixture()
        val (_, dayId) = f.routineWithDay("Rutina A", weekday = 3)
        val (targetId, _) = f.routineWithDay("Rutina B", dayName = "Pierna", weekday = 5)

        val newDayId = assertNotNull(f.repo.copyDayToRoutine(dayId, targetId))

        val copied = assertNotNull(f.repo.routineDetail(targetId)).days.first { it.id == newDayId }
        assertEquals(3, copied.weekday)
    }

    @Test
    fun suelta_el_dia_de_la_semana_si_el_destino_ya_lo_tiene_ocupado() = runTest {
        // Dos días el mismo miércoles harían que el recordatorio avisara dos veces.
        val f = Fixture()
        val (_, dayId) = f.routineWithDay("Rutina A", weekday = 3)
        val (targetId, _) = f.routineWithDay("Rutina B", dayName = "Pierna", weekday = 3)

        val newDayId = assertNotNull(f.repo.copyDayToRoutine(dayId, targetId))

        val copied = assertNotNull(f.repo.routineDetail(targetId)).days.first { it.id == newDayId }
        assertNull(copied.weekday)
    }

    @Test
    fun desambigua_el_nombre_si_el_destino_ya_tiene_uno_igual() = runTest {
        val f = Fixture()
        val (_, dayId) = f.routineWithDay("Rutina A", dayName = "Empuje")
        val (targetId, _) = f.routineWithDay("Rutina B", dayName = "Empuje")

        f.repo.copyDayToRoutine(dayId, targetId)
        f.repo.copyDayToRoutine(dayId, targetId)

        val names = assertNotNull(f.repo.routineDetail(targetId)).days.map { it.name }
        assertEquals(listOf("Empuje", "Empuje (copia)", "Empuje (copia 2)"), names)
    }

    @Test
    fun copiar_a_una_rutina_inexistente_no_hace_nada() = runTest {
        val f = Fixture()
        val (_, dayId) = f.routineWithDay("Rutina A")

        assertNull(f.repo.copyDayToRoutine(dayId, targetRoutineId = 999L))
        assertNull(f.repo.copyDayToRoutine(dayId = 999L, targetRoutineId = 1L))
    }

    @Test
    fun sustituir_un_ejercicio_lo_deja_en_la_misma_posicion() = runTest {
        val f = Fixture()
        val (routineId, dayId) = f.routineWithDay("Rutina A")
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)
        f.repo.addExerciseToDay(dayId, f.fly, RoutineGoal.HIPERTROFIA)

        val primero = assertNotNull(f.repo.routineDetail(routineId)).days.single().exercises.first()
        f.repo.replaceRoutineExercise(primero.id, f.plank, RoutineGoal.HIPERTROFIA)

        val day = assertNotNull(f.repo.routineDetail(routineId)).days.single()
        assertEquals(listOf("Plancha", "Aperturas"), day.exercises.map { it.exerciseName })
        assertEquals(0, day.exercises.first().position)
    }

    @Test
    fun al_sustituir_por_un_ejercicio_de_tiempo_se_rehacen_los_objetivos() = runTest {
        // "Plancha" es de tiempo: dejarle las repeticiones del anterior daría
        // un objetivo imposible de registrar.
        val f = Fixture()
        val (routineId, dayId) = f.routineWithDay("Rutina A")
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)

        val item = assertNotNull(f.repo.routineDetail(routineId)).days.single().exercises.single()
        f.repo.replaceRoutineExercise(item.id, f.plank, RoutineGoal.HIPERTROFIA)

        val nuevo = assertNotNull(f.repo.routineDetail(routineId)).days.single().exercises.single()
        assertNull(nuevo.targetRepsMin)
        assertNull(nuevo.targetRepsMax)
        assertEquals(60, nuevo.targetTimeSeconds)
        // Las series y el descanso sí se respetan.
        assertEquals(item.targetSets, nuevo.targetSets)
        assertEquals(item.restSeconds, nuevo.restSeconds)
    }
}
