package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GymExerciseFilterTest {

    @Test
    fun la_categoria_cardio_de_wger_se_descarta_entera() {
        assertFalse(
            GymExerciseFilter.isGymExercise("Lo que sea", GymExerciseFilter.CARDIO_CATEGORY_ID),
        )
    }

    @Test
    fun nadar_correr_y_demas_se_descartan_aunque_no_vengan_como_cardio() {
        // wger cataloga natación con el dorsal como músculo primario, así que sin
        // este filtro acababa entre las series de espalda.
        listOf(
            "Nadar a crol",
            "Natación estilo espalda",
            "Correr en cinta",
            "Bicicleta estática",
            "Elíptica",
            "Saltar a la comba",
            "Escalada en rocódromo",
            "Estiramiento de isquiotibiales",
        ).forEach {
            assertFalse(GymExerciseFilter.isGymExercise(it, categoryId = 12L), "no debería colar: $it")
        }
    }

    @Test
    fun los_ejercicios_de_sala_pasan() {
        listOf(
            "Press de banca con barra",
            "Jalón al pecho",
            "Remo con mancuerna",
            "Prensa de piernas",
            "Escalador", // no es escalada: es un ejercicio de core
            "Elevación de gemelos de pie",
        ).forEach {
            assertTrue(GymExerciseFilter.isGymExercise(it, categoryId = 9L), "debería pasar: $it")
        }
    }

    @Test
    fun un_ejercicio_propio_sin_categoria_se_acepta() {
        assertTrue(GymExerciseFilter.isGymExercise("Mi invento", categoryId = null))
    }

    @Test
    fun las_palabras_clave_van_en_limite_de_palabra() {
        // "andar" vivía dentro de "estándar" y descartaba ejercicios de sala.
        assertTrue(GymExerciseFilter.isGymExercise("Press estándar", categoryId = 9L))
    }
}
