package com.pelayo.gimnasio.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/** Error de red o de servicio con un mensaje ya listo para enseñar al usuario. */
class OpenFoodFactsException(message: String) : Exception(message)

class OpenFoodFactsApi(private val client: HttpClient) {

    companion object {
        const val PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product"

        /**
         * Servicio de búsqueda por texto de Open Food Facts. El antiguo
         * `cgi/search.pl` está limitadísimo y responde 503 a poco que se insista.
         */
        const val SEARCH_URL = "https://search.openfoodfacts.org/search"

        /** Búsqueda de la API v2, que admite varios códigos separados por comas. */
        const val PRODUCTS_URL = "https://world.openfoodfacts.org/api/v2/search"

        const val FIELDS =
            "code,product_name,product_name_es,brands,image_front_small_url,serving_quantity,nutriments"
    }

    /** Devuelve null si el código de barras no está en la base de datos de OFF. */
    suspend fun productByBarcode(barcode: String): OffProduct? {
        val response = client.get("$PRODUCT_URL/$barcode") {
            parameter("fields", FIELDS)
        }.ensureSuccess()
        val body: OffProductResponse = response.body()
        return body.product.takeIf { body.status == 1 }
    }

    /**
     * Busca en dos pasos, porque ningún endpoint hace las dos cosas bien:
     *
     * 1. `search.openfoodfacts.org` busca por texto de maravilla, pero **no
     *    devuelve los nutrientes** por mucho que se pidan en `fields`, así que
     *    solo se le piden los códigos de barras.
     * 2. La búsqueda de la API v2 acepta varios códigos separados por comas y sí
     *    trae `nutriments`, de modo que una sola petición completa la lista.
     *
     * El orden de relevancia lo pone el primer paso; el segundo solo rellena.
     */
    suspend fun search(query: String, pageSize: Int = 25): List<OffProduct> {
        val codes = client.get(SEARCH_URL) {
            parameter("q", query)
            parameter("page_size", pageSize)
            parameter("fields", "code")
        }.ensureSuccess()
            .body<OffSearchResponse>().hits
            .mapNotNull { it.code?.takeIf { code -> code.isNotBlank() } }
            .distinct()
        if (codes.isEmpty()) return emptyList()

        val byCode = client.get(PRODUCTS_URL) {
            parameter("code", codes.joinToString(","))
            parameter("fields", FIELDS)
            parameter("page_size", codes.size)
        }.ensureSuccess()
            .body<OffProductsResponse>().products
            .associateBy { it.code }

        return codes.mapNotNull { byCode[it] }
    }

    /**
     * Traduce los fallos del servicio a mensajes en español. Open Food Facts se
     * satura a menudo y devuelve 503; conviene distinguirlo de un error nuestro.
     */
    private fun HttpResponse.ensureSuccess(): HttpResponse {
        if (status.isSuccess()) return this
        val message = when (status) {
            HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadGateway,
            HttpStatusCode.GatewayTimeout,
            ->
                "Open Food Facts no responde ahora mismo (error ${status.value}). " +
                    "Inténtalo en un rato, o crea el alimento a mano."

            HttpStatusCode.TooManyRequests ->
                "Demasiadas búsquedas seguidas. Espera un momento antes de volver a intentarlo."

            HttpStatusCode.NotFound ->
                "Open Food Facts no encontró ese producto."

            else ->
                "Open Food Facts devolvió un error ${status.value}."
        }
        throw OpenFoodFactsException(message)
    }
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
