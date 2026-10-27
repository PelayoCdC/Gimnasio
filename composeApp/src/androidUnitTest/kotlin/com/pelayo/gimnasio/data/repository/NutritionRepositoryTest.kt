package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.FoodUnit
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
import kotlin.test.assertFalse
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
    fun el_balance_compara_lo_ingerido_con_el_tdee_sin_contar_el_entreno_dos_veces() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val foodId = repo.createCustomFood("Comida", null, 100.0, 0.0, 0.0, 0.0)
        repo.logFood(date, MealType.COMIDA, foodId, 2000.0) // 2000 kcal

        val balance = repo.calorieBalance(date, tdeeKcal = 2400.0, exerciseKcal = 300.0)

        assertClose(2000.0, balance.intakeKcal)
        // El factor de actividad del TDEE ya incluye el entrenamiento: las kcal
        // del entreno se guardan como detalle pero no se suman al gasto.
        assertClose(2400.0, balance.expenditureKcal!!)
        assertClose(-400.0, balance.balanceKcal!!, 0.01)
        assertClose(300.0, balance.exerciseKcal)
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
    fun un_liquido_se_apunta_en_mililitros() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val leche = repo.createCustomFood(
            "Leche entera", null, 63.0, 3.1, 4.7, 3.6, unit = FoodUnit.ML,
        )

        repo.logFood(date, MealType.DESAYUNO, leche, grams = 250.0)

        val entry = repo.dayNutrition(date).entriesByMeal[MealType.DESAYUNO]!!.single()
        assertEquals("250 ml", entry.quantityLabel)
        assertEquals(FoodUnit.ML, entry.unit)
        assertClose(157.5, entry.macros.kcal)
    }

    @Test
    fun la_unidad_se_puede_corregir_despues() = runTest {
        // Lo cacheado antes de que existiera la columna se quedó en gramos.
        val (repo, _) = repo()
        val leche = repo.createCustomFood("Leche", null, 63.0, 3.1, 4.7, 3.6)
        assertEquals(FoodUnit.G, repo.food(leche)!!.unit)

        repo.updateFoodUnit(leche, FoodUnit.ML)

        assertEquals(FoodUnit.ML, repo.food(leche)!!.unit)
    }

    @Test
    fun las_calorias_por_dia_solo_traen_los_dias_con_registro() = runTest {
        val (repo, _) = repo()
        val comida = repo.createCustomFood("Comida", null, 100.0, 0.0, 0.0, 0.0)
        repo.logFood(LocalDate(2026, 7, 20), MealType.COMIDA, comida, grams = 500.0)
        repo.logFood(LocalDate(2026, 7, 25), MealType.CENA, comida, grams = 300.0)
        // Fuera del rango pedido.
        repo.logFood(LocalDate(2026, 8, 2), MealType.CENA, comida, grams = 900.0)

        val byDate = repo.dailyCalories(LocalDate(2026, 7, 1), LocalDate(2026, 7, 31))

        assertEquals(setOf(LocalDate(2026, 7, 20), LocalDate(2026, 7, 25)), byDate.keys)
        assertClose(500.0, byDate.getValue(LocalDate(2026, 7, 20)))
        assertClose(300.0, byDate.getValue(LocalDate(2026, 7, 25)))
    }

    @Test
    fun las_calorias_por_dia_cuentan_tambien_las_recetas() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", servings = 2, notes = null)
        repo.addIngredient(recipeId, arroz, grams = 200.0) // 100 kcal por ración
        repo.logRecipe(date, MealType.CENA, recipeId, servings = 2.0)

        assertClose(200.0, repo.dailyCalories(date, date).getValue(date))
    }

    @Test
    fun el_catalogo_cuenta_lo_guardado_y_lo_creado_a_mano() = runTest {
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

        repo.createCustomFood("Avena de casa", null, 380.0, 13.0, 60.0, 7.0)
        repo.searchOnline("cacao").getOrThrow()

        val counts = repo.foodCounts()
        assertEquals(2, counts.total, "cuenta lo descargado y lo propio")
        assertEquals(1, counts.custom)
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
    fun editar_lo_apuntado_cambia_la_cantidad_y_la_comida() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        repo.logFood(date, MealType.DESAYUNO, avena, grams = 100.0)
        val entry = repo.dayNutrition(date).entriesByMeal[MealType.DESAYUNO]!!.single()

        repo.updateLogEntry(entry.id, MealType.CENA, grams = 50.0, servings = null)

        val day = repo.dayNutrition(date)
        assertTrue(day.entriesByMeal[MealType.DESAYUNO].isNullOrEmpty())
        val moved = day.entriesByMeal[MealType.CENA]!!.single()
        assertEquals("50 g", moved.quantityLabel)
        assertEquals(50.0, moved.grams)
        assertClose(190.0, moved.macros.kcal)
    }

    @Test
    fun editar_una_receta_apuntada_cambia_las_raciones() = runTest {
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", servings = 2, notes = null)
        repo.addIngredient(recipeId, arroz, grams = 200.0) // 100 kcal por ración
        repo.logRecipe(date, MealType.CENA, recipeId, servings = 1.0)
        val entry = repo.dayNutrition(date).entriesByMeal[MealType.CENA]!!.single()

        repo.updateLogEntry(entry.id, MealType.CENA, grams = null, servings = 2.0)

        val updated = repo.dayNutrition(date).entriesByMeal[MealType.CENA]!!.single()
        assertEquals("2 raciones", updated.quantityLabel)
        assertEquals(2.0, updated.servings)
        assertClose(200.0, updated.macros.kcal)
    }

    @Test
    fun una_receta_apuntada_en_el_diario_no_se_borra() = runTest {
        // Antes reventaba con un fallo de clave foránea, que en el móvil es un
        // cierre de la app al tocar la papelera.
        val (repo, _) = repo()
        val date = LocalDate(2026, 7, 25)
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", 2, null)
        repo.addIngredient(recipeId, arroz, 200.0)
        repo.logRecipe(date, MealType.CENA, recipeId, 1.0)

        assertFalse(repo.deleteRecipe(recipeId), "no debería dejar borrarla")

        assertNotNull(repo.recipe(recipeId), "la receta sigue ahí")
        assertEquals(1, repo.dayNutrition(date).entriesByMeal[MealType.CENA]?.size)
        assertEquals(1, repo.recipeUsage(recipeId).diaryEntries)
    }

    @Test
    fun borrar_una_receta_la_quita_de_los_planes_de_dieta() = runTest {
        // El plan es una plantilla y se rehace; el diario no.
        val (repo, _) = repo()
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", 2, null)
        repo.addIngredient(recipeId, arroz, 200.0)
        val planId = repo.createDietPlan("Dieta", null, null, null, null)
        repo.addRecipeToDietPlan(planId, 1, MealType.CENA, recipeId, 1.0)

        assertEquals(1, repo.recipeUsage(recipeId).dietPlanMeals)
        assertTrue(repo.deleteRecipe(recipeId))

        assertNull(repo.recipe(recipeId))
        assertTrue(repo.dietPlan(planId)!!.meals.isEmpty())
    }

    @Test
    fun cambiar_la_cantidad_de_un_ingrediente_recalcula_la_racion() = runTest {
        val (repo, _) = repo()
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", servings = 2, notes = null)
        repo.addIngredient(recipeId, arroz, grams = 200.0)
        assertClose(100.0, repo.recipe(recipeId)!!.macrosPerServing.kcal)

        val ingredientId = repo.recipe(recipeId)!!.ingredients.single().id
        repo.updateIngredient(ingredientId, grams = 300.0)

        val recipe = assertNotNull(repo.recipe(recipeId))
        assertEquals(300.0, recipe.ingredients.single().grams)
        assertClose(150.0, recipe.macrosPerServing.kcal)
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

    @Test
    fun un_alimento_en_el_diario_o_en_una_receta_no_se_puede_borrar() = runTest {
        val (repo, _) = repo()
        val foodId = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        repo.logFood(LocalDate(2026, 7, 25), MealType.DESAYUNO, foodId, 80.0)

        // En el diario: bloqueado (antes esto era un crash de clave foránea).
        assertFalse(repo.deleteFood(foodId))
        assertNotNull(repo.food(foodId))

        // En una receta también.
        val other = repo.createCustomFood("Arroz", null, 350.0, 7.0, 77.0, 1.0)
        val recipeId = repo.createRecipe("Arroz blanco", 2, null)
        repo.addIngredient(recipeId, other, 200.0)
        assertFalse(repo.deleteFood(other))

        // Libre de usos: se borra, y de los planes de dieta se quita solo.
        val free = repo.createCustomFood("Nueces", null, 650.0, 15.0, 7.0, 60.0)
        val planId = repo.createDietPlan("Plan", null, null, null, null)
        repo.addFoodToDietPlan(planId, 1, MealType.SNACK, free, 30.0)
        assertTrue(repo.deleteFood(free))
        assertNull(repo.food(free))
        assertTrue(repo.dietPlan(planId)!!.meals.isEmpty())
    }

    @Test
    fun la_busqueda_local_ignora_tildes_y_mayusculas() = runTest {
        val (repo, _) = repo()
        repo.createCustomFood("Café con leche", null, 40.0, 2.0, 4.0, 1.5)
        repo.createCustomFood("Plátano", null, 89.0, 1.1, 23.0, 0.3)

        // "cafe" debe encontrar "Café" y "PLATANO" a "Plátano": el LIKE de SQLite
        // no sabe de acentos, por eso existe Food.searchName.
        assertEquals(1, repo.searchLocal("cafe").size)
        assertEquals(1, repo.searchLocal("PLATANO").size)
        assertEquals(1, repo.searchLocal("platano").size)
    }

    @Test
    fun aplicar_el_plan_dos_veces_no_duplica_el_diario() = runTest {
        val (repo, _) = repo()
        val foodId = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        val planId = repo.createDietPlan("Plan", null, null, null, null)
        // El 27 de julio de 2026 es lunes (ISO 1).
        val monday = LocalDate(2026, 7, 27)
        repo.addFoodToDietPlan(planId, 1, MealType.DESAYUNO, foodId, 80.0)

        assertEquals(1, repo.applyDietPlanToDay(planId, monday))
        // Segundo toque: nada nuevo que insertar.
        assertEquals(0, repo.applyDietPlanToDay(planId, monday))
        assertEquals(1, repo.dayNutrition(monday).entriesByMeal.values.flatten().size)
    }

    @Test
    fun repetir_ayer_copia_solo_esa_comida_y_es_idempotente() = runTest {
        val (repo, _) = repo()
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        val pollo = repo.createCustomFood("Pollo", null, 120.0, 23.0, 0.0, 2.5)
        val yesterday = LocalDate(2026, 7, 29)
        val today = LocalDate(2026, 7, 30)
        repo.logFood(yesterday, MealType.DESAYUNO, avena, 80.0)
        repo.logFood(yesterday, MealType.COMIDA, pollo, 200.0)

        // Copia el desayuno de ayer, no el día entero.
        assertEquals(1, repo.repeatMealFromPreviousDay(today, MealType.DESAYUNO))
        val day = repo.dayNutrition(today)
        assertEquals(1, day.entriesByMeal[MealType.DESAYUNO]?.size)
        assertNull(day.entriesByMeal[MealType.COMIDA])

        // Segundo toque: nada nuevo. Y sin ayer, cero.
        assertEquals(0, repo.repeatMealFromPreviousDay(today, MealType.DESAYUNO))
        assertEquals(0, repo.repeatMealFromPreviousDay(today, MealType.CENA))
    }

    @Test
    fun deshacer_un_borrado_del_diario_reinserta_la_entrada() = runTest {
        val (repo, _) = repo()
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        val date = LocalDate(2026, 7, 30)
        repo.logFood(date, MealType.DESAYUNO, avena, 80.0)
        val item = repo.dayNutrition(date).entriesByMeal[MealType.DESAYUNO]!!.single()

        repo.deleteLogEntry(item.id)
        assertTrue(repo.dayNutrition(date).entriesByMeal.isEmpty())

        repo.restoreLogEntry(date, item)
        val restored = repo.dayNutrition(date).entriesByMeal[MealType.DESAYUNO]!!.single()
        assertEquals(80.0, restored.grams)
        assertEquals(avena, restored.foodId)
    }

    @Test
    fun editar_un_alimento_propio_actualiza_sus_macros() = runTest {
        val (repo, _) = repo()
        val foodId = repo.createCustomFood("Avena", null, 999.0, 13.0, 60.0, 7.0)

        repo.updateCustomFood(
            foodId, "Avena integral", "Hacendado",
            kcalPer100 = 380.0, proteinPer100 = 13.0, carbsPer100 = 60.0, fatPer100 = 7.0,
        )

        val food = assertNotNull(repo.food(foodId))
        assertEquals("Avena integral", food.name)
        assertEquals(380.0, food.kcalPer100)
        // Y sigue apareciendo al buscar por el nombre nuevo, sin tildes.
        assertEquals(1, repo.searchLocal("integral").size)
    }
}
