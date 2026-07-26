package com.pelayo.gimnasio.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.RoutineGoal
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
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
import kotlin.test.assertTrue

/**
 * Inicio es la primera pantalla y la que decide qué hace el usuario a
 * continuación, así que sus estados vacíos importan tanto como los llenos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var nutrition: NutritionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = createTestDatabase()
        routines = RoutineRepository(db, Dispatchers.Unconfined)
        workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        profiles = ProfileRepository(db, Dispatchers.Unconfined)

        val engine = MockEngine {
            respond(
                content = """{"count":0,"hits":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(HttpClientFactory.json) } }
        nutrition = NutritionRepository(db, OpenFoodFactsApi(client), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun showScreen() {
        compose.setContent {
            HomeScreen(
                onStartWorkout = {},
                onOpenRoutines = {},
                onOpenCatalog = {},
                onOpenNutrition = {},
                onOpenProfile = {},
                viewModel = HomeViewModel(routines, workouts, profiles, nutrition),
            )
        }
    }

    @Test
    fun sin_catalogo_ni_perfil_guia_por_donde_empezar() {
        showScreen()

        compose.onNodeWithText("Descarga el catálogo de ejercicios").assertIsDisplayed()
        compose.onNodeWithText("Completa tu perfil").assertExists()
        compose.onNodeWithText("Sin rutina activa").assertExists()
    }

    @Test
    fun con_rutina_activa_propone_el_siguiente_entrenamiento() {
        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L))
        runBlocking {
            val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
            val dayId = routines.addDay(routineId, "Día 1 – Torso", weekday = null)
            routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
        }

        showScreen()

        compose.onNodeWithText("Día 1 – Torso").assertExists()
        compose.onNodeWithText("Empezar entrenamiento").assertExists()
    }

    @Test
    fun un_entrenamiento_a_medias_sale_el_primero_con_sus_dos_salidas() {
        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L))
        runBlocking {
            val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
            val dayId = routines.addDay(routineId, "Día 1 – Torso", null)
            routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
            val sessionId = workouts.startSession(dayId, LocalDate(2026, 7, 20))
            workouts.logSet(sessionId, benchId, 1, 60.0, 10, null)
        }

        showScreen()

        compose.onNodeWithText("Tienes un entrenamiento sin terminar").assertIsDisplayed()
        compose.onNodeWithText("Retomar").assertExists()
        compose.onNodeWithText("Descartar").assertExists()
    }

    @Test
    fun descartar_el_entrenamiento_a_medias_lo_borra_y_quita_el_aviso() {
        val benchId = db.insertTestExercise(1, "Press de banca", listOf(4L))
        runBlocking {
            val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
            val dayId = routines.addDay(routineId, "Día 1", null)
            routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
            workouts.startSession(dayId, LocalDate(2026, 7, 20))
        }

        showScreen()
        compose.onNodeWithText("Descartar").performClick()

        assertTrue(runBlocking { workouts.unfinishedSessions().isEmpty() })
        compose.onNodeWithText("Tienes un entrenamiento sin terminar").assertDoesNotExist()
    }
}
