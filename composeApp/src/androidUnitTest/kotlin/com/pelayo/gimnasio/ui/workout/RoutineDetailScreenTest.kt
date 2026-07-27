package com.pelayo.gimnasio.ui.workout

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.ui.workout.routine.RoutineDetailScreen
import com.pelayo.gimnasio.ui.workout.routine.RoutineDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cubre lo que se hace desde el detalle de la rutina y toca la base de datos:
 * copiar un día a otra rutina y aplicar una sugerencia.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoutineDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb
    private lateinit var routines: RoutineRepository
    private lateinit var exercises: ExerciseRepository
    private var routineId = 0L
    private var dayId = 0L
    private var otherRoutineId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = createTestDatabase()
        routines = RoutineRepository(db, Dispatchers.Unconfined)
        exercises = ExerciseRepository(db, Dispatchers.Unconfined)

        // Catálogo con ejercicios para todos los músculos de la semilla, para que
        // el consejero tenga con qué rellenar los grupos que falten.
        var wgerId = 1L
        (1L..15L).forEach { muscleId ->
            db.insertTestExercise(wgerId++, "Compuesto $muscleId", listOf(muscleId), listOf(1L, 2L))
            db.insertTestExercise(wgerId++, "Aislamiento $muscleId", listOf(muscleId))
        }
        val bench = db.catalogQueries.selectExerciseIdByWgerId(1L).executeAsOne()

        runBlocking {
            routineId = routines.createRoutine("Rutina A", null, RoutineGoal.HIPERTROFIA)
            dayId = routines.addDay(routineId, "Empuje", weekday = null)
            routines.addExerciseToDay(dayId, bench, RoutineGoal.HIPERTROFIA)
            otherRoutineId = routines.createRoutine("Rutina B", null, RoutineGoal.FUERZA)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun showScreen() {
        val viewModel = RoutineDetailViewModel(
            routineId = routineId,
            repository = routines,
            workoutRepository = WorkoutRepository(db, Dispatchers.Unconfined),
            exerciseRepository = exercises,
        )
        compose.setContent {
            RoutineDetailScreen(
                routineId = routineId,
                onBack = {},
                onAddExercise = {},
                onOpenExercise = {},
                onStartWorkout = {},
                viewModel = viewModel,
            )
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun copiar_un_dia_lo_lleva_a_la_rutina_elegida() {
        showScreen()

        // Por el nombre del día no vale: las sugerencias lo mencionan y el
        // desplazamiento se quedaría en ellas, encima de la tarjeta.
        scrollTo("Sin día asignado")
        compose.onNodeWithContentDescription("Copiar día a otra rutina").performClick()
        compose.onNodeWithText("Rutina B").performClick()

        val target = runBlocking { assertNotNull(routines.routineDetail(otherRoutineId)) }
        assertEquals(listOf("Empuje"), target.days.map { it.name })
        assertEquals(1, target.days.single().exercises.size)
    }

    @Test
    fun el_dialogo_avisa_cuando_no_hay_otra_rutina() {
        runBlocking { routines.deleteRoutine(otherRoutineId) }
        showScreen()

        scrollTo("Sin día asignado")
        compose.onNodeWithContentDescription("Copiar día a otra rutina").performClick()

        compose.onNode(hasText("No hay otra rutina", substring = true)).assertExists()
    }

    @Test
    fun aplicar_una_sugerencia_mete_el_ejercicio_en_el_dia() {
        showScreen()

        // Hay que traer el botón a la ventana: lo que queda debajo ni se compone,
        // y un clic fuera de ella se descarta.
        scrollTo("Añadir")
        compose.onNodeWithText("Sugerencias").assertExists()
        compose.onAllNodesWithText("Añadir")[0].performClick()

        val day = runBlocking { assertNotNull(routines.routineDetail(routineId)).days.single() }
        assertTrue(day.exercises.size == 2, "la sugerencia no ha añadido nada: ${day.exercises}")
    }

    @Test
    fun una_sugerencia_descartada_desaparece_de_la_lista() {
        showScreen()

        scrollTo("Ahora no")
        val antes = compose.onAllNodesWithText("Ahora no").fetchSemanticsNodes().size
        assertTrue(antes > 0, "no hay ninguna sugerencia que descartar")

        compose.onAllNodesWithText("Ahora no")[0].performClick()

        assertEquals(antes - 1, compose.onAllNodesWithText("Ahora no").fetchSemanticsNodes().size)
        // Descartar no toca la rutina.
        val day = runBlocking { assertNotNull(routines.routineDetail(routineId)).days.single() }
        assertEquals(1, day.exercises.size)
    }
}
