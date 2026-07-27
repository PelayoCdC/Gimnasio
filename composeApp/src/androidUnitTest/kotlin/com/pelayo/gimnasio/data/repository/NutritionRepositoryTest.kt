package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.MealType
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionRepositoryTest {

    /**
     * Open Food Facts se consulta de tres formas y el motor simulado responde
     * según cuál sea: el buscador por texto (que solo da códigos), la búsqueda en
     * lote de la v2 (que da los datos completos) y el producto por código.
     */
    private fun repo(
        db: GimnasioDb = createTestDatabase(),
        offResponse: String = """{"status":0}""",
        offHits: String = """{"count":0,"hits":[]}""",
        offProducts: String = """{"count":0,"products":[]}""",
    ): Pair<NutritionRepository, GimnasioDb> {
        val engine = MockEngine { request ->
            val content = when {
                request.url.host.startsWith("search.") -> offHits
                request.url.encodedPath.endsWith("/api/v2/search") -> offProducts
                else -> offResponse
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        return NutritionRepository(db, OpenFoodFactsApi(client), Dispatchers.Unconfined) to db
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.01) {
        assertTrue(abs(expected - actual) < tolerance, "esperado $expected, obtenido $actual")
    }

    @Test
    fun crea_alimento_propio_y_lo_encuentra_buscando() = runTest {
        val (repo, _) = repo()
        repo.createCustomFood("Arroz cocido", null, 130.0, 2.7, 28.0, 0.3)

        val results = repo.searchLocal("arroz")
        assertEquals(1, results.size)
        assertTrue(results.single().isCustom)
        assertEquals(130.0, results.single().kcalPer100)
    }

    @Test
    fun registra_comida_y_calcula_los_macros_por_gramos() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val foodId = repo.createCustomFood("Pechuga de pollo", null, 165.0, 31.0, 0.0, 3.6)

        repo.logFood(date, MealType.COMIDA, foodId, grams = 200.0)

        val day = repo.dayNutrition(date)
        assertClose(330.0, day.total.kcal)
        assertClose(62.0, day.total.protein)
        assertClose(7.2, day.total.fat)

        val entry = day.entriesByMeal[MealType.COMIDA]!!.single()
        assertEquals("200 g", entry.quantityLabel)
        assertTrue(!entry.isRecipe)
    }

    @Test
    fun agrupa_las_entradas_por_comida() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        val pollo = repo.createCustomFood("Pollo", null, 165.0, 31.0, 0.0, 3.6)

        repo.logFood(date, MealType.DESAYUNO, avena, 100.0)
        repo.logFood(date, MealType.COMIDA, pollo, 150.0)

        val day = repo.dayNutrition(date)
        assertEquals(setOf(MealType.DESAYUNO, MealType.COMIDA), day.entriesByMeal.keys)
        assertClose(380.0, day.mealTotal(MealType.DESAYUNO).kcal)
        assertClose(247.5, day.mealTotal(MealType.COMIDA).kcal)
        assertClose(627.5, day.total.kcal)
    }

    @Test
    fun cada_dia_lleva_su_propio_diario() = runTest {
        val (repo, _) = repo()
        val foodId = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        repo.logFood(LocalDate(2026, 7, 24), MealType.DESAYUNO, foodId, 100.0)

        assertClose(0.0, repo.dayNutrition(LocalDate(2026, 7, 25)).total.kcal)
        assertClose(380.0, repo.dayNutrition(LocalDate(2026, 7, 24)).total.kcal)
    }

    @Test
    fun borrar_una_entrada_la_quita_del_total() = runTest {
        val (repo, db) = repo()
        val date = LocalDate(2026, 7, 25)
        val foodId = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        repo.logFood(date, MealType.DESAYUNO, foodId, 100.0)

        val entryId = repo.dayNutrition(date).entriesByMeal[MealType.DESAYUNO]!!.single().id
        repo.deleteLogEntry(entryId)

        assertClose(0.0, repo.dayNutrition(date).total.kcal)
    }

    @Test
    fun el_balance_resta_tdee_y_entrenos_a_lo_ingerido() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val foodId = repo.createCustomFood("Comida", null, 100.0, 0.0, 0.0, 0.0)
        repo.logFood(date, MealType.COMIDA, foodId, 2000.0) // 2000 kcal

        val balance = repo.calorieBalance(date, tdeeKcal = 2400.0, exerciseKcal = 300.0)

        assertClose(2000.0, balance.intakeKcal)
        assertClose(2700.0, balance.expenditureKcal!!)
        assertClose(-700.0, balance.balanceKcal!!, 0.01)
    }

    @Test
    fun sin_tdee_no_hay_balance_pero_si_ingesta() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val foodId = repo.createCustomFood("Comida", null, 100.0, 0.0, 0.0, 0.0)
        repo.logFood(date, MealType.COMIDA, foodId, 500.0)

        val balance = repo.calorieBalance(date, tdeeKcal = null, exerciseKcal = 0.0)
        assertClose(500.0, balance.intakeKcal)
        assertNull(balance.balanceKcal)
    }

    @Test
    fun la_busqueda_online_cachea_los_productos_en_local() = runTest {
        // El buscador solo devuelve el código, y con las marcas en lista.
        val offHits = """{"count":1,"hits":[{"code":"3017624010701","brands":["Ferrero"]}]}"""
        val offProducts = """
        {
          "count": 1,
          "products": [
            {
              "code": "3017624010701",
              "product_name_es": "Crema de cacao",
              "brands": "Ferrero",
              "nutriments": {"energy-kcal_100g": 539, "proteins_100g": 6.3,
                             "carbohydrates_100g": 57.5, "fat_100g": 30.9}
            }
          ]
        }
        """.trimIndent()
        val (repo, _) = repo(offHits = offHits, offProducts = offProducts)

        assertTrue(repo.searchLocal("cacao").isEmpty())

        val online = repo.searchOnline("cacao").getOrThrow()
        assertEquals(1, online.size)

        // Tras la búsqueda online el producto queda disponible sin conexión.
        val local = repo.searchLocal("cacao")
        assertEquals(1, local.size)
        assertEquals("3017624010701", local.single().barcode)
        assertEquals(539.0, local.single().kcalPer100)
        assertTrue(!local.single().isCustom)
    }

    @Test
    fun buscar_por_codigo_de_barras_usa_primero_la_cache_local() = runTest {
        val product = """
        {
          "status": 1,
          "product": {
            "code": "111",
            "product_name": "Galletas",
            "nutriments": {"energy-kcal_100g": 450}
          }
        }
        """.trimIndent()
        val (repo, _) = repo(offResponse = product)

        val first = assertNotNull(repo.findByBarcode("111").getOrThrow())
        assertEquals("Galletas", first.name)

        // Segunda llamada: sale de la base local, sin depender de la red.
        val second = assertNotNull(repo.findByBarcode("111").getOrThrow())
        assertEquals(first.id, second.id)
    }

    @Test
    fun las_recetas_reparten_los_macros_entre_las_raciones() = runTest {
        val (repo, _) = repo()
        val arroz = repo.createCustomFood("Arroz", null, 130.0, 2.7, 28.0, 0.3)
        val pollo = repo.createCustomFood("Pollo", null, 165.0, 31.0, 0.0, 3.6)

        val recipeId = repo.createRecipe("Arroz con pollo", servings = 2, notes = null)
        repo.addIngredient(recipeId, arroz, grams = 300.0)
        repo.addIngredient(recipeId, pollo, grams = 200.0)

        val recipe = assertNotNull(repo.recipe(recipeId))
        // Total: 390 + 330 = 720 kcal → 360 por ración.
        assertClose(360.0, recipe.macrosPerServing.kcal)
        // Proteína total: 8.1 + 62 = 70.1 → 35.05 por ración.
        assertClose(35.05, recipe.macrosPerServing.protein)
        assertEquals(2, recipe.ingredients.size)
    }

    @Test
    fun registrar_una_receta_escala_por_raciones_consumidas() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", servings = 2, notes = null)
        repo.addIngredient(recipeId, arroz, grams = 200.0) // 200 kcal → 100 por ración

        repo.logRecipe(date, MealType.CENA, recipeId, servings = 1.5)

        val entry = repo.dayNutrition(date).entriesByMeal[MealType.CENA]!!.single()
        assertTrue(entry.isRecipe)
        assertEquals("1.5 raciones", entry.quantityLabel)
        assertClose(150.0, entry.macros.kcal)
    }

    @Test
    fun borrar_una_receta_arrastra_sus_ingredientes() = runTest {
        val (repo, db) = repo()
        val arroz = repo.createCustomFood("Arroz", null, 130.0, 2.7, 28.0, 0.3)
        val recipeId = repo.createRecipe("Prueba", 1, null)
        repo.addIngredient(recipeId, arroz, 100.0)

        repo.deleteRecipe(recipeId)

        assertNull(repo.recipe(recipeId))
        assertTrue(db.nutritionQueries.selectIngredientsForRecipe(recipeId).executeAsList().isEmpty())
    }
}
