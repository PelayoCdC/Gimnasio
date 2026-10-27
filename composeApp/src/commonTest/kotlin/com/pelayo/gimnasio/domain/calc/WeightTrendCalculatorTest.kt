package com.pelayo.gimnasio.domain.calc

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeightTrendCalculatorTest {

    private val today = LocalDate(2026, 7, 30)

    @Test
    fun sin_pesajes_no_hay_tendencia() {
        assertNull(WeightTrendCalculator.calculate(emptyList(), today))
    }

    @Test
    fun sin_pesajes_esta_semana_tampoco() {
        val entries = listOf(LocalDate(2026, 6, 1) to 80.0)
        assertNull(WeightTrendCalculator.calculate(entries, today))
    }

    @Test
    fun la_media_es_de_los_ultimos_siete_dias() {
        val entries = listOf(
            LocalDate(2026, 7, 28) to 80.0,
            LocalDate(2026, 7, 29) to 81.0,
            // Fuera de la ventana: no debe contar.
            LocalDate(2026, 7, 1) to 90.0,
        )
        val trend = assertNotNull(WeightTrendCalculator.calculate(entries, today))
        assertEquals(80.5, trend.averageKg, absoluteTolerance = 0.001)
    }

    @Test
    fun el_ritmo_compara_con_la_semana_anterior() {
        val entries = listOf(
            LocalDate(2026, 7, 29) to 79.0,
            LocalDate(2026, 7, 20) to 80.0,
        )
        val trend = assertNotNull(WeightTrendCalculator.calculate(entries, today))
        assertEquals(-1.0, trend.weeklyRateKg!!, absoluteTolerance = 0.001)
    }

    @Test
    fun sin_semana_anterior_el_ritmo_es_nulo() {
        val entries = listOf(LocalDate(2026, 7, 29) to 79.0)
        val trend = assertNotNull(WeightTrendCalculator.calculate(entries, today))
        assertNull(trend.weeklyRateKg)
    }

    @Test
    fun una_medicion_de_hace_meses_no_se_presenta_como_cambio_reciente() {
        // El bug que sustituye: "las dos últimas mediciones" comparaba hoy con
        // una de hace meses como si fuera de ayer.
        val entries = listOf(
            LocalDate(2026, 7, 29) to 79.0,
            LocalDate(2026, 1, 10) to 85.0,
        )
        val trend = assertNotNull(WeightTrendCalculator.calculate(entries, today))
        assertNull(trend.weeklyRateKg)
    }

    @Test
    fun la_proyeccion_calcula_la_fecha_de_llegada() {
        // 80 kg, objetivo 78, perdiendo 0,5/semana → 4 semanas → 28 días.
        val date = WeightTrendCalculator.projectedDate(80.0, 78.0, -0.5, today)
        assertEquals(LocalDate(2026, 8, 27), date)
    }

    @Test
    fun sin_objetivo_o_sin_ritmo_no_hay_proyeccion() {
        assertNull(WeightTrendCalculator.projectedDate(80.0, null, -0.5, today))
        assertNull(WeightTrendCalculator.projectedDate(80.0, 78.0, null, today))
        // Ruido de báscula: por debajo del mínimo no es tendencia.
        assertNull(WeightTrendCalculator.projectedDate(80.0, 78.0, -0.01, today))
    }

    @Test
    fun un_ritmo_que_aleja_del_objetivo_no_proyecta_nada() {
        // Quiere bajar a 78 pero está subiendo: prometer una fecha sería mentir.
        assertNull(WeightTrendCalculator.projectedDate(80.0, 78.0, 0.5, today))
    }

    @Test
    fun una_proyeccion_a_anios_vista_se_descarta() {
        // 20 kg a 0,05/semana serían 400 semanas: eso no es una estimación.
        assertNull(WeightTrendCalculator.projectedDate(100.0, 80.0, -0.05, today))
    }
}
