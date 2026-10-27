package com.pelayo.gimnasio.util

import kotlin.math.round

/**
 * Número con un decimal como mucho: "72.5", y "72" si es entero. Redondea, no
 * trunca. Es el único formateador de decimales de la app; había cinco copias.
 */
fun Double.oneDecimal(): String =
    if (this == toLong().toDouble()) toLong().toString() else (round(this * 10) / 10.0).toString()
