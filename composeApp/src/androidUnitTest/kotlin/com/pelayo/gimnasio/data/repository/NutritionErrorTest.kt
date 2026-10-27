package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.db.GimnasioDb
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Cuando Open Food Facts se cae (cosa frecuente), el usuario debe ver un aviso
 * en español, no una excepción de Ktor.
 */
@OptIn(ExperimentalTime::class)
class NutritionErrorTest {

    private fun repoWith(
        engine: MockEngine,
        db: GimnasioDb = createTestDatabase(),
        now: () -> Instant = { Clock.System.now() },
    ): NutritionRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        return NutritionRepository(db, OpenFoodFactsApi(client), Dispatchers.Unconfined, now)
    }

    private fun failingRepo(status: HttpStatusCode): NutritionRepository =
        repoWith(MockEngine { respondError(status) })

    @Test
    fun un_503_al_buscar_avisa_de_que_hay_que_esperar() = runTest {
        // El 503 de OFF no es una caída: es su corte por exceso de peticiones.
        val repo = failingRepo(HttpStatusCode.ServiceUnavailable)
        val result = repo.searchOnline("avena")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("Open Food Facts"), "mensaje: $message")
        assertTrue(message.contains("cortado"), "mensaje: $message")
        assertTrue(message.contains("60s"), "debe decir cuánto esperar: $message")
        // Nada de jerga de Ktor delante del usuario.
        assertTrue(!message.contains("Ktor") && !message.contains("Exception"), "mensaje: $message")

        assertEquals(60, repo.rateLimitSecondsLeft())
    }

    @Test
    fun un_502_de_verdad_no_se_confunde_con_el_corte() = runTest {
        val repo = failingRepo(HttpStatusCode.BadGateway)

        val message = repo.searchOnline("avena").exceptionOrNull()?.message.orEmpty()

        assertTrue(message.contains("502"), "mensaje: $message")
        assertEquals(0, repo.rateLimitSecondsLeft(), "una caída no debe bloquear el botón")
    }

    @Test
    fun mientras_dura_el_corte_no_se_vuelve_a_llamar_a_la_red() = runTest {
        // Insistir es lo que mantiene el corte abierto, así que ni se intenta.
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val repo = repoWith(engine)

        repo.searchOnline("avena")
        val second = repo.searchOnline("avena")

        assertEquals(1, calls, "la segunda búsqueda no debe salir a la red")
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull()?.message.orEmpty().contains("espera"))
    }

    @Test
    fun se_respeta_el_retry_after_que_manda_el_servidor() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "30"),
            )
        }
        val repo = repoWith(engine)

        repo.searchOnline("avena")

        assertEquals(30, repo.rateLimitSecondsLeft())
    }

    @Test
    fun pasado_el_corte_se_puede_volver_a_buscar() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        var now = Instant.fromEpochSeconds(1_000)
        val repo = repoWith(engine, now = { now })

        repo.searchOnline("avena")
        assertEquals(60, repo.rateLimitSecondsLeft())

        now = now.plus(61.seconds)

        assertEquals(0, repo.rateLimitSecondsLeft())
        repo.searchOnline("avena")
        assertEquals(2, calls, "cumplida la espera, se vuelve a preguntar")
    }

    @Test
    fun durante_el_corte_el_escaneo_sigue_resolviendo_lo_que_ya_esta_en_local() = runTest {
        val db = createTestDatabase()
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val repo = repoWith(engine, db)
        db.nutritionQueries.upsertFoodByBarcode(
            name = "Galletas", brand = null, kcalPer100 = 450.0, proteinPer100 = 6.0,
            carbsPer100 = 60.0, fatPer100 = 20.0, fiberPer100 = null, servingSizeG = null,
            imageUrl = null, cachedAt = null, offBarcode = "111", unit = "G", searchName = "galletas",
        )

        repo.searchOnline("galletas") // provoca el corte

        assertTrue(repo.findByBarcode("111").isSuccess, "la caché local no depende de OFF")
        assertTrue(repo.findByBarcode("222").isFailure)
        assertEquals(1, calls, "el código desconocido tampoco sale a la red")
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
    fun una_respuesta_que_no_encaja_no_culpa_a_la_conexion() = runTest {
        // Es lo que pasaba de verdad: OFF respondía 200 con un campo de otro tipo
        // y la app decía que no había internet, con internet perfecto.
        val engine = MockEngine {
            respond(
                // Una lista donde se espera un texto: exactamente el desajuste
                // que traía `brands` del buscador nuevo.
                content = """{"count":1,"hits":[{"code":["111","222"]}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        val repo = NutritionRepository(
            createTestDatabase(), OpenFoodFactsApi(client), Dispatchers.Unconfined,
        )

        val result = repo.searchOnline("avena")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("no entiende"), "mensaje: $message")
        assertTrue(!message.contains("conexión"), "sigue culpando a la conexión: $message")
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
            imageUrl = null, cachedAt = null, offBarcode = "111", unit = "G", searchName = "galletas",
        )

        val result = repo.findByBarcode("111")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.name == "Galletas")
    }
}
