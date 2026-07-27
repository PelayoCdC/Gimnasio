package com.pelayo.gimnasio.ui.components

import androidx.compose.ui.graphics.Path

/** Letras (comandos) y números; cualquier otra cosa del SVG sobra aquí. */
private val token = Regex("[A-Za-z]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?")

/**
 * Lee una ruta SVG con `M`, `L`, `C`, `Q` y `Z` **en absoluto** y la convierte en
 * un [Path] de Compose.
 *
 * Existe para poder escribir las formas del mapa muscular y del logotipo tal cual
 * salen del SVG (o del `pathData` de un vector de Android) en vez de traducirlas
 * a mano a llamadas encadenadas, que con veinte curvas es pedir una errata.
 *
 * Los comandos que no entiende (arcos, coordenadas relativas…) se saltan con sus
 * números: se pierde ese trozo del dibujo, pero el resto se sigue viendo. Antes
 * los números del comando ignorado se colaban en el anterior y podían tumbar la
 * pantalla entera con un `NumberFormatException`.
 */
internal fun svgPath(data: String): Path {
    val path = Path()
    val tokens = token.findAll(data).map { it.value }.toList()
    var index = 0
    var command = ""

    fun isNumber(position: Int) =
        position < tokens.size && tokens[position].first().isLetter().not()

    fun hasNumbers(count: Int) = (0 until count).all { isNumber(index + it) }

    fun next() = tokens[index++].toFloat()

    while (index < tokens.size) {
        val current = tokens[index]
        if (current.first().isLetter()) {
            command = current
            index++
            if (command == "Z") path.close()
            continue
        }
        when {
            command == "M" && hasNumbers(2) -> path.moveTo(next(), next())
            command == "L" && hasNumbers(2) -> path.lineTo(next(), next())
            command == "C" && hasNumbers(6) ->
                path.cubicTo(next(), next(), next(), next(), next(), next())

            command == "Q" && hasNumbers(4) -> path.quadraticTo(next(), next(), next(), next())
            // Comando no soportado, o le faltan números: se descarta este.
            else -> index++
        }
    }
    return path
}
