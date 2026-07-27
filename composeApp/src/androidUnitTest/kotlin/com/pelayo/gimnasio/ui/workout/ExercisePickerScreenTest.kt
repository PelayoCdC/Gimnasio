package com.pelayo.gimnasio.ui.workout

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.ui.workout.routine.ExercisePickerScreen
import com.pelayo.gimnasio.ui.workout.routine.ExercisePickerViewModel
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

/** El buscador: que encuentre por palabras sueltas y que deje mirar antes de añadir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExercisePickerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb
    private lateinit var routines: RoutineRepository
    private var routineId = 0L
    private var dayId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = createTestDatabase()
        routines = RoutineRepository(db, Dispatchers.Unconfined)

        db.catalogQueries.upsertExerciseByWgerId(
            nameEs = "Press de banca inclinado",
            nameEn = null,
            descriptionEs = "Túmbate en el banco a 30 grados y empuja.",
            categoryId = null,
            videoUrl = null,
            metValue = 3.5,
            exerciseType = "REPS",
            wgerId = 1L,
            searchName = "press de banca inclinado",
        )
        db.insertTestExercise(2, "Elevación lateral", listOf(2L))

        runBlocking {
            routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
            dayId = routines.addDay(routineId, "Empuje", weekday = null)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun showScreen() {
        val viewModel = ExercisePickerViewModel(
            dayId = dayId,
            routineId = routineId,
            exerciseRepository = ExerciseRepository(db, Dispatchers.Unconfined),
            routineRepository = routines,
        )
        compose.setContent { ExercisePickerScreen(dayId, routineId, onDone = {}, viewModel = viewModel) }
    }

    private fun exercisesInDay(): List<String> = runBlocking {
        assertNotNull(routines.routineDetail(routineId)).days.single().exercises.map { it.exerciseName }
    }

    private fun countOf(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /**
     * La lista pasa por un `debounce` de 200 ms que corre en tiempo real, no en el
     * reloj de Compose, así que hay que esperar de verdad a que llegue.
     */
    private fun waitForCount(text: String, expected: Int) {
        compose.waitUntil(timeoutMillis = 5_000) { countOf(text) == expected }
    }

    @Test
    fun busca_por_palabras_sueltas_sin_tildes() {
        showScreen()
        waitForCount("Press de banca inclinado", 1)

        // Ni "inclinado press" ni "press inclinado" aparecen literales en el nombre.
        compose.onNodeWithText("Buscar ejercicio…").performTextReplacement("inclinado press")
        waitForCount("Elevación lateral", 0)
        compose.onNodeWithText("Press de banca inclinado").assertExists()

        // Con el campo escrito ya no hay marcador de posición: se busca por su texto.
        compose.onNodeWithText("inclinado press").performTextReplacement("elevacion")
        waitForCount("Elevación lateral", 1)
        compose.onAllNodesWithText("Press de banca inclinado").assertCountEquals(0)
    }

    @Test
    fun tocar_el_ejercicio_ensena_la_ficha_sin_anadirlo() {
        showScreen()
        waitForCount("Press de banca inclinado", 1)

        compose.onNodeWithText("Press de banca inclinado").performClick()

        compose.onNode(hasText("Túmbate en el banco", substring = true)).assertExists()
        assertEquals(emptyList(), exercisesInDay(), "mirar la ficha no debe añadir nada")
    }

    @Test
    fun desde_la_ficha_se_puede_anadir() {
        showScreen()
        waitForCount("Press de banca inclinado", 1)

        compose.onNodeWithText("Press de banca inclinado").performClick()
        compose.onNodeWithText("Añadir a la rutina").performClick()

        assertEquals(listOf("Press de banca inclinado"), exercisesInDay())
    }

    @Test
    fun el_boton_de_la_lista_anade_directamente() {
        showScreen()
        waitForCount("Elevación lateral", 1)

        compose.onNodeWithContentDescription("Añadir Elevación lateral").performClick()

        assertEquals(listOf("Elevación lateral"), exercisesInDay())
    }
}
