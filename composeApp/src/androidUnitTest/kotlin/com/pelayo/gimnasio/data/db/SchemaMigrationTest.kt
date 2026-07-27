package com.pelayo.gimnasio.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pelayo.gimnasio.data.dumpSchema
import com.pelayo.gimnasio.db.GimnasioDb
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Red de seguridad de las migraciones.
 *
 * El usuario ya tiene datos reales en el móvil. Si alguien cambia un `.sq` sin
 * añadir la migración correspondiente, la app instalada se encontraría una base
 * de datos vieja que nadie actualiza. Estos tests hacen que eso rompa el build
 * en vez de romperle la app.
 *
 * Si un test falla porque el cambio de esquema es intencionado:
 *  1. Añade `<versión actual>.sqm` en `commonMain/sqldelight/.../db/` con el
 *     ALTER TABLE o CREATE TABLE que lleve del esquema viejo al nuevo.
 *  2. Borra la instantánea `schema-snapshot.sql` y vuelve a ejecutar los tests:
 *     se regenera sola con el esquema nuevo.
 */
class SchemaMigrationTest {

    private val snapshotFile = File("src/androidUnitTest/resources/schema-snapshot.sql")

    /**
     * El esquema de la primera versión publicada, congelado. No se regenera nunca:
     * es la base de datos que tiene instalada quien lleva la app desde el principio,
     * y el punto de partida de la cadena de migraciones.
     */
    private val baselineFile = File("src/androidUnitTest/resources/schema-v1.sql")

    private fun emptyDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    /** Levanta una base de datos con el esquema de la versión 1. */
    private fun v1Driver(): JdbcSqliteDriver {
        val driver = emptyDriver()
        // Cada sentencia ocupa una línea; sqlite_master las guarda sin punto y
        // coma final. El fichero va ordenado alfabéticamente, así que hay que
        // crear las tablas antes que los índices que las referencian.
        val statements = baselineFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val (indexes, tables) = statements.partition { it.startsWith("CREATE INDEX", ignoreCase = true) }
        (tables + indexes).forEach { driver.execute(null, it, 0) }
        return driver
    }

    /** Esquema tal y como lo crea SQLDelight hoy a partir de los `.sq`. */
    private fun currentSchema(): String {
        val driver = emptyDriver()
        GimnasioDb.Schema.create(driver)
        return driver.dumpSchema()
    }

    @Test
    fun el_esquema_no_cambia_sin_que_nos_enteremos() {
        val current = currentSchema()

        if (!snapshotFile.exists()) {
            snapshotFile.parentFile.mkdirs()
            snapshotFile.writeText(current)
            fail(
                "No había instantánea del esquema y se ha creado en ${snapshotFile.path}. " +
                    "Revísala, añádela al control de versiones y vuelve a ejecutar los tests.",
            )
        }

        val expected = snapshotFile.readText().trim()
        assertEquals(
            expected,
            current.trim(),
            "El esquema de la base de datos ha cambiado. Si es a propósito, añade la migración " +
                "`${GimnasioDb.Schema.version}.sqm` y borra ${snapshotFile.path} para regenerar la " +
                "instantánea. Sin migración, la app instalada se rompería al actualizar.",
        )
    }

    @Test
    fun migrar_desde_la_primera_version_reproduce_el_esquema_actual() {
        val driver = v1Driver()

        // Se aplican todas las migraciones que haya desde la versión 1.
        GimnasioDb.Schema.migrate(driver, 1, GimnasioDb.Schema.version)

        assertEquals(
            currentSchema().trim(),
            driver.dumpSchema().trim(),
            "Aplicar las migraciones a una base de datos vieja no produce el esquema actual. " +
                "Falta una migración o está incompleta.",
        )
    }

    @Test
    fun la_version_del_esquema_cuadra_con_las_migraciones_existentes() {
        // SQLDelight numera la versión como 1 + número de ficheros .sqm.
        val migrationsDir = File("src/commonMain/sqldelight/com/pelayo/gimnasio/db")
        val migrationCount = migrationsDir.listFiles { file -> file.extension == "sqm" }?.size ?: 0

        assertEquals(
            (migrationCount + 1).toLong(),
            GimnasioDb.Schema.version,
            "Hay $migrationCount migraciones pero el esquema dice ser la versión " +
                "${GimnasioDb.Schema.version}.",
        )
    }

    @Test
    fun los_datos_del_usuario_sobreviven_a_la_migracion() {
        // Se parte de la versión 1 con datos dentro, que es la situación real de
        // quien actualiza la app con su histórico ya metido.
        val driver = v1Driver()
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        val db = GimnasioDb(driver)

        db.profileQueries.upsertProfile("Pelayo", "M", "1996-03-14", 178.0, 1.55, null, null)
        db.profileQueries.insertMeasurement("2026-07-25", 80.0, null, null, null, null, null)

        GimnasioDb.Schema.migrate(driver, 1, GimnasioDb.Schema.version)

        assertEquals("Pelayo", db.profileQueries.selectProfile().executeAsOne().name)
        assertTrue(db.profileQueries.selectMeasurements().executeAsList().isNotEmpty())
    }
}
