package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
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

class DietPlanTest {

    private fun repo(): NutritionRepository {
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
        return NutritionRepository(createTestDatabase(), OpenFoodFactsApi(client), Dispatchers.Unconfined)
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.01) {
        assertTrue(abs(expected - actual) < tolerance, "esperado $expected, obtenido $actual")
    }

    @Test
    fun crea_un_plan_con_objetivos_y_lo_recupera() = runTest {
        val repo = repo()
        val id = repo.createDietPlan("Volumen", 2800.0, 160.0, 350.0, 78.0)

        val plan = assertNotNull(repo.dietPlan(id))
        assertEquals("Volumen", plan.name)
        assertEquals(2800.0, plan.targetKcal)
        assertEquals(160.0, plan.targetProteinG)
        assertTrue(plan.isActive, "el primer plan creado debe quedar activo")
        assertTrue(plan.meals.isEmpty())
    }

    @Test
    fun suma_los_macros_de_los_alimentos_del_menu() = runTest {
        val repo = repo()
        val planId = repo.createDietPlan("Dieta", 2000.0, null, null, null)
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        val pollo = repo.createCustomFood("Pollo", null, 165.0, 31.0, 0.0, 3.6)

        repo.addFoodToDietPlan(planId, MealType.DESAYUNO, avena, grams = 100.0)
        repo.addFoodToDietPlan(planId, MealType.COMIDA, pollo, grams = 200.0)

        val plan = assertNotNull(repo.dietPlan(planId))
        assertClose(380.0 + 330.0, plan.plannedMacros.kcal)
        assertClose(13.0 + 62.0, plan.plannedMacros.protein)
        assertEquals(1, plan.mealsFor(MealType.DESAYUNO).size)
        assertEquals(1, plan.mealsFor(MealType.COMIDA).size)
        assertTrue(plan.mealsFor(MealType.CENA).isEmpty())
    }

    @Test
    fun una_receta_en_el_plan_escala_por_raciones() = runTest {
        val repo = repo()
        val planId = repo.createDietPlan("Dieta", null, null, null, null)
        val arroz = repo.createCustomFood("Arroz", null, 100.0, 0.0, 0.0, 0.0)
        val recipeId = repo.createRecipe("Arroz simple", servings = 2, notes = null)
        repo.addIngredient(recipeId, arroz, grams = 200.0) // 200 kcal → 100 por ración

        repo.addRecipeToDietPlan(planId, MealType.CENA, recipeId, servings = 1.5)

        val meal = assertNotNull(repo.dietPlan(planId)).mealsFor(MealType.CENA).single()
        assertEquals("1.5 raciones", meal.quantityLabel)
        assertClose(150.0, meal.macros.kcal)
    }

    @Test
    fun aplicar_el_plan_vuelca_el_menu_al_diario() = runTest {
        val repo = repo()
        val date = LocalDate(2026, 7, 25)
        val planId = repo.createDietPlan("Dieta", null, null, null, null)
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        val pollo = repo.createCustomFood("Pollo", null, 165.0, 31.0, 0.0, 3.6)
        repo.addFoodToDietPlan(planId, MealType.DESAYUNO, avena, 100.0)
        repo.addFoodToDietPlan(planId, MealType.COMIDA, pollo, 200.0)

        val added = repo.applyDietPlanToDay(planId, date)

        assertEquals(2, added)
        val day = repo.dayNutrition(date)
        assertClose(710.0, day.total.kcal)
        assertEquals("Avena", day.entriesByMeal[MealType.DESAYUNO]!!.single().name)
        assertEquals("100 g", day.entriesByMeal[MealType.DESAYUNO]!!.single().quantityLabel)
    }

    @Test
    fun aplicar_un_plan_vacio_no_anade_nada() = runTest {
        val repo = repo()
        val planId = repo.createDietPlan("Vacío", null, null, null, null)
        assertEquals(0, repo.applyDietPlanToDay(planId, LocalDate(2026, 7, 25)))
    }

    @Test
    fun solo_un_plan_puede_estar_activo() = runTest {
        val repo = repo()
        val first = repo.createDietPlan("Definición", null, null, null, null)
        val second = repo.createDietPlan("Volumen", null, null, null, null)

        repo.setActiveDietPlan(second)

        assertEquals("Volumen", repo.activeDietPlan()?.name)
        assertTrue(!repo.dietPlan(first)!!.isActive)
    }

    @Test
    fun quitar_un_alimento_actualiza_el_total() = runTest {
        val repo = repo()
        val planId = repo.createDietPlan("Dieta", null, null, null, null)
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        repo.addFoodToDietPlan(planId, MealType.DESAYUNO, avena, 100.0)

        val mealId = repo.dietPlan(planId)!!.meals.single().id
        repo.removeDietPlanMeal(mealId)

        assertClose(0.0, repo.dietPlan(planId)!!.plannedMacros.kcal)
    }

    @Test
    fun borrar_el_plan_arrastra_su_menu() = runTest {
        val repo = repo()
        val planId = repo.createDietPlan("Dieta", null, null, null, null)
        val avena = repo.createCustomFood("Avena", null, 380.0, 13.0, 60.0, 7.0)
        repo.addFoodToDietPlan(planId, MealType.DESAYUNO, avena, 100.0)

        repo.deleteDietPlan(planId)

        assertNull(repo.dietPlan(planId))
        assertTrue(repo.dietPlans().isEmpty())
    }
}
