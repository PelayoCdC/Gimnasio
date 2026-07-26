package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseComplexity
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseComplexityClassifierTest {

    private fun classify(name: String, equipment: Set<Long> = emptySet()) =
        ExerciseComplexityClassifier.classify(name, equipment)

    @Test
    fun los_derivados_del_halterofilia_son_avanzados() {
        assertEquals(ExerciseComplexity.AVANZADO, classify("Cargada de fuerza", setOf(1L)))
        assertEquals(ExerciseComplexity.AVANZADO, classify("Arrancada colgante"))
        assertEquals(ExerciseComplexity.AVANZADO, classify("Push jerk"))
    }

    @Test
    fun los_movimientos_con_riesgo_de_hombro_o_columna_son_avanzados() {
        assertEquals(ExerciseComplexity.AVANZADO, classify("Press tras nuca", setOf(1L)))
        assertEquals(ExerciseComplexity.AVANZADO, classify("Buenos días con barra", setOf(1L)))
        assertEquals(ExerciseComplexity.AVANZADO, classify("Swing con kettlebell", setOf(10L)))
    }

    @Test
    fun las_tildes_no_impiden_reconocer_la_palabra_clave() {
        assertEquals(ExerciseComplexity.AVANZADO, classify("Buenos días"))
        assertEquals(ExerciseComplexity.INTERMEDIO, classify("Zancada caminando con mancuernas", setOf(3L)))
    }

    @Test
    fun cualquier_cosa_con_barra_es_al_menos_intermedia() {
        assertEquals(ExerciseComplexity.INTERMEDIO, classify("Curl con barra Z", setOf(2L)))
        assertEquals(ExerciseComplexity.INTERMEDIO, classify("Press de banca", setOf(1L, 8L)))
    }

    @Test
    fun los_basicos_de_maquina_o_mancuerna_son_basicos() {
        assertEquals(ExerciseComplexity.BASICO, classify("Curl de bíceps con mancuernas", setOf(3L)))
        assertEquals(ExerciseComplexity.BASICO, classify("Plancha abdominal", setOf(4L)))
        assertEquals(ExerciseComplexity.BASICO, classify("Flexiones"))
        assertEquals(ExerciseComplexity.BASICO, classify("Elevaciones laterales", setOf(3L)))
    }

    @Test
    fun el_peso_muerto_y_las_dominadas_son_intermedios_aunque_no_lleven_barra() {
        assertEquals(ExerciseComplexity.INTERMEDIO, classify("Peso muerto con mancuernas", setOf(3L)))
        assertEquals(ExerciseComplexity.INTERMEDIO, classify("Fondos en paralelas"))
    }
}
