package com.pelayo.gimnasio.data.remote

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El endpoint antiguo de búsqueda (`cgi/search.pl`) responde 503 de forma
 * sistemática; estos tests fijan el comportamiento del nuevo y el trato de errores.
 */
class OpenFoodFactsApiTest {

    private fun apiRespondingWith(
        status: HttpStatusCode,
        body: String = "",
    ): Pair<OpenFoodFactsApi, MockEngine> {
        val engine = MockEngine {
            if (status == HttpStatusCode.OK) {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respondError(status)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.json) }
        }
        return OpenFoodFactsApi(client) to engine
    }

    @Test
    fun la_busqueda_apunta_al_servicio_nuevo() = runTest {
        val (api, engine) = apiRespondingWith(HttpStatusCode.OK, """{"count":0,"hits":[]}""")

        api.search("avena")

        val url = engine.requestHistory.single().url.toString()
        assertTrue(url.startsWith("https://search.openfoodfacts.org/search"), "URL usada: $url")
        assertTrue(url.contains("q=avena"), "falta el término de búsqueda: $url")
        assertTrue(!url.contains("search.pl"), "sigue usando el endpoint antiguo")
    }

    @Test
    fun un_503_se_convierte_en_un_mensaje_entendible() = runTest {
        val (api, _) = apiRespondingWith(HttpStatusCode.ServiceUnavailable)

        val error = assertFailsWith<OpenFoodFactsException> { api.search("avena") }

        assertTrue(error.message!!.contains("no responde"), "mensaje: ${error.message}")
        assertTrue(error.message!!.contains("503"))
        assertTrue(error.message!!.contains("crea el alimento a mano"))
    }

    @Test
    fun demasiadas_peticiones_avisa_de_esperar() = runTest {
        val (api, _) = apiRespondingWith(HttpStatusCode.TooManyRequests)
        val error = assertFailsWith<OpenFoodFactsException> { api.search("avena") }
        assertTrue(error.message!!.contains("Espera"), "mensaje: ${error.message}")
    }

    @Test
    fun el_codigo_de_barras_sigue_usando_la_api_v2() = runTest {
        val body = """{"status":1,"product":{"code":"111","product_name":"Galletas",
            "nutriments":{"energy-kcal_100g":450}}}"""
        val (api, engine) = apiRespondingWith(HttpStatusCode.OK, body)

        val product = api.productByBarcode("111")

        assertEquals("Galletas", product?.productName)
        val url = engine.requestHistory.single().url.toString()
        assertTrue(url.contains("/api/v2/product/111"), "URL usada: $url")
    }

    @Test
    fun un_producto_inexistente_devuelve_null_sin_lanzar() = runTest {
        val (api, _) = apiRespondingWith(
            HttpStatusCode.OK,
            """{"code":"000","status":0,"status_verbose":"product not found"}""",
        )
        assertNull(api.productByBarcode("000"))
    }

    @Test
    fun un_500_en_el_barcode_tambien_da_mensaje_propio() = runTest {
        val (api, _) = apiRespondingWith(HttpStatusCode.InternalServerError)
        val error = assertFailsWith<OpenFoodFactsException> { api.productByBarcode("111") }
        assertTrue(error.message!!.contains("500"), "mensaje: ${error.message}")
    }
}
