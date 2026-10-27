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

    /** Responde a cada petición por orden: primero el buscador, luego el lote v2. */
    private fun apiRespondingInOrder(vararg bodies: String): Pair<OpenFoodFactsApi, MockEngine> {
        var index = 0
        val engine = MockEngine {
            respond(
                content = bodies[index++.coerceAtMost(bodies.lastIndex)],
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
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
    fun completa_los_nutrientes_con_una_segunda_peticion_en_lote() = runTest {
        // El buscador nuevo no devuelve nutrientes por mucho que se pidan, así
        // que de él solo salen los códigos.
        val (api, engine) = apiRespondingInOrder(
            """{"count":2,"hits":[{"code":"111"},{"code":"222"}]}""",
            """{"count":2,"products":[
                {"code":"222","product_name":"Avena integral","nutriments":{"energy-kcal_100g":380}},
                {"code":"111","product_name":"Galletas de avena","nutriments":{"energy-kcal_100g":450}}
            ]}""",
        )

        val products = api.search("avena")

        assertEquals(2, engine.requestHistory.size, "debería hacer dos peticiones")
        val lote = engine.requestHistory[1].url.toString()
        assertTrue(lote.startsWith("https://world.openfoodfacts.org/api/v2/search"), "URL: $lote")
        assertTrue(lote.contains("code=111%2C222") || lote.contains("code=111,222"), "URL: $lote")
        assertTrue(lote.contains("nutriments"), "no pide los nutrientes: $lote")

        // Manda el orden de relevancia del buscador, no el que devuelva el lote.
        assertEquals(listOf("111", "222"), products.map { it.code })
        assertEquals(450.0, products.first().nutriments.kcalPer100)
    }

    @Test
    fun sin_resultados_no_hace_la_segunda_peticion() = runTest {
        val (api, engine) = apiRespondingWith(HttpStatusCode.OK, """{"count":0,"hits":[]}""")

        assertTrue(api.search("noexiste").isEmpty())
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun las_marcas_valen_como_lista_y_como_cadena() = runTest {
        // El buscador nuevo devuelve brands como lista y la v2 como cadena; que la
        // lista reventara la deserialización es lo que dejó la búsqueda inservible.
        val (api, _) = apiRespondingInOrder(
            """{"count":2,"hits":[{"code":"111","brands":["Hacendado","Mercadona"]},{"code":"222"}]}""",
            """{"count":2,"products":[
                {"code":"111","product_name":"Leche","brands":["Hacendado","Mercadona"],
                 "nutriments":{"energy-kcal_100g":45}},
                {"code":"222","product_name":"Queso","brands":"Garcia Baquero",
                 "nutriments":{"energy-kcal_100g":300}}
            ]}""",
        )

        val products = api.search("lacteos")

        assertEquals("Hacendado, Mercadona", products.first { it.code == "111" }.brands)
        assertEquals("Garcia Baquero", products.first { it.code == "222" }.brands)
    }

    @Test
    fun un_503_es_el_corte_por_limite_de_peticiones() = runTest {
        // OFF no se cae: corta a los anónimos con un 503 y una página de aviso.
        val (api, _) = apiRespondingWith(HttpStatusCode.ServiceUnavailable)

        val error = assertFailsWith<OpenFoodFactsException> { api.search("avena") }

        assertTrue(error.isRateLimited, "debe poder bloquear el botón")
        assertEquals(60, error.retryAfterSeconds)
        assertTrue(error.message!!.contains("cortado"), "mensaje: ${error.message}")
        assertTrue(error.message!!.contains("crear el alimento a mano"))
    }

    @Test
    fun demasiadas_peticiones_avisa_de_esperar() = runTest {
        val (api, _) = apiRespondingWith(HttpStatusCode.TooManyRequests)

        val error = assertFailsWith<OpenFoodFactsException> { api.search("avena") }

        assertTrue(error.isRateLimited)
        assertTrue(error.message!!.contains("espera"), "mensaje: ${error.message}")
    }

    @Test
    fun una_caida_de_verdad_no_bloquea_el_boton() = runTest {
        val (api, _) = apiRespondingWith(HttpStatusCode.BadGateway)

        val error = assertFailsWith<OpenFoodFactsException> { api.search("avena") }

        assertTrue(!error.isRateLimited)
        assertTrue(error.message!!.contains("502"), "mensaje: ${error.message}")
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
