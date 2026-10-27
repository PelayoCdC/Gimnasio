package com.pelayo.gimnasio.util

/**
 * Texto en minúsculas y sin tildes ni diéresis.
 *
 * Es lo que se guarda en `Exercise.searchName` y contra lo que se compara la
 * consulta, para que "Press de banca inclinado" salga escribiendo "PRESS" o
 * "inclinádo". SQLite no sabe hacer esto: su `LIKE` ignora mayúsculas solo en
 * ASCII y no toca los acentos.
 */
fun normalizeForSearch(text: String): String = buildString(text.length) {
    for (char in text.lowercase()) {
        append(
            when (char) {
                'á', 'à', 'ä', 'â', 'ã' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                'ç' -> 'c'
                else -> char
            },
        )
    }
}

/**
 * true si [keyword] aparece en el texto empezando en límite de palabra.
 *
 * Basta con el límite inicial: así "explosiv" sigue cazando "explosivo" y
 * "explosiva", pero "pino" deja de aparecer dentro de "supino", que es lo que
 * mandaba al pino cualquier ejercicio con agarre supino. Vale también para
 * frases ("buenos dias"): el límite se comprueba donde empieza la primera palabra.
 */
fun String.containsWordStarting(keyword: String): Boolean {
    var index = indexOf(keyword)
    while (index >= 0) {
        if (index == 0 || !this[index - 1].isLetterOrDigit()) return true
        index = indexOf(keyword, index + 1)
    }
    return false
}

/** Cuántos términos de la búsqueda se tienen en cuenta. */
const val MAX_SEARCH_TERMS = 4

/**
 * Parte la consulta en palabras sueltas y normalizadas. Cada una se exige por
 * separado, así que "press inclinado" encuentra "Press de banca inclinado":
 * buscar la frase entera y adyacente no encontraba nada.
 *
 * Se queda con las [MAX_SEARCH_TERMS] primeras porque la consulta SQL tiene un
 * hueco por término; con cuatro palabras ya no queda casi nada por filtrar.
 */
fun searchTerms(query: String): List<String> =
    query.split(' ', '\t', '\n', ',', ';')
        // Sin % ni _: son comodines de LIKE y venían del teclado tal cual.
        .map { normalizeForSearch(it).replace("%", "").replace("_", "").trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_SEARCH_TERMS)
