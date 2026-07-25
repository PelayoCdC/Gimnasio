package com.pelayo.gimnasio.platform

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.pelayo.gimnasio.db.GimnasioDb

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = GimnasioDb.Schema,
        context = context,
        name = "gimnasio.db",
        callback = object : AndroidSqliteDriver.Callback(GimnasioDb.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
}
