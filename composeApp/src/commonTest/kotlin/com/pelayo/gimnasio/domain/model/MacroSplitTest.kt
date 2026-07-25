package com.pelayo.gimnasio.domain.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroSplitTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.5) {
        assertTrue(abs(expected - actual) < tolerance, "esperado $expected, obtenido $actual")
    }

    @Test
    fun la_proteina_se_fija_por_peso_corporal() {
        val macros = MacroSplit.split(2500.0, bodyWeightKg = 80.0, goal = RoutineGoal.HIPERTROFIA)
        assertClose(160.0, macros.protein, 0.01) // 2.0 g/kg
    }

    @Test
    fun en_definicion_sube_la_proteina() {
        val macros = MacroSplit.split(2000.0, bodyWeightKg = 80.0, goal = RoutineGoal.PERDIDA_GRASA)
        assertClose(176.0, macros.protein, 0.01) // 2.2 g/kg
    }

    @Test
    fun la_grasa_es_el_veinticinco_por_ciento_de_las_calorias() {
        val macros = MacroSplit.split(2400.0, bodyWeightKg = 80.0, goal = RoutineGoal.HIPERTROFIA)
        // 2400 × 0.25 / 9 = 66.67 g
        assertClose(66.67, macros.fat, 0.01)
    }

    @Test
    fun los_macros_cuadran_con_las_calorias_objetivo() {
        val target = 2500.0
        val macros = MacroSplit.split(target, bodyWeightKg = 80.0, goal = RoutineGoal.HIPERTROFIA)
        val fromMacros = macros.protein * 4 + macros.carbs * 4 + macros.fat * 9
        assertClose(target, fromMacros, 1.0)
        assertEquals(target, macros.kcal)
    }

    @Test
    fun con_muy_pocas_calorias_los_hidratos_no_bajan_de_cero() {
        // Proteína y grasa por sí solas ya superan el objetivo: no debe salir negativo.
        val macros = MacroSplit.split(800.0, bodyWeightKg = 100.0, goal = RoutineGoal.PERDIDA_GRASA)
        assertTrue(macros.carbs >= 0.0, "hidratos negativos: ${macros.carbs}")
    }

    @Test
    fun la_resistencia_deja_mas_margen_para_hidratos() {
        val hipertrofia = MacroSplit.split(2500.0, 80.0, RoutineGoal.HIPERTROFIA)
        val resistencia = MacroSplit.split(2500.0, 80.0, RoutineGoal.RESISTENCIA)
        assertTrue(
            resistencia.carbs > hipertrofia.carbs,
            "resistencia ${resistencia.carbs} vs hipertrofia ${hipertrofia.carbs}",
        )
    }
}
