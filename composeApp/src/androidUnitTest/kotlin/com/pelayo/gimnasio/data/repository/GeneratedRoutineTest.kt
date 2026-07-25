package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.calc.CoverageLevel
import com.pelayo.gimnasio.domain.calc.RoutineGenerator
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.db.GimnasioDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprueba el circuito completo del generador: catálogo real en base de datos →
 * propuesta → persistencia → análisis de balance sobre lo guardado.
 */
class GeneratedRoutineTest {

    /** Siembra un catálogo con ejercicios para cada uno de los 15 músculos de wger. */
    private fun GimnasioDb.seedCatalog() {
        var wgerId = 1L
        // (músculo primario, secundarios) por cada músculo de la semilla.
        val muscles = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L)
        muscles.forEach { muscleId ->
            val others = muscles.filter { it != muscleId }.take(2)
            insertTestExercise(wgerId++, "Compuesto $muscleId", listOf(muscleId), others)
            repeat(3) { index ->
                insertTestExercise(wgerId++, "Aislamiento $muscleId-$index", listOf(muscleId))
            }
        }
    }

    @Test
    fun genera_guarda_y_la_rutina_queda_equilibrada() = runTest {
        val db = createTestDatabase().apply { seedCatalog() }
        val exercises = ExerciseRepository(db, Dispatchers.Unconfined)
        val routines = RoutineRepository(db, Dispatchers.Unconfined)

        val proposal = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = exercises.candidates(),
        )
        assertTrue(proposal.uncoveredGroups.isEmpty(), "sin cubrir: ${proposal.uncoveredGroups}")

        val routineId = routines.saveGenerated(proposal)

        val saved = assertNotNull(routines.routineDetail(routineId))
        assertEquals(3, saved.days.size)
        assertEquals(listOf("Empuje", "Tirón", "Pierna"), saved.days.map { it.name })
        assertTrue(saved.days.all { it.exercises.isNotEmpty() })
        // Cada ejercicio guardado lleva la prescripción de hipertrofia.
        assertTrue(saved.days.flatMap { it.exercises }.all { it.targetSets == 4 && it.targetRepsMax == 12 })

        // El balance calculado desde la base de datos no deja ningún grupo fuera.
        val report = routines.balanceReport(routineId)
        val ausentes = report.scores.filter { it.level == CoverageLevel.AUSENTE }.map { it.group.name }
        assertTrue(ausentes.isEmpty(), "grupos sin entrenar tras guardar: $ausentes")
    }

    @Test
    fun los_dias_guardados_conservan_el_orden_de_la_propuesta() = runTest {
        val db = createTestDatabase().apply { seedCatalog() }
        val exercises = ExerciseRepository(db, Dispatchers.Unconfined)
        val routines = RoutineRepository(db, Dispatchers.Unconfined)

        val proposal = RoutineGenerator.generate(6, RoutineGoal.FUERZA, exercises.candidates())
        val routineId = routines.saveGenerated(proposal)

        val saved = assertNotNull(routines.routineDetail(routineId))
        assertEquals(proposal.days.map { it.name }, saved.days.map { it.name })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), saved.days.map { it.dayIndex })
    }

    @Test
    fun la_rutina_generada_queda_marcada_como_generada() = runTest {
        val db = createTestDatabase().apply { seedCatalog() }
        val exercises = ExerciseRepository(db, Dispatchers.Unconfined)
        val routines = RoutineRepository(db, Dispatchers.Unconfined)

        val routineId = routines.saveGenerated(
            RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, exercises.candidates()),
        )

        assertEquals(1L, db.routinesQueries.selectRoutineById(routineId).executeAsOne().isGenerated)
    }
}
