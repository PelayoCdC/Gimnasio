package com.pelayo.gimnasio.ui.workout

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.ui.workout.routine.RoutineGeneratorScreen
import com.pelayo.gimnasio.ui.workout.routine.RoutineGeneratorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprueba que el generador pregunta por tiempo y nivel, y que la propuesta
 * responde a lo que se elige: el ajuste no sirve de nada si la pantalla no lo
 * traslada al ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoutineGeneratorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = createTestDatabase().apply {
            var wgerId = 1L
            val muscles = (1L..15L).toList()
            muscles.forEach { muscleId ->
                val others = muscles.filter { it != muscleId }.take(2)
                insertTestExercise(wgerId++, "Compuesto $muscleId", listOf(muscleId), others)
                repeat(3) { index ->
                    insertTestExercise(wgerId++, "Aislamiento $muscleId-$index", listOf(muscleId))
                }
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun showScreen() {
        val viewModel = RoutineGeneratorViewModel(
            exerciseRepository = ExerciseRepository(db, Dispatchers.Unconfined),
            routineRepository = RoutineRepository(db, Dispatchers.Unconfined),
        )
        compose.setContent {
            RoutineGeneratorScreen(onSaved = {}, onBack = {}, viewModel = viewModel)
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun generate() {
        scrollTo("Generar propuesta")
        compose.onNodeWithText("Generar propuesta").performClick()
    }

    @Test
    fun pregunta_por_el_tiempo_y_por_el_nivel() {
        showScreen()

        compose.onNodeWithText("¿Cuánto tiempo tienes por sesión?").assertExists()
        compose.onNodeWithText("45 min").assertExists()
        compose.onNodeWithText("¿Cuál es tu nivel?").assertExists()
        compose.onNodeWithText("Principiante").assertExists()
    }

    @Test
    fun la_propuesta_dice_cuanto_va_a_durar_la_sesion() {
        showScreen()
        generate()

        scrollTo("por sesión, calentamiento incluido")
        compose.onNode(hasText("por sesión, calentamiento incluido", substring = true)).assertExists()
    }

    @Test
    fun con_nivel_principiante_propone_cuerpo_completo() {
        showScreen()

        compose.onNodeWithText("Principiante").performClick()
        generate()

        scrollTo("Cuerpo completo A")
        compose.onNodeWithText("Cuerpo completo A").assertExists()
        scrollTo("Cuerpo completo C")
        compose.onNodeWithText("Cuerpo completo C").assertExists()
    }

    @Test
    fun el_tiempo_elegido_acorta_la_sesion_propuesta() {
        showScreen()

        // 60 min por defecto: seis ejercicios de cuatro series con 90 s de descanso.
        generate()
        scrollTo("unos 60 min")
        compose.onNode(hasText("unos 60 min", substring = true)).assertExists()

        // Con 30 min solo caben tres, y la estimación baja en consecuencia.
        scrollTo("30 min")
        compose.onNodeWithText("30 min").performClick()
        scrollTo("unos 34 min")
        compose.onNode(hasText("unos 34 min", substring = true)).assertExists()
    }
}
