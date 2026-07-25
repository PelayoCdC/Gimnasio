package com.pelayo.gimnasio.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.pelayo.gimnasio.db.GimnasioDb

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = GimnasioDb.Schema,
        name = "gimnasio.db",
        // SQLite ignora las claves foráneas si no se activan explícitamente,
        // y el esquema depende de ON DELETE CASCADE.
        onConfiguration = { config ->
            config.copy(
                extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true),
            )
        },
    )
}
