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
         * Servicio de búsqueda actual de Open Food Facts. El antiguo
         * `cgi/search.pl` está saturado y responde 503 de forma sistemática.
         */
        const val SEARCH_URL = "https://search.openfoodfacts.org/search"

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

    suspend fun search(query: String, pageSize: Int = 25): List<OffProduct> {
        val response = client.get(SEARCH_URL) {
            parameter("q", query)
            parameter("page_size", pageSize)
        }.ensureSuccess()
        return response.body<OffSearchResponse>().hits
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
