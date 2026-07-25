package com.pelayo.gimnasio.domain.calc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class EnergyCalculatorTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.01) {
        assertTrue(abs(expected - actual) < tolerance, "esperado $expected, obtenido $actual")
    }

    @Test
    fun met6_80kg_1h_son_480kcal() {
        assertClose(480.0, EnergyCalculator.kcalBurned(met = 6.0, weightKg = 80.0, hours = 1.0))
    }

    @Test
    fun horas_activas_3sets_40s_descanso90() {
        // 3 × (40 + 45) s = 255 s = 0.070833 h
        assertClose(0.070833, EnergyCalculator.estimateActiveHours(3, 40.0, 90), 0.0001)
    }

    @Test
    fun kcal_ejercicio_fuerza_moderada() {
        // MET 3.5 × 80 kg × 0.070833 h ≈ 19.83 kcal
        assertClose(19.83, EnergyCalculator.exerciseKcal(3.5, 80.0, sets = 3), 0.05)
    }

    @Test
    fun cero_sets_cero_kcal() {
        assertClose(0.0, EnergyCalculator.exerciseKcal(3.5, 80.0, sets = 0))
    }
}
