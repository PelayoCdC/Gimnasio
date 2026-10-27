package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.platform.BarcodeScanner
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * El alta de alimento desde la receta ya existía, pero iba detrás de una lista de
 * alto fijo y se quedaba fuera de la zona visible del diálogo. Este test fija que
 * se vea y que lleve al formulario.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecipeEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: GimnasioDb
    private lateinit var repository: NutritionRepository
    private var recipeId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = createTestDatabase()
        val engine = MockEngine {
            respond(
                content = """{"count":0,"hits":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        repository = NutritionRepository(db, OpenFoodFactsApi(client), Dispatchers.Unconfined)

        runBlocking {
            recipeId = repository.createRecipe("Arroz con pollo", servings = 2, notes = null)
            // Bastantes alimentos para que la lista llene el diálogo.
            repeat(12) { index ->
                repository.createCustomFood("Alimento $index", null, 100.0, 1.0, 1.0, 1.0)
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun showScreen() {
        val viewModel = RecipeEditViewModel(
            recipeId = recipeId,
            repository = repository,
            barcodeScanner = BarcodeScanner(RuntimeEnvironment.getApplication()),
        )
        compose.setContent {
            RecipeEditScreen(recipeId = recipeId, onBack = {}, viewModel = viewModel)
        }
    }

    @Test
    fun el_selector_de_ingredientes_deja_crear_un_alimento_nuevo() {
        showScreen()

        compose.onNodeWithText("Añadir ingrediente").performClick()

        // Con la lista llena, el botón tiene que seguir viéndose.
        compose.onNodeWithText("Crear alimento").assertIsDisplayed()
        compose.onNodeWithText("Crear alimento").performClick()

        compose.onNodeWithText("Nuevo alimento").assertIsDisplayed()
    }

    @Test
    fun el_alimento_creado_desde_la_receta_se_puede_anadir_de_seguido() {
        showScreen()

        compose.onNodeWithText("Añadir ingrediente").performClick()
        compose.onNodeWithText("Crear alimento").performClick()

        compose.onNodeWithText("Nombre").performTextReplacement("Sofrito casero")
        compose.onNodeWithText("kcal / 100 g").performTextReplacement("150")
        compose.onNodeWithText("Crear").performClick()

        // Vuelve al diálogo de ingredientes con el alimento nuevo ya elegido.
        compose.onNodeWithText("Añadir").performClick()

        val recipe = runBlocking { assertNotNull(repository.recipe(recipeId)) }
        assertEquals(listOf("Sofrito casero"), recipe.ingredients.map { it.foodName })
    }
}
