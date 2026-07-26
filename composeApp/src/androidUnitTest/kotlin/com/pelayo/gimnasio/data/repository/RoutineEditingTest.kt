package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.model.RoutineGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineEditingTest {

    private class Fixture {
        val db = createTestDatabase()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val bench = db.insertTestExercise(1, "Press de banca", listOf(4L), listOf(5L))
        val squat = db.insertTestExercise(2, "Sentadilla", listOf(10L))
    }

    @Test
    fun renombrar_un_dia_no_toca_sus_ejercicios() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.repo.addDay(routineId, "Día 1", weekday = null)
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)

        f.repo.updateDay(dayId, "Empuje", weekday = 3, dayIndex = 0)

        val day = assertNotNull(f.repo.routineDetail(routineId)).days.single()
        assertEquals("Empuje", day.name)
        assertEquals(3, day.weekday)
        assertEquals(listOf("Press de banca"), day.exercises.map { it.exerciseName })
    }

    @Test
    fun se_puede_asignar_dia_de_la_semana_a_posteriori() = runTest {
        // Es el caso que dejaba los recordatorios inservibles: día creado sin
        // weekday y sin forma de ponérselo después.
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.repo.addDay(routineId, "Día 1", weekday = null)
        assertNull(f.repo.routineDetail(routineId)!!.days.single().weekday)

        f.repo.updateDay(dayId, "Día 1", weekday = 1, dayIndex = 0)

        assertEquals(1, f.repo.routineDetail(routineId)!!.days.single().weekday)
    }

    @Test
    fun quitar_el_dia_de_la_semana_tambien_funciona() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.repo.addDay(routineId, "Día 1", weekday = 2)

        f.repo.updateDay(dayId, "Día 1", weekday = null, dayIndex = 0)

        assertNull(f.repo.routineDetail(routineId)!!.days.single().weekday)
    }

    @Test
    fun duplicar_copia_dias_ejercicios_y_prescripcion() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso/Pierna", "Mi programa", RoutineGoal.FUERZA)
        val day1 = f.repo.addDay(routineId, "Torso", weekday = 1)
        f.repo.addExerciseToDay(day1, f.bench, RoutineGoal.FUERZA)
        val day2 = f.repo.addDay(routineId, "Pierna", weekday = 4)
        f.repo.addExerciseToDay(day2, f.squat, RoutineGoal.FUERZA)

        val copyId = assertNotNull(f.repo.duplicateRoutine(routineId))

        val copy = assertNotNull(f.repo.routineDetail(copyId))
        assertEquals("Torso/Pierna (copia)", copy.name)
        assertEquals("Mi programa", copy.description)
        assertEquals(RoutineGoal.FUERZA, copy.goal)
        assertEquals(listOf("Torso", "Pierna"), copy.days.map { it.name })
        assertEquals(listOf(1, 4), copy.days.map { it.weekday })
        assertEquals(listOf("Press de banca"), copy.days[0].exercises.map { it.exerciseName })
        // La prescripción de fuerza viaja con el ejercicio.
        assertEquals(4, copy.days[0].exercises.single().targetSets)
        assertEquals(180, copy.days[0].exercises.single().restSeconds)
    }

    @Test
    fun la_copia_no_roba_el_puesto_de_rutina_activa() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Original", null, RoutineGoal.HIPERTROFIA)
        assertTrue(f.repo.routineDetail(routineId)!!.isActive)

        val copyId = assertNotNull(f.repo.duplicateRoutine(routineId))

        assertFalse(f.repo.routineDetail(copyId)!!.isActive)
        assertTrue(f.repo.routineDetail(routineId)!!.isActive)
    }

    @Test
    fun duplicar_dos_veces_no_repite_el_nombre() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)

        f.repo.duplicateRoutine(routineId)
        f.repo.duplicateRoutine(routineId)

        // Como conjunto: el orden alfabético entre "(copia)" y "(copia 2)" no
        // es el intuitivo, porque el espacio ordena antes que el paréntesis.
        val names = f.repo.routines().first().map { it.name }.toSet()
        assertEquals(setOf("Torso", "Torso (copia)", "Torso (copia 2)"), names)
    }

    @Test
    fun editar_la_copia_no_afecta_al_original() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.repo.addDay(routineId, "Día 1", null)
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)

        val copyId = assertNotNull(f.repo.duplicateRoutine(routineId))
        val copyExercise = f.repo.routineDetail(copyId)!!.days.single().exercises.single()
        f.repo.removeRoutineExercise(copyExercise.id)

        assertTrue(f.repo.routineDetail(copyId)!!.days.single().exercises.isEmpty())
        assertEquals(1, f.repo.routineDetail(routineId)!!.days.single().exercises.size)
    }

    @Test
    fun duplicar_una_rutina_que_no_existe_devuelve_null() = runTest {
        assertNull(Fixture().repo.duplicateRoutine(999))
    }
}
