package com.pelayo.gimnasio.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun nowIsoString(): String = Clock.System.now().toString()

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

// Formatos de fecha en español, para que todas las pantallas digan las fechas
// igual en vez de cada una a su manera (hubo cuatro formatos distintos, ISO
// en crudo incluido).

/** "12 mar", para etiquetas con poco sitio (gráficas, chips). */
fun LocalDate.formatShortEs(): String = "$day ${monthNamesEs[monthNumber - 1].take(3)}"

/** "12 de marzo". */
fun LocalDate.formatDayMonthEs(): String = "$day de ${monthNamesEs[monthNumber - 1]}"

/** "12 de marzo de 2026". */
fun LocalDate.formatLongEs(): String = "${formatDayMonthEs()} de $year"
