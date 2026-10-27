package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Antes vivía dentro de GymExerciseFilterTest; el clasificador tiene entidad propia. */
class ExerciseStyleClassifierTest {

    private fun style(name: String, equipment: Set<Long> = emptySet()) =
        ExerciseStyleClassifier.classify(name, equipment)

    @Test
    fun el_nombre_delata_la_maquina_y_la_polea() {
        // wger no tiene equipamiento para máquina ni polea: solo queda el nombre.
        assertEquals(ExerciseStyle.POLEA, style("Jalón al pecho"))
        assertEquals(ExerciseStyle.POLEA, style("Cruces en polea"))
        assertEquals(ExerciseStyle.POLEA, style("Pushdown de tríceps"))
        assertEquals(ExerciseStyle.MAQUINA, style("Prensa de piernas"))
        assertEquals(ExerciseStyle.MAQUINA, style("Press de pecho en máquina"))
    }

    @Test
    fun sin_pistas_en_el_nombre_manda_el_material() {
        assertEquals(ExerciseStyle.MANCUERNA, style("Curl alterno", setOf(3L)))
        assertEquals(ExerciseStyle.BANCO, style("Press de banca", setOf(8L)))
        assertEquals(ExerciseStyle.BARRA, style("Sentadilla", setOf(1L)))
        assertEquals(ExerciseStyle.PESO_CORPORAL, style("Flexiones"))
        assertEquals(ExerciseStyle.PESO_CORPORAL, style("Dominadas", setOf(6L)))
    }

    @Test
    fun la_maquina_y_la_polea_van_por_delante_de_todo() {
        assertTrue(ExerciseStyle.MAQUINA.priority < ExerciseStyle.MANCUERNA.priority)
        assertTrue(ExerciseStyle.POLEA.priority < ExerciseStyle.MANCUERNA.priority)
        assertTrue(ExerciseStyle.MANCUERNA.priority < ExerciseStyle.BARRA.priority)
        assertTrue(ExerciseStyle.BANCO.priority < ExerciseStyle.BARRA.priority)
        assertTrue(ExerciseStyle.BARRA.priority < ExerciseStyle.PESO_CORPORAL.priority)
    }

    @Test
    fun una_mancuerna_con_nombre_de_maquina_cuenta_como_maquina() {
        // El nombre manda: "en máquina" describe mejor el ejercicio que su material.
        assertEquals(ExerciseStyle.MAQUINA, style("Curl en máquina", setOf(3L)))
    }

    @Test
    fun las_palabras_clave_van_en_limite_de_palabra() {
        // "hack" no debe dispararse dentro de otra palabra.
        assertEquals(ExerciseStyle.PESO_CORPORAL, style("Ejercicio machacado"))
    }
}
