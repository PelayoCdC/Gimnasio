package com.pelayo.gimnasio.domain.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OneRepMaxCalculatorTest {

    @Test
    fun con_una_repeticion_el_1rm_es_el_propio_peso() {
        assertEquals(100.0, OneRepMaxCalculator.estimate(100.0, 1))
    }

    @Test
    fun epley_con_diez_repeticiones() {
        // 100 × (1 + 10/30) = 133.33…
        assertEquals(133.33, OneRepMaxCalculator.estimate(100.0, 10)!!, absoluteTolerance = 0.01)
    }

    @Test
    fun sin_peso_o_sin_reps_no_hay_estimacion() {
        assertNull(OneRepMaxCalculator.estimate(null, 10))
        assertNull(OneRepMaxCalculator.estimate(100.0, null))
        assertNull(OneRepMaxCalculator.estimate(0.0, 10))
        assertNull(OneRepMaxCalculator.estimate(100.0, 0))
    }

    @Test
    fun por_encima_del_tope_de_reps_la_formula_no_estima() {
        assertNull(OneRepMaxCalculator.estimate(100.0, OneRepMaxCalculator.MAX_RELIABLE_REPS + 1))
    }
}
