package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cuando Open Food Facts se cae (cosa frecuente), el usuario debe ver un aviso
 * en español, no una excepción de Ktor.
 */
class NutritionErrorTest {

    private fun failingRepo(status: HttpStatusCode): NutritionRepository {
        val engine = MockEngine { respondError(status) }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        return NutritionRepository(createTestDatabase(), OpenFoodFactsApi(client), Dispatchers.Unconfined)
    }

    @Test
    fun un_503_al_buscar_devuelve_un_mensaje_en_espanol() = runTest {
        val result = failingRepo(HttpStatusCode.ServiceUnavailable).searchOnline("avena")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("Open Food Facts"), "mensaje: $message")
        assertTrue(message.contains("503"), "mensaje: $message")
        // Nada de jerga de Ktor delante del usuario.
        assertTrue(!message.contains("Ktor") && !message.contains("Exception"), "mensaje: $message")
    }

    @Test
    fun un_fallo_de_red_al_buscar_habla_de_la_conexion() = runTest {
        val engine = MockEngine { throw java.io.IOException("connection reset") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        val repo = NutritionRepository(
            createTestDatabase(), OpenFoodFactsApi(client), Dispatchers.Unconfined,
        )

        val result = repo.searchOnline("avena")

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("conexión"),
            "mensaje: ${result.exceptionOrNull()?.message}",
        )
    }

    @Test
    fun un_503_al_escanear_tambien_da_mensaje_propio() = runTest {
        val result = failingRepo(HttpStatusCode.ServiceUnavailable).findByBarcode("3017624010701")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Open Food Facts"))
    }

    @Test
    fun si_el_alimento_ya_esta_en_local_el_escaneo_no_toca_la_red() = runTest {
        val db = createTestDatabase()
        val engine = MockEngine { error("no debería llamarse a la red") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        val repo = NutritionRepository(db, OpenFoodFactsApi(client), Dispatchers.Unconfined)

        db.nutritionQueries.upsertFoodByBarcode(
            name = "Galletas", brand = null, kcalPer100 = 450.0, proteinPer100 = 6.0,
            carbsPer100 = 60.0, fatPer100 = 20.0, fiberPer100 = null, servingSizeG = null,
            imageUrl = null, cachedAt = null, offBarcode = "111",
        )

        val result = repo.findByBarcode("111")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.name == "Galletas")
    }
}
