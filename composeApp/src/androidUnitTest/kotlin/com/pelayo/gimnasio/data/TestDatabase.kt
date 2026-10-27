package com.pelayo.gimnasio.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pelayo.gimnasio.data.db.DatabaseSeeder
import com.pelayo.gimnasio.data.db.WgerMappings
import com.pelayo.gimnasio.db.GimnasioDb

/**
 * Base de datos en memoria con el esquema real y los catálogos sembrados.
 * Sirve para validar que las consultas .sq se ejecutan de verdad contra SQLite.
 */
fun createTestDatabase(): GimnasioDb {
    // Bajo el classloader de Robolectric el driver no se autorregistra vía
    // ServiceLoader y DriverManager falla con "No suitable driver found".
    runCatching { Class.forName("org.sqlite.JDBC") }

    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    GimnasioDb.Schema.create(driver)
    // Igual que en producción: sin este pragma SQLite ignora ON DELETE CASCADE.
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return GimnasioDb(driver).also { DatabaseSeeder.seed(it) }
}

/** Inserta un ejercicio de catálogo con sus músculos y devuelve su id. */
fun GimnasioDb.insertTestExercise(
    wgerId: Long,
    name: String,
    primaryMuscleIds: List<Long>,
    secondaryMuscleIds: List<Long> = emptyList(),
    // Igual que al sincronizar con wger: el tipo lo decide el nombre, así que una
    // plancha entra como ejercicio de tiempo sin tener que decirlo aquí.
    exerciseType: String = WgerMappings.exerciseTypeFor(name, categoryId = null),
    metValue: Double = 3.5,
    imageUrls: List<Pair<String, Boolean>> = emptyList(),
): Long {
    catalogQueries.upsertExerciseByWgerId(
        nameEs = name,
        nameEn = null,
        descriptionEs = null,
        categoryId = null,
        videoUrl = null,
        metValue = metValue,
        exerciseType = exerciseType,
        wgerId = wgerId,
        searchName = DatabaseSeeder.searchNameFor(name, null),
    )
    val id = catalogQueries.selectExerciseIdByWgerId(wgerId).executeAsOne()
    primaryMuscleIds.forEach { catalogQueries.insertExerciseMuscle(id, it, "PRIMARY") }
    secondaryMuscleIds.forEach { catalogQueries.insertExerciseMuscle(id, it, "SECONDARY") }
    imageUrls.forEach { (url, isMain) ->
        catalogQueries.insertExerciseImage(id, url, if (isMain) 1 else 0)
    }
    return id
}
