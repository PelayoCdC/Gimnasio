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
    fun cambiar_las_series_de_la_rutina_las_pone_en_todos_los_ejercicios() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayOne = f.repo.addDay(routineId, "Día 1", weekday = null)
        val dayTwo = f.repo.addDay(routineId, "Día 2", weekday = null)
        f.repo.addExerciseToDay(dayOne, f.bench, RoutineGoal.HIPERTROFIA)
        f.repo.addExerciseToDay(dayTwo, f.squat, RoutineGoal.HIPERTROFIA)

        f.repo.setSetsForRoutine(routineId, 5)

        val exercises = f.repo.routineDetail(routineId)!!.days.flatMap { it.exercises }
        assertEquals(listOf(5, 5), exercises.map { it.targetSets })
        // Las repeticiones y el descanso se quedan como estaban.
        assertTrue(exercises.all { it.restSeconds == 90 })
    }

    @Test
    fun una_rutina_exportada_a_texto_se_vuelve_a_importar() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", "Mi plan", RoutineGoal.HIPERTROFIA)
        val dayId = f.repo.addDay(routineId, "Empuje", weekday = 1)
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)
        f.repo.addExerciseToDay(dayId, f.squat, RoutineGoal.HIPERTROFIA)

        val markdown = assertNotNull(f.repo.exportRoutineMarkdown(routineId))
        val result = assertNotNull(f.repo.importRoutineMarkdown(markdown))

        assertTrue(result.skippedExercises.isEmpty(), "no ha reconocido: ${result.skippedExercises}")
        assertEquals(2, result.exercises)
        // No pisa la original: se queda con las dos.
        assertEquals(2, f.repo.routines().first().size)

        val imported = assertNotNull(f.repo.routineDetail(result.routineId))
        assertEquals("Torso (2)", imported.name, "el nombre repetido se desambigua")
        assertEquals(RoutineGoal.HIPERTROFIA, imported.goal)
        val day = imported.days.single()
        assertEquals("Empuje", day.name)
        assertEquals(1, day.weekday)
        assertEquals(listOf("Press de banca", "Sentadilla"), day.exercises.map { it.exerciseName })
    }

    @Test
    fun importar_una_rutina_escrita_a_mano_salta_lo_que_no_conoce() = runTest {
        val f = Fixture()
        val texto = """
        # Rutina del gimnasio

        Objetivo: fuerza

        ## Lunes (lunes)
        - Press de banca 5x5 descanso 3 min
        - Ejercicio que no existe 3x10
        """.trimIndent()

        val result = assertNotNull(f.repo.importRoutineMarkdown(texto))

        assertEquals(1, result.exercises)
        assertEquals(listOf("Ejercicio que no existe"), result.skippedExercises)

        val exercise = f.repo.routineDetail(result.routineId)!!.days.single().exercises.single()
        assertEquals("Press de banca", exercise.exerciseName)
        assertEquals(5, exercise.targetSets, "respeta las series del texto")
        assertEquals(180, exercise.restSeconds, "y el descanso en minutos")
    }

    @Test
    fun un_fichero_que_no_es_una_rutina_no_crea_nada() = runTest {
        val f = Fixture()
        assertNull(f.repo.importRoutineMarkdown("hola, esto no es una rutina"))
        assertTrue(f.repo.routines().first().isEmpty())
    }

    @Test
    fun cambiar_un_ejercicio_por_otro_conserva_series_descanso_y_posicion() = runTest {
        val f = Fixture()
        val routineId = f.repo.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = f.repo.addDay(routineId, "Día 1", weekday = null)
        f.repo.addExerciseToDay(dayId, f.squat, RoutineGoal.HIPERTROFIA)
        f.repo.addExerciseToDay(dayId, f.bench, RoutineGoal.HIPERTROFIA)
        val press = f.repo.routineDetail(routineId)!!.days.single().exercises.last()
        f.repo.updateRoutineExercise(
            id = press.id,
            targetSets = 5,
            targetRepsMin = 6,
            targetRepsMax = 8,
            targetTimeSeconds = null,
            targetWeight = 60.0,
            restSeconds = 150,
            notes = null,
            position = press.position,
        )

        f.repo.replaceRoutineExercise(press.id, f.squat, RoutineGoal.HIPERTROFIA)

        val exercises = f.repo.routineDetail(routineId)!!.days.single().exercises
        assertEquals(2, exercises.size, "cambiar no debe añadir una fila")
        val swapped = exercises.last()
        assertEquals("Sentadilla", swapped.exerciseName)
        assertEquals(press.position, swapped.position, "se queda donde estaba")
        assertEquals(5, swapped.targetSets, "conserva las series")
        assertEquals(150, swapped.restSeconds, "conserva el descanso")
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
