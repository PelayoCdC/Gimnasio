package com.pelayo.gimnasio.ui.workout

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.platform.RestTimerNotifier
import com.pelayo.gimnasio.ui.workout.active.ActiveWorkoutScreen
import com.pelayo.gimnasio.ui.workout.active.ActiveWorkoutViewModel
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
import org.robolectric.RuntimeEnvironment

/**
 * Prueba la pantalla de entrenamiento con su ViewModel y una base de datos de
 * verdad: es el recorrido donde más cosas pueden romperse en silencio
 * (prerrelleno, contador de series, sugerencia de subir peso).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ActiveWorkoutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var profiles: ProfileRepository
    private var benchId = 0L
    private var dayId = 0L

    @Before
    fun setUp() {
        // El viewModelScope despacha en Main, que en la JVM no existe.
        Dispatchers.setMain(Dispatchers.Unconfined)

        db = createTestDatabase()
        routines = RoutineRepository(db, Dispatchers.Unconfined)
        workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        profiles = ProfileRepository(db, Dispatchers.Unconfined)

        benchId = db.insertTestExercise(1, "Press de banca", listOf(4L), listOf(5L))
        runBlocking {
            val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
            dayId = routines.addDay(routineId, "Día 1 – Torso", null)
            routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelFor(sessionId: Long) = ActiveWorkoutViewModel(
        sessionId = sessionId,
        repository = workouts,
        profileRepository = profiles,
        notifier = RestTimerNotifier(RuntimeEnvironment.getApplication()),
    )

    private fun startSession(): Long = runBlocking { workouts.startSession(dayId, LocalDate(2026, 7, 25)) }

    private fun showScreen(sessionId: Long) {
        compose.setContent {
            ActiveWorkoutScreen(
                sessionId = sessionId,
                onFinished = {},
                onCancel = {},
                onOpenExercise = {},
                viewModel = viewModelFor(sessionId),
            )
        }
    }

    @Test
    fun enseña_el_ejercicio_del_dia_y_el_objetivo_de_series() {
        showScreen(startSession())

        compose.onNodeWithText("Día 1 – Torso").assertIsDisplayed()
        compose.onNodeWithText("Press de banca").assertIsDisplayed()
        compose.onNodeWithText("Objetivo: 0/4 series").assertIsDisplayed()
    }

    @Test
    fun un_ejercicio_de_tiempo_pide_segundos_y_ofrece_cronometro() {
        // La plancha se aguanta, no se repite: ni kilos ni reps.
        val plankId = db.insertTestExercise(2, "Plancha de antebrazo", listOf(6L))
        val sessionId = runBlocking {
            val routineId = routines.createRoutine("Core", null, RoutineGoal.HIPERTROFIA)
            val coreDay = routines.addDay(routineId, "Core", null)
            routines.addExerciseToDay(coreDay, plankId, RoutineGoal.HIPERTROFIA)
            workouts.startSession(coreDay, LocalDate(2026, 7, 25))
        }

        showScreen(sessionId)

        compose.onNodeWithText("Segundos").assertExists()
        compose.onNodeWithContentDescription("Cronometrar la serie").assertExists()
        compose.onAllNodesWithText("kg").assertCountEquals(0)
        compose.onAllNodesWithText("Reps").assertCountEquals(0)
    }

    @Test
    fun un_ejercicio_de_peso_corporal_no_pide_kilos_pero_deja_ponerlos() {
        val crunchId = db.insertTestExercise(3, "Crunch abdominal", listOf(6L))
        val sessionId = runBlocking {
            val routineId = routines.createRoutine("Abdomen", null, RoutineGoal.HIPERTROFIA)
            val coreDay = routines.addDay(routineId, "Abdomen", null)
            routines.addExerciseToDay(coreDay, crunchId, RoutineGoal.HIPERTROFIA)
            workouts.startSession(coreDay, LocalDate(2026, 7, 25))
        }

        showScreen(sessionId)

        compose.onNodeWithText("Reps").assertExists()
        compose.onAllNodesWithText("kg").assertCountEquals(0)

        compose.onNodeWithText("Añadir peso").performClick()
        compose.onNodeWithText("kg").assertExists()
    }

    @Test
    fun registrar_una_serie_la_lista_y_avanza_el_contador() {
        showScreen(startSession())

        compose.onNodeWithText("kg").performTextReplacement("60")
        compose.onNodeWithText("Reps").performTextReplacement("10")
        compose.onNodeWithContentDescription("Registrar serie").performClick()

        compose.onNodeWithText("Serie 1").assertExists()
        compose.onNodeWithText("60 kg × 10").assertExists()
        compose.onNodeWithText("Objetivo: 1/4 series").assertExists()
    }

    @Test
    fun la_siguiente_serie_hereda_el_peso_y_las_reps_de_la_recien_apuntada() {
        showScreen(startSession())

        compose.onNodeWithText("kg").performTextReplacement("62.5")
        compose.onNodeWithText("Reps").performTextReplacement("10")
        compose.onNodeWithContentDescription("Registrar serie").performClick()

        // Los campos quedan listos para repetir la serie sin teclear nada.
        compose.onNode(hasText("62.5") and hasSetTextAction()).assertExists()
        compose.onNode(hasText("10") and hasSetTextAction()).assertExists()
    }

    @Test
    fun sin_series_hoy_el_prerrelleno_sale_de_la_sesion_anterior() {
        runBlocking {
            val previous = workouts.startSession(dayId, LocalDate(2026, 7, 18))
            workouts.logSet(previous, benchId, 1, 57.5, 11, null)
            workouts.completeSession(previous, 45, null, null)
        }

        showScreen(startSession())

        compose.onNode(hasText("57.5") and hasSetTextAction()).assertExists()
        compose.onNode(hasText("11") and hasSetTextAction()).assertExists()
    }

    @Test
    fun el_boton_de_registrar_esta_apagado_sin_repeticiones() {
        showScreen(startSession())

        compose.onNodeWithText("Reps").performTextReplacement("")
        compose.onNodeWithContentDescription("Registrar serie").assertIsNotEnabled()
    }

    @Test
    fun repetir_un_dia_enseña_lo_que_se_hizo_la_vez_anterior() {
        // Antes solo salía un resumen y desaparecía al apuntar la primera serie.
        runBlocking {
            val previous = workouts.startSession(dayId, LocalDate(2026, 7, 18))
            workouts.logSet(previous, benchId, 1, 60.0, 12, null)
            workouts.logSet(previous, benchId, 2, 60.0, 10, null)
            workouts.completeSession(previous, 45, null, null)
        }

        showScreen(startSession())

        compose.onNode(hasText("Última vez", substring = true)).assertExists()
        compose.onNodeWithText("Serie 1: 60 kg × 12").assertExists()
        compose.onNodeWithText("Serie 2: 60 kg × 10").assertExists()

        // Y sigue estando después de registrar una serie.
        compose.onNodeWithText("Reps").performTextReplacement("12")
        compose.onNodeWithContentDescription("Registrar serie").performClick()
        compose.onNodeWithText("Serie 2: 60 kg × 10").assertExists()
    }

    @Test
    fun tras_dos_sesiones_al_tope_propone_subir_peso() {
        runBlocking {
            listOf(LocalDate(2026, 7, 10), LocalDate(2026, 7, 17)).forEach { date ->
                val id = workouts.startSession(dayId, date)
                repeat(4) { index -> workouts.logSet(id, benchId, index + 1, 60.0, 12, null) }
                workouts.completeSession(id, 45, null, null)
            }
        }

        showScreen(startSession())

        compose.onNodeWithText("Toca subir a 62.5 kg").assertExists()
        compose.onAllNodes(hasText("las últimas 2 sesiones", substring = true)).assertCountEquals(1)
    }

    @Test
    fun un_dia_sin_ejercicios_lo_dice_en_vez_de_quedarse_en_blanco() {
        val emptyDayId = runBlocking {
            val routineId = routines.createRoutine("Vacía", null, RoutineGoal.FUERZA)
            routines.addDay(routineId, "Día vacío", null)
        }
        val sessionId = runBlocking { workouts.startSession(emptyDayId, LocalDate(2026, 7, 25)) }

        showScreen(sessionId)

        compose.onNodeWithText("Este día no tiene ejercicios").assertIsDisplayed()
    }
}
