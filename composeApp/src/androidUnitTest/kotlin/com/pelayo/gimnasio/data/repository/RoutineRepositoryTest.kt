package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.calc.CoverageLevel
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

/**
 * Ejercita el esquema completo contra SQLite: crear rutina, días, ejercicios y
 * calcular el balance muscular a partir de los datos realmente persistidos.
 */
class RoutineRepositoryTest {

    // Músculos según la semilla de wger: 4 pectoral, 12 dorsal, 10 cuádriceps, 5 tríceps, 1 bíceps.
    private fun setup() = createTestDatabase()

    @Test
    fun crea_rutina_con_dias_y_ejercicios_y_los_recupera() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)

        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L), listOf(5L))
        val rowId = db.insertTestExercise(2, "Remo con barra", listOf(12L), listOf(1L))

        val routineId = repo.createRoutine("Torso", "Prueba", RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Día 1 – Torso", weekday = 1)
        repo.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
        repo.addExerciseToDay(dayId, rowId, RoutineGoal.HIPERTROFIA)

        val detail = assertNotNull(repo.routineDetail(routineId))
        assertEquals("Torso", detail.name)
        assertEquals(RoutineGoal.HIPERTROFIA, detail.goal)
        assertTrue(detail.isActive, "la primera rutina creada debe quedar activa")

        val day = detail.days.single()
        assertEquals("Día 1 – Torso", day.name)
        assertEquals(1, day.weekday)
        assertEquals(listOf("Press de banca", "Remo con barra"), day.exercises.map { it.exerciseName })

        // La prescripción de hipertrofia es 4 × 8-12 con 90 s de descanso.
        val bench = day.exercises.first()
        assertEquals(4, bench.targetSets)
        assertEquals(8, bench.targetRepsMin)
        assertEquals(12, bench.targetRepsMax)
        assertEquals(90, bench.restSeconds)
        assertEquals("4 × 8-12", bench.targetLabel)
        assertNull(bench.targetTimeSeconds)
    }

    @Test
    fun ejercicio_de_tiempo_recibe_segundos_en_vez_de_repeticiones() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val plankId = db.insertTestExercise(3, "Plancha", listOf(6L), exerciseType = "TIME")

        val routineId = repo.createRoutine("Core", null, RoutineGoal.RESISTENCIA)
        val dayId = repo.addDay(routineId, "Core", null)
        repo.addExerciseToDay(dayId, plankId, RoutineGoal.RESISTENCIA)

        val exercise = repo.routineDetail(routineId)!!.days.single().exercises.single()
        assertEquals(60, exercise.targetTimeSeconds)
        assertNull(exercise.targetRepsMin)
        assertEquals("3 × 60 s", exercise.targetLabel)
    }

    @Test
    fun balance_detecta_grupos_ausentes_en_rutina_solo_de_torso() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L), listOf(5L))
        val rowId = db.insertTestExercise(2, "Remo con barra", listOf(12L), listOf(1L))

        val routineId = repo.createRoutine("Solo torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Torso", null)
        repo.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
        repo.addExerciseToDay(dayId, rowId, RoutineGoal.HIPERTROFIA)

        val report = repo.balanceReport(routineId)
        assertFalse(report.isBalanced)

        val ausentes = report.scores.filter { it.level == CoverageLevel.AUSENTE }.map { it.group.name }
        assertTrue("Cuádriceps" in ausentes, "faltan piernas: $ausentes")
        assertTrue("Glúteos" in ausentes)
        assertTrue(report.warnings.any { it.message.contains("Cuádriceps") })

        // Pecho: 4 series como primario = 4.0; tríceps: 4 como secundario = 2.0.
        val pecho = report.scores.first { it.group.name == "Pecho" }
        assertEquals(4.0, pecho.effectiveSets)
        val triceps = report.scores.first { it.group.name == "Tríceps" }
        assertEquals(2.0, triceps.effectiveSets)
    }

    @Test
    fun borrar_dia_arrastra_sus_ejercicios() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val id = db.insertTestExercise(1, "Sentadilla", listOf(10L))

        val routineId = repo.createRoutine("Piernas", null, RoutineGoal.FUERZA)
        val dayId = repo.addDay(routineId, "Piernas", null)
        repo.addExerciseToDay(dayId, id, RoutineGoal.FUERZA)
        assertEquals(1, repo.routineDetail(routineId)!!.days.single().exercises.size)

        repo.deleteDay(dayId)

        assertTrue(repo.routineDetail(routineId)!!.days.isEmpty())
        // Sin días no queda volumen: el balance debe estar todo a cero.
        assertTrue(repo.balanceReport(routineId).scores.all { it.effectiveSets == 0.0 })
    }

    @Test
    fun lista_de_rutinas_cuenta_dias_y_ejercicios() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val squat = db.insertTestExercise(1, "Sentadilla", listOf(10L))
        val bench = db.insertTestExercise(2, "Press banca", listOf(4L))

        val routineId = repo.createRoutine("Full body", null, RoutineGoal.FUERZA)
        val d1 = repo.addDay(routineId, "A", null)
        val d2 = repo.addDay(routineId, "B", null)
        repo.addExerciseToDay(d1, squat, RoutineGoal.FUERZA)
        repo.addExerciseToDay(d2, bench, RoutineGoal.FUERZA)

        val summary = repo.routines().first().single()
        assertEquals("Full body", summary.name)
        assertEquals(2, summary.dayCount)
        assertEquals(2, summary.exerciseCount)
        assertTrue(summary.isActive)
    }

    @Test
    fun solo_una_rutina_puede_estar_activa() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val first = repo.createRoutine("Primera", null, RoutineGoal.FUERZA)
        val second = repo.createRoutine("Segunda", null, RoutineGoal.HIPERTROFIA)

        repo.setActive(second)

        val routines = repo.routines().first()
        assertEquals(listOf(false, true), listOf(first, second).map { id ->
            routines.first { it.id == id }.isActive
        })
    }

    @Test
    fun subir_y_bajar_ejercicios_cambia_el_orden() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val a = db.insertTestExercise(1, "A", listOf(4L))
        val b = db.insertTestExercise(2, "B", listOf(12L))
        val c = db.insertTestExercise(3, "C", listOf(10L))

        val routineId = repo.createRoutine("Orden", null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Día", null)
        listOf(a, b, c).forEach { repo.addExerciseToDay(dayId, it, RoutineGoal.HIPERTROFIA) }

        suspend fun names() = repo.routineDetail(routineId)!!.days.single().exercises.map { it.exerciseName }
        suspend fun idOf(name: String) =
            repo.routineDetail(routineId)!!.days.single().exercises.first { it.exerciseName == name }.id

        assertEquals(listOf("A", "B", "C"), names())

        repo.moveRoutineExercise(idOf("C"), -1)
        assertEquals(listOf("A", "C", "B"), names())

        repo.moveRoutineExercise(idOf("A"), 1)
        assertEquals(listOf("C", "A", "B"), names())
    }

    @Test
    fun reordenar_en_lote_aplica_el_orden_del_arrastre() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val a = db.insertTestExercise(1, "A", listOf(4L))
        val b = db.insertTestExercise(2, "B", listOf(12L))
        val c = db.insertTestExercise(3, "C", listOf(10L))

        val routineId = repo.createRoutine("Orden", null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Día", null)
        listOf(a, b, c).forEach { repo.addExerciseToDay(dayId, it, RoutineGoal.HIPERTROFIA) }

        val items = repo.routineDetail(routineId)!!.days.single().exercises
        fun rowId(name: String) = items.first { it.exerciseName == name }.id

        // El orden que dejó el arrastre, más un id fantasma que debe ignorarse.
        repo.reorderRoutineExercises(dayId, listOf(rowId("C"), rowId("A"), rowId("B"), 9999L))

        assertEquals(
            listOf("C", "A", "B"),
            repo.routineDetail(routineId)!!.days.single().exercises.map { it.exerciseName },
        )
    }

    @Test
    fun mover_fuera_de_los_extremos_no_hace_nada() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val a = db.insertTestExercise(1, "A", listOf(4L))
        val b = db.insertTestExercise(2, "B", listOf(12L))

        val routineId = repo.createRoutine("Orden", null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Día", null)
        listOf(a, b).forEach { repo.addExerciseToDay(dayId, it, RoutineGoal.HIPERTROFIA) }

        val first = repo.routineDetail(routineId)!!.days.single().exercises.first()
        val last = repo.routineDetail(routineId)!!.days.single().exercises.last()

        repo.moveRoutineExercise(first.id, -1)
        repo.moveRoutineExercise(last.id, 1)

        assertEquals(
            listOf("A", "B"),
            repo.routineDetail(routineId)!!.days.single().exercises.map { it.exerciseName },
        )
    }

    @Test
    fun al_borrar_un_ejercicio_las_posiciones_se_renumeran() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val a = db.insertTestExercise(1, "A", listOf(4L))
        val b = db.insertTestExercise(2, "B", listOf(12L))
        val c = db.insertTestExercise(3, "C", listOf(10L))

        val routineId = repo.createRoutine("Orden", null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Día", null)
        listOf(a, b, c).forEach { repo.addExerciseToDay(dayId, it, RoutineGoal.HIPERTROFIA) }

        val middle = repo.routineDetail(routineId)!!.days.single().exercises[1]
        repo.removeRoutineExercise(middle.id)

        // Sin renumerar quedaría 0 y 2, y el reordenado posterior fallaría.
        val remaining = repo.routineDetail(routineId)!!.days.single().exercises
        assertEquals(listOf("A", "C"), remaining.map { it.exerciseName })
        assertEquals(listOf(0, 1), remaining.map { it.position })

        repo.moveRoutineExercise(remaining.last().id, -1)
        assertEquals(
            listOf("C", "A"),
            repo.routineDetail(routineId)!!.days.single().exercises.map { it.exerciseName },
        )
    }

    @Test
    fun el_orden_de_un_dia_no_afecta_a_otro() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val a = db.insertTestExercise(1, "A", listOf(4L))
        val b = db.insertTestExercise(2, "B", listOf(12L))

        val routineId = repo.createRoutine("Dos días", null, RoutineGoal.HIPERTROFIA)
        val day1 = repo.addDay(routineId, "Uno", null)
        val day2 = repo.addDay(routineId, "Dos", null)
        repo.addExerciseToDay(day1, a, RoutineGoal.HIPERTROFIA)
        repo.addExerciseToDay(day1, b, RoutineGoal.HIPERTROFIA)
        repo.addExerciseToDay(day2, a, RoutineGoal.HIPERTROFIA)
        repo.addExerciseToDay(day2, b, RoutineGoal.HIPERTROFIA)

        val firstOfDay1 = repo.routineDetail(routineId)!!.days.first().exercises.last()
        repo.moveRoutineExercise(firstOfDay1.id, -1)

        val detail = repo.routineDetail(routineId)!!
        assertEquals(listOf("B", "A"), detail.days[0].exercises.map { it.exerciseName })
        assertEquals(listOf("A", "B"), detail.days[1].exercises.map { it.exerciseName })
    }

    @Test
    fun editar_series_y_descanso_persiste() = runTest {
        val db = setup()
        val repo = RoutineRepository(db, Dispatchers.Unconfined)
        val id = db.insertTestExercise(1, "Curl", listOf(1L))
        val routineId = repo.createRoutine("Brazo", null, RoutineGoal.HIPERTROFIA)
        val dayId = repo.addDay(routineId, "Brazo", null)
        repo.addExerciseToDay(dayId, id, RoutineGoal.HIPERTROFIA)

        val original = repo.routineDetail(routineId)!!.days.single().exercises.single()
        repo.updateRoutineExercise(
            id = original.id,
            targetSets = 5,
            targetRepsMin = 6,
            targetRepsMax = 8,
            targetTimeSeconds = null,
            targetWeight = 22.5,
            restSeconds = 120,
            notes = "Serie descendente",
            position = original.position,
        )

        val updated = repo.routineDetail(routineId)!!.days.single().exercises.single()
        assertEquals(5, updated.targetSets)
        assertEquals(6, updated.targetRepsMin)
        assertEquals(22.5, updated.targetWeight)
        assertEquals(120, updated.restSeconds)
        assertEquals("Serie descendente", updated.notes)
    }
}
