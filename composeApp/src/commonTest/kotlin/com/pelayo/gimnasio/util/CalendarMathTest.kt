package com.pelayo.gimnasio.util

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarMathTest {

    @Test
    fun julio_2026_empieza_en_miercoles() {
        // 1 de julio de 2026 es miércoles → 2 huecos antes (lunes y martes).
        val grid = buildMonthGrid(2026, 7)
        assertNull(grid.cells[0])
        assertNull(grid.cells[1])
        assertEquals(LocalDate(2026, 7, 1), grid.cells[2])
        assertEquals(LocalDate(2026, 7, 31), grid.cells[32])
        assertEquals("Julio 2026", grid.title)
    }

    @Test
    fun la_rejilla_siempre_tiene_42_celdas() {
        listOf(2026 to 2, 2024 to 2, 2026 to 1, 2026 to 12).forEach { (year, month) ->
            assertEquals(42, buildMonthGrid(year, month).cells.size)
        }
    }

    @Test
    fun febrero_bisiesto_tiene_29_dias() {
        val grid = buildMonthGrid(2024, 2)
        assertEquals(29, grid.cells.filterNotNull().size)
        assertEquals(LocalDate(2024, 2, 29), grid.cells.filterNotNull().last())
    }

    @Test
    fun febrero_no_bisiesto_tiene_28_dias() {
        assertEquals(28, buildMonthGrid(2026, 2).cells.filterNotNull().size)
    }

    @Test
    fun un_mes_que_empieza_en_lunes_no_deja_huecos_iniciales() {
        // 1 de junio de 2026 es lunes.
        assertEquals(LocalDate(2026, 6, 1), buildMonthGrid(2026, 6).cells[0])
    }

    @Test
    fun el_rango_del_mes_cubre_del_primero_al_ultimo_dia() {
        assertEquals(LocalDate(2026, 7, 1) to LocalDate(2026, 7, 31), monthRange(2026, 7))
        assertEquals(LocalDate(2024, 2, 1) to LocalDate(2024, 2, 29), monthRange(2024, 2))
        assertEquals(LocalDate(2026, 12, 1) to LocalDate(2026, 12, 31), monthRange(2026, 12))
    }
}
