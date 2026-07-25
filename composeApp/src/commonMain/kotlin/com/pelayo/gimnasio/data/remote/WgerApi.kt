package com.pelayo.gimnasio.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class WgerApi(private val client: HttpClient) {

    companion object {
        const val BASE_URL = "https://wger.de/api/v2"
        const val PAGE_SIZE = 100
    }

    suspend fun getLanguages(): List<WgerLanguage> =
        client.get("$BASE_URL/language/") {
            parameter("limit", 100)
            parameter("format", "json")
        }.body<WgerPage<WgerLanguage>>().results

    suspend fun getMuscles(): List<WgerMuscleDto> =
        client.get("$BASE_URL/muscle/") {
            parameter("limit", 100)
            parameter("format", "json")
        }.body<WgerPage<WgerMuscleDto>>().results

    suspend fun getEquipment(): List<WgerEquipmentDto> =
        client.get("$BASE_URL/equipment/") {
            parameter("limit", 100)
            parameter("format", "json")
        }.body<WgerPage<WgerEquipmentDto>>().results

    suspend fun getExercisePage(offset: Int): WgerPage<WgerExerciseInfoDto> =
        client.get("$BASE_URL/exerciseinfo/") {
            parameter("limit", PAGE_SIZE)
            parameter("offset", offset)
            parameter("format", "json")
        }.body()
}
