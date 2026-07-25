package com.pelayo.gimnasio.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** Nombres de mes en español, indexados por número de mes (1..12). */
val monthNamesEs = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** Iniciales de los días, empezando en lunes como es costumbre en España. */
val weekdayInitialsEs = listOf("L", "M", "X", "J", "V", "S", "D")

data class MonthGrid(
    val year: Int,
    val month: Int,
    /** 42 celdas (6 semanas); null donde la celda cae fuera del mes. */
    val cells: List<LocalDate?>,
) {
    val title: String get() = "${monthNamesEs[month - 1].replaceFirstChar { it.uppercase() }} $year"
}

/**
 * Construye la rejilla mensual con las semanas empezando en lunes.
 * Las celdas anteriores al día 1 y posteriores al último día quedan a null.
 */
fun buildMonthGrid(year: Int, month: Int): MonthGrid {
    val first = LocalDate(year, month, 1)
    val daysInMonth = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    // isoDayNumber: lunes = 1 … domingo = 7.
    val leadingBlanks = first.dayOfWeek.isoDayNumber - 1

    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(LocalDate(year, month, day))
        while (size < 42) add(null)
    }
    return MonthGrid(year, month, cells)
}

/** Número de mes (1..12) de una fecha. */
val LocalDate.monthNumber: Int get() = month.number

/** Primer y último día del mes, para consultar el rango en la base de datos. */
fun monthRange(year: Int, month: Int): Pair<LocalDate, LocalDate> {
    val first = LocalDate(year, month, 1)
    val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    return first to last
}
