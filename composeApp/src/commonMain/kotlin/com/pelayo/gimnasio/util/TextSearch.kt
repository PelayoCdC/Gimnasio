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
        .map { normalizeForSearch(it).trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_SEARCH_TERMS)
