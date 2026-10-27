package com.pelayo.gimnasio.domain.calc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseFormatClassifierTest {

    @Test
    fun los_isometricos_se_miden_en_tiempo_aunque_wger_diga_repeticiones() {
        // wger clasifica la plancha en abdominales, no en cardio, y llegaba
        // pidiendo kilos y repeticiones para algo que se aguanta.
        listOf(
            "Plancha de antebrazo",
            "Plancha lateral",
            "Plank",
            "Sentadilla isométrica en la pared",
            "Paseo del granjero",
            "Colgarse de la barra",
        ).forEach {
            assertTrue(
                ExerciseFormatClassifier.isTimeBased(it, categoryId = 10L),
                "debería ir por tiempo: $it",
            )
        }
    }

    @Test
    fun el_cardio_sigue_yendo_por_tiempo() {
        assertTrue(
            ExerciseFormatClassifier.isTimeBased(
                "Lo que sea",
                ExerciseFormatClassifier.CARDIO_CATEGORY_ID,
            ),
        )
    }

    @Test
    fun lo_que_se_repite_no_se_cronometra() {
        listOf("Press de banca", "Curl de bíceps", "Crunch abdominal", "Sentadilla con barra")
            .forEach {
                assertFalse(
                    ExerciseFormatClassifier.isTimeBased(it, categoryId = 11L),
                    "no debería ir por tiempo: $it",
                )
            }
    }

    @Test
    fun a_los_de_peso_corporal_no_se_les_pide_peso() {
        listOf(
            "Crunch abdominal" to emptySet<Long>(),
            "Elevación de piernas colgado" to setOf(6L),
            "Flexiones" to setOf(7L),
            "Plancha" to setOf(4L),
            "Dominadas" to setOf(6L),
        ).forEach { (name, equipment) ->
            assertTrue(
                ExerciseFormatClassifier.isBodyweight(name, equipment),
                "debería ser de peso corporal: $name",
            )
        }
    }

    @Test
    fun lo_que_lleva_carga_sigue_pidiendo_kilos() {
        listOf(
            "Press de banca con barra" to setOf(1L, 8L),
            "Curl con mancuernas" to setOf(3L),
            "Swing con kettlebell" to setOf(10L),
        ).forEach { (name, equipment) ->
            assertFalse(
                ExerciseFormatClassifier.isBodyweight(name, equipment),
                "no debería ser de peso corporal: $name",
            )
        }
    }

    @Test
    fun sin_equipamiento_no_se_asume_peso_corporal() {
        // Medio catálogo de wger viene sin material, press de banca incluido:
        // dar por hecho que es peso corporal escondía el campo de kilos.
        assertFalse(ExerciseFormatClassifier.isBodyweight("Press de banca", emptySet()))
        assertFalse(ExerciseFormatClassifier.isBodyweight("Jalón al pecho", emptySet()))
    }

    @Test
    fun el_material_con_carga_del_nombre_manda_sobre_las_palabras_de_peso_corporal() {
        // "Abdominales en máquina" o "crunch en polea" llevan kilos que apuntar
        // por mucho crunch que digan.
        assertFalse(ExerciseFormatClassifier.isBodyweight("Abdominales en máquina", emptySet()))
        assertFalse(ExerciseFormatClassifier.isBodyweight("Crunch abdominal en polea", emptySet()))
        assertFalse(ExerciseFormatClassifier.isBodyweight("Flexiones de muñeca con barra", emptySet()))
    }

    @Test
    fun la_flexion_de_piernas_es_un_curl_femoral_no_una_flexion() {
        assertFalse(ExerciseFormatClassifier.isBodyweight("Flexión de piernas", emptySet()))
        // Las flexiones de toda la vida siguen siendo de peso corporal.
        assertTrue(ExerciseFormatClassifier.isBodyweight("Flexiones", emptySet()))
        assertTrue(ExerciseFormatClassifier.isBodyweight("Flexión de brazos", emptySet()))
    }
}
