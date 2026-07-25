package com.pelayo.gimnasio.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Vuelca el esquema real de una base de datos SQLite en texto normalizado, para
 * poder compararlo. Se quitan comentarios y se colapsan los espacios, de forma
 * que reformatear un `.sq` no cuente como cambio de esquema pero añadir o quitar
 * una columna sí.
 */
fun SqlDriver.dumpSchema(): String {
    val statements = executeQuery(
        identifier = null,
        sql = """
            SELECT sql FROM sqlite_master
            WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'
            ORDER BY type, name
        """.trimIndent(),
        mapper = { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) {
                cursor.getString(0)?.let { rows.add(it.normalizeSql()) }
            }
            QueryResult.Value(rows)
        },
        parameters = 0,
    ).value

    return statements.sorted().joinToString("\n")
}

private val lineCommentRegex = Regex("--[^\n]*")
private val whitespaceRegex = Regex("\\s+")

private fun String.normalizeSql(): String =
    replace(lineCommentRegex, " ")
        .replace(whitespaceRegex, " ")
        .replace(" ,", ",")
        .replace("( ", "(")
        .replace(" )", ")")
        .trim()
