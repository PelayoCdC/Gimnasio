package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.BmiCategory
import com.pelayo.gimnasio.domain.model.Sex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyMetricsCalculatorTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.01) {
        assertTrue(abs(expected - actual) < tolerance, "esperado $expected, obtenido $actual")
    }

    @Test
    fun bmi_hombre_80kg_175cm() {
        assertClose(26.12, BodyMetricsCalculator.bmi(80.0, 175.0), 0.01)
    }

    @Test
    fun bmi_categorias_oms() {
        assertEquals(BmiCategory.BAJO_PESO, BodyMetricsCalculator.bmiCategory(18.4))
        assertEquals(BmiCategory.NORMAL, BodyMetricsCalculator.bmiCategory(18.5))
        assertEquals(BmiCategory.NORMAL, BodyMetricsCalculator.bmiCategory(24.9))
        assertEquals(BmiCategory.SOBREPESO, BodyMetricsCalculator.bmiCategory(25.0))
        assertEquals(BmiCategory.OBESIDAD, BodyMetricsCalculator.bmiCategory(30.0))
    }

    @Test
    fun bmr_mifflin_hombre_30a_175cm_80kg() {
        // 10·80 + 6.25·175 − 5·30 + 5 = 1748.75
        assertClose(1748.75, BodyMetricsCalculator.bmrMifflinStJeor(80.0, 175.0, 30, Sex.M))
    }

    @Test
    fun bmr_mifflin_mujer_30a_175cm_80kg() {
        // 10·80 + 6.25·175 − 5·30 − 161 = 1582.75
        assertClose(1582.75, BodyMetricsCalculator.bmrMifflinStJeor(80.0, 175.0, 30, Sex.F))
    }

    @Test
    fun tdee_moderado() {
        assertClose(2710.56, BodyMetricsCalculator.tdee(1748.75, 1.55), 0.01)
    }

    @Test
    fun navy_hombre_valores_tipicos() {
        // altura 175, cuello 38, cintura 85 → ≈16.9 %
        val result = BodyMetricsCalculator.bodyFatNavy(Sex.M, 175.0, 38.0, 85.0)
        assertClose(16.94, result!!, 0.1)
    }

    @Test
    fun navy_mujer_requiere_cadera() {
        assertNull(BodyMetricsCalculator.bodyFatNavy(Sex.F, 165.0, 33.0, 70.0, hipCm = null))
    }

    @Test
    fun navy_sin_medidas_devuelve_null() {
        assertNull(BodyMetricsCalculator.bodyFatNavy(Sex.M, 175.0, null, 85.0))
        assertNull(BodyMetricsCalculator.bodyFatNavy(Sex.M, 175.0, 38.0, null))
    }

    @Test
    fun navy_cintura_menor_que_cuello_devuelve_null() {
        assertNull(BodyMetricsCalculator.bodyFatNavy(Sex.M, 175.0, 40.0, 39.0))
    }

    @Test
    fun deurenberg_hombre_30a_bmi26() {
        // 1.20·26.1224 + 0.23·30 − 10.8·1 − 5.4 ≈ 22.05
        val bmi = BodyMetricsCalculator.bmi(80.0, 175.0)
        assertClose(22.05, BodyMetricsCalculator.bodyFatDeurenberg(bmi, 30, Sex.M), 0.1)
    }

    @Test
    fun deurenberg_mujer_mayor_que_hombre_a_igual_bmi() {
        val bmi = 25.0
        val m = BodyMetricsCalculator.bodyFatDeurenberg(bmi, 30, Sex.M)
        val f = BodyMetricsCalculator.bodyFatDeurenberg(bmi, 30, Sex.F)
        assertClose(10.8, f - m, 0.001)
    }
}
