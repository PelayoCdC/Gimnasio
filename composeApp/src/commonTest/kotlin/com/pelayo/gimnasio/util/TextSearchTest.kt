package com.pelayo.gimnasio.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextSearchTest {

    @Test
    fun normaliza_mayusculas_y_tildes() {
        assertEquals("press de banca", normalizeForSearch("PRESS de Bánca"))
        assertEquals("espanol", normalizeForSearch("Español"))
    }

    @Test
    fun parte_la_consulta_en_terminos_y_los_topa() {
        assertEquals(listOf("press", "inclinado"), searchTerms("Press  inclinado"))
        assertEquals(MAX_SEARCH_TERMS, searchTerms("a b c d e f").size)
    }

    @Test
    fun quita_los_comodines_de_like() {
        // % y _ venían del teclado tal cual y actuaban de comodín en la consulta.
        assertEquals(listOf("press"), searchTerms("%press_"))
    }

    @Test
    fun contains_word_starting_exige_limite_inicial() {
        // El caso que mandaba al pino cualquier agarre supino.
        assertFalse("curl con agarre supino".containsWordStarting("pino"))
        assertTrue("hacer el pino".containsWordStarting("pino"))
        // El límite es solo inicial: los prefijos siguen valiendo.
        assertTrue("sentadilla explosiva".containsWordStarting("explosiv"))
        // Y "andar" ya no vive dentro de "estándar".
        assertFalse("press estandar".containsWordStarting("andar"))
    }
}
