package com.pelayo.gimnasio.ui.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.ui.workout.active.WorkoutSummaryScreen
import com.pelayo.gimnasio.ui.workout.active.WorkoutSummaryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * El resumen dejó de ser de solo lectura: corregir una serie mal apuntada es lo
 * que evita que un dedazo contamine gráficas y sugerencias para siempre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutSummaryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb
    private lateinit var workouts: WorkoutRepository
    private lateinit var profiles: ProfileRepository
    private var sessionId = 0L
    private var benchId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = createTestDatabase()
        val routines = RoutineRepository(db, Dispatchers.Unconfined)
        workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        profiles = ProfileRepository(db, Dispatchers.Unconfined)
        benchId = db.insertTestExercise(1, "Press de banca", listOf(4L))

        runBlocking {
            val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
            val dayId = routines.addDay(routineId, "Día 1", null)
            routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
            sessionId = workouts.startSession(dayId, LocalDate(2026, 7, 25))
            // Dedazo a propósito: eran 10 kg.
            workouts.logSet(sessionId, benchId, 1, 100.0, 10, null)
            workouts.completeSession(sessionId, 45, null, null)
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun showScreen() {
        compose.setContent {
            WorkoutSummaryScreen(
                sessionId = sessionId,
                onDone = {},
                viewModel = WorkoutSummaryViewModel(sessionId, workouts, profiles),
            )
        }
    }

    /**
     * El resumen es un LazyColumn: lo que queda por debajo ni siquiera se
     * compone, así que hay que desplazarse hasta ello antes de buscarlo.
     */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun enseña_las_series_registradas() {
        showScreen()

        compose.onNodeWithText("Día 1").assertIsDisplayed()
        scrollTo("Serie 1")
        compose.onNodeWithText("Serie 1: 100 kg × 10").assertExists()
    }

    @Test
    fun corregir_una_serie_actualiza_la_pantalla_y_la_base_de_datos() {
        showScreen()

        scrollTo("Serie 1")
        compose.onNodeWithText("Serie 1: 100 kg × 10").performClick()
        compose.onNodeWithText("Peso (kg)").performTextReplacement("10")
        compose.onNodeWithText("Guardar").performClick()

        compose.onNodeWithText("Serie 1: 10 kg × 10").assertExists()
        assertEquals(10.0, runBlocking { workouts.progressHistory(benchId).single().maxWeight })
    }

    @Test
    fun borrar_una_serie_desde_el_dialogo_la_quita() {
        showScreen()

        scrollTo("Serie 1")
        compose.onNodeWithText("Serie 1: 100 kg × 10").performClick()
        // El diálogo creció con el campo de RPE: el botón hay que traerlo a la vista.
        compose.onNodeWithText("Borrar esta serie").performScrollTo().performClick()

        // Borra al momento; la red de seguridad es el "Deshacer" del snackbar.
        assertEquals(0, runBlocking { workouts.summary(sessionId)!!.totalSets })
        compose.onNodeWithText("Deshacer").assertExists()
    }

    @Test
    fun las_notas_del_dia_se_guardan() {
        showScreen()

        scrollTo("Notas del día")
        compose.onNodeWithText("Sin notas").performTextReplacement("Dormí fatal, bajé el peso")
        compose.onNodeWithText("Guardar notas").performClick()

        assertEquals(
            "Dormí fatal, bajé el peso",
            runBlocking { workouts.summary(sessionId)!!.notes },
        )
    }

    @Test
    fun borrar_el_entrenamiento_pide_confirmacion_antes() {
        showScreen()

        compose.onNodeWithContentDescription("Borrar entrenamiento").performClick()
        compose.onNodeWithText("Borrar entrenamiento").assertIsDisplayed()

        // Cancelar no borra nada.
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1L, runBlocking { workouts.completedSessionCount() })

        compose.onNodeWithContentDescription("Borrar entrenamiento").performClick()
        compose.onNodeWithText("Borrar").performClick()

        assertEquals(0L, runBlocking { workouts.completedSessionCount() })
        assertNull(runBlocking { workouts.summary(sessionId) })
    }
}
