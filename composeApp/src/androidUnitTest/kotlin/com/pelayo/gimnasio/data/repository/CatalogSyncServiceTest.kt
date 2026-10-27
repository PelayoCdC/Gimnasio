package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.WgerApi
import com.russhwolf.settings.Settings
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
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Settings en memoria: el artefacto no-arg no expone MapSettings. */
private class FakeSettings : Settings {
    private val map = mutableMapOf<String, Any>()

    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = key in map

    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int

    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long

    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String =
        map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String

    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float =
        map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float

    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double =
        map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double

    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

/**
 * El servicio de sincronización con wger no tenía ni un test, y era el único de
 * los repositorios/servicios sin red de seguridad.
 */
class CatalogSyncServiceTest {

    private val languages = """{"count":2,"next":null,"results":[
        {"id":4,"short_name":"es"},{"id":2,"short_name":"en"}]}"""

    private val equipment = """{"count":1,"next":null,"results":[{"id":1,"name":"Barbell"}]}"""

    /** Un ejercicio con traducción al español y material fuera de la página de equipamiento. */
    private val exercises = """{"count":1,"next":null,"results":[{
        "id":345,
        "category":{"id":11,"name":"Chest"},
        "muscles":[{"id":4}],
        "muscles_secondary":[],
        "equipment":[{"id":1,"name":"Barbell"},{"id":99,"name":"Inventado"}],
        "translations":[{"id":1,"name":"Press de banca","description":"","language":4,"aliases":[]}],
        "images":[],
        "videos":[]}]}"""

    private fun service(
        db: com.pelayo.gimnasio.db.GimnasioDb = createTestDatabase(),
        exercisesBody: String = exercises,
        exercisesStatus: HttpStatusCode = HttpStatusCode.OK,
    ): CatalogSyncService {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.contains("/language/") -> respond(
                    languages, HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                path.contains("/equipment/") -> respond(
                    equipment, HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> if (exercisesStatus == HttpStatusCode.OK) {
                    respond(
                        exercisesBody, HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                } else {
                    respondError(exercisesStatus)
                }
            }
        }
        val api = WgerApi(
            HttpClient(engine) { install(ContentNegotiation) { json(HttpClientFactory.json) } },
        )
        return CatalogSyncService(api, db, FakeSettings())
    }

    @Test
    fun una_sincronizacion_completa_importa_y_termina_en_finished() = runTest {
        val db = createTestDatabase()
        val states = service(db).sync().toList()

        val finished = assertIs<SyncProgress.Finished>(states.last())
        assertEquals(1, finished.imported)
        assertEquals(1L, db.catalogQueries.countExercises().executeAsOne())
    }

    @Test
    fun el_material_desconocido_no_tumba_la_transaccion() = runTest {
        val db = createTestDatabase()
        // El ejercicio referencia el equipamiento 99, que no está en /equipment/:
        // insertarlo violaría la clave foránea y abortaba la página entera.
        val states = service(db).sync().toList()

        assertIs<SyncProgress.Finished>(states.last())
        val exerciseId = db.catalogQueries.selectExerciseIdByWgerId(345).executeAsOne()
        val equipmentIds = db.catalogQueries.selectEquipmentForExercise(exerciseId)
            .executeAsList().map { it.id }
        assertEquals(listOf(1L), equipmentIds, "solo el material conocido")
    }

    @Test
    fun un_503_de_wger_sale_como_fallo_legible_no_como_json_roto() = runTest {
        val states = service(exercisesStatus = HttpStatusCode.ServiceUnavailable).sync().toList()
        val failed = assertIs<SyncProgress.Failed>(states.last())
        assertTrue(
            "wger" in failed.message && "503" in failed.message,
            "el mensaje debe ser el traducido, no el de deserialización: ${failed.message}",
        )
    }

    @Test
    fun una_excepcion_del_colector_no_se_convierte_en_failed() = runTest {
        // La transparencia del Flow: si el colector lanza, la excepción tiene que
        // subir, no ser cazada por el servicio y reemitida como Failed.
        var thrown = false
        try {
            service().sync().collect { progress ->
                if (progress is SyncProgress.Finished) error("boom del colector")
            }
        } catch (e: IllegalStateException) {
            thrown = e.message == "boom del colector"
        }
        assertTrue(thrown, "la excepción del colector debe propagarse tal cual")
    }
}
