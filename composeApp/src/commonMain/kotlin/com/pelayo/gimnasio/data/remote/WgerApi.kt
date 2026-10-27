package com.pelayo.gimnasio.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

/** Error de wger con un mensaje ya listo para enseñar al usuario. */
class WgerException(message: String) : Exception(message)

class WgerApi(private val client: HttpClient) {

    companion object {
        const val BASE_URL = "https://wger.de/api/v2"
        const val PAGE_SIZE = 100
    }

    suspend fun getLanguages(): List<WgerLanguage> =
        client.get("$BASE_URL/language/") {
            parameter("limit", 100)
            parameter("format", "json")
        }.ensureSuccess().body<WgerPage<WgerLanguage>>().results

    suspend fun getEquipment(): List<WgerEquipmentDto> =
        client.get("$BASE_URL/equipment/") {
            parameter("limit", 100)
            parameter("format", "json")
        }.ensureSuccess().body<WgerPage<WgerEquipmentDto>>().results

    suspend fun getExercisePage(offset: Int): WgerPage<WgerExerciseInfoDto> =
        client.get("$BASE_URL/exerciseinfo/") {
            parameter("limit", PAGE_SIZE)
            parameter("offset", offset)
            parameter("format", "json")
        }.ensureSuccess().body()

    /**
     * Un 503 de wger devuelve HTML, y sin esta comprobación acababa enseñado como
     * un galimatías de deserialización ("Unexpected JSON token at offset 0…").
     */
    private fun HttpResponse.ensureSuccess(): HttpResponse {
        if (status.value in 200..299) return this
        throw WgerException(
            when (status.value) {
                in 500..599 ->
                    "wger no responde ahora mismo (error ${status.value}). Inténtalo más tarde."

                else -> "wger devolvió un error ${status.value}."
            },
        )
    }
}
