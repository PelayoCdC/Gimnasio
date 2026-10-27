package com.pelayo.gimnasio.domain.calc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionAdvisorTest {

    private fun session(weight: Double?, vararg reps: Int) = PastPerformance(weight, reps.toList())

    @Test
    fun dos_sesiones_al_tope_del_rango_sugieren_subir() {
        val suggestion = assertNotNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(
                    session(60.0, 12, 12, 12),
                    session(60.0, 12, 12, 12),
                ),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = true,
            ),
        )
        assertEquals(65.0, suggestion.newWeightKg)
        assertEquals(60.0, suggestion.previousWeightKg)
        assertEquals(5.0, suggestion.incrementKg)
        assertTrue(suggestion.reason.contains("3×12"))
    }

    @Test
    fun una_sola_sesion_no_basta() {
        assertNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(session(60.0, 12, 12, 12)),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = true,
            ),
        )
    }

    @Test
    fun si_una_serie_se_queda_corta_no_sugiere() {
        assertNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(
                    session(60.0, 12, 12, 10),
                    session(60.0, 12, 12, 12),
                ),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = true,
            ),
        )
    }

    @Test
    fun si_faltan_series_no_sugiere() {
        assertNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(
                    session(60.0, 12, 12),
                    session(60.0, 12, 12, 12),
                ),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = true,
            ),
        )
    }

    @Test
    fun si_el_peso_cambio_entre_sesiones_no_sugiere() {
        // Ya subió por su cuenta; hay que consolidar antes de volver a subir.
        assertNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(
                    session(62.5, 12, 12, 12),
                    session(60.0, 12, 12, 12),
                ),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = true,
            ),
        )
    }

    @Test
    fun los_aislamientos_suben_de_dos_y_medio() {
        val suggestion = assertNotNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(
                    session(12.5, 12, 12, 12),
                    session(12.5, 12, 12, 12),
                ),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = false,
            ),
        )
        assertEquals(15.0, suggestion.newWeightKg)
        assertEquals(2.5, suggestion.incrementKg)
    }

    @Test
    fun superar_el_tope_tambien_cuenta() {
        val suggestion = ProgressionAdvisor.suggest(
            recentSessions = listOf(
                session(60.0, 14, 13, 12),
                session(60.0, 12, 12, 13),
            ),
            targetSets = 3,
            targetRepsMax = 12,
            isCompound = true,
        )
        assertNotNull(suggestion)
    }

    @Test
    fun las_series_de_mas_no_estorban() {
        // Cuatro series registradas con objetivo de tres: solo cuentan las tres primeras.
        val suggestion = ProgressionAdvisor.suggest(
            recentSessions = listOf(
                session(60.0, 12, 12, 12, 8),
                session(60.0, 12, 12, 12, 9),
            ),
            targetSets = 3,
            targetRepsMax = 12,
            isCompound = true,
        )
        assertNotNull(suggestion)
    }

    @Test
    fun sin_peso_registrado_no_hay_sugerencia() {
        assertNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(session(null, 12, 12, 12), session(null, 12, 12, 12)),
                targetSets = 3,
                targetRepsMax = 12,
                isCompound = true,
            ),
        )
    }

    @Test
    fun un_ejercicio_sin_rango_de_repeticiones_se_ignora() {
        // Los de tiempo (plancha, cardio) no entran en la doble progresión.
        assertNull(
            ProgressionAdvisor.suggest(
                recentSessions = listOf(session(0.0), session(0.0)),
                targetSets = 3,
                targetRepsMax = null,
                isCompound = false,
            ),
        )
    }

    @Test
    fun solo_se_miran_las_dos_ultimas_sesiones() {
        // La tercera, peor, es historia antigua y no debe bloquear la subida.
        val suggestion = ProgressionAdvisor.suggest(
            recentSessions = listOf(
                session(60.0, 12, 12, 12),
                session(60.0, 12, 12, 12),
                session(55.0, 8, 8, 8),
            ),
            targetSets = 3,
            targetRepsMax = 12,
            isCompound = true,
        )
        assertNotNull(suggestion)
    }

    @Test
    fun tres_sesiones_estancadas_con_el_mismo_peso_proponen_deload() {
        val suggestion = ProgressionAdvisor.suggest(
            recentSessions = listOf(
                session(100.0, 8, 7, 6),
                session(100.0, 8, 8, 7),
                session(100.0, 9, 8, 7),
            ),
            targetSets = 3,
            targetRepsMax = 12,
            isCompound = true,
        )
        assertNotNull(suggestion)
        assertTrue(suggestion.isDeload)
        assertTrue(suggestion.newWeightKg < 100.0, "el deload baja el peso")
        // Al múltiplo de 2,5 más cercano: 90 kg.
        assertEquals(90.0, suggestion.newWeightKg)
    }

    @Test
    fun dos_sesiones_flojas_no_bastan_para_el_deload() {
        val suggestion = ProgressionAdvisor.suggest(
            recentSessions = listOf(
                session(100.0, 8, 7, 6),
                session(100.0, 8, 8, 7),
            ),
            targetSets = 3,
            targetRepsMax = 12,
            isCompound = true,
        )
        assertNull(suggestion)
    }

    @Test
    fun si_una_de_las_tres_llego_al_tope_no_hay_deload() {
        val suggestion = ProgressionAdvisor.suggest(
            recentSessions = listOf(
                session(100.0, 8, 7, 6),
                session(100.0, 12, 12, 12),
                session(100.0, 9, 8, 7),
            ),
            targetSets = 3,
            targetRepsMax = 12,
            isCompound = true,
        )
        assertNull(suggestion)
    }

    @Test
    fun aguantar_el_objetivo_de_tiempo_dos_sesiones_propone_subirlo() {
        val suggestion = ProgressionAdvisor.suggestTime(
            recentSessions = listOf(listOf(60, 62, 60), listOf(61, 60, 60)),
            targetSets = 3,
            targetTimeSeconds = 60,
        )
        assertNotNull(suggestion)
        assertEquals(75, suggestion.newTimeSeconds)
        assertEquals(60, suggestion.previousTargetSeconds)
    }

    @Test
    fun sin_llegar_al_tiempo_objetivo_no_se_sube() {
        assertNull(
            ProgressionAdvisor.suggestTime(
                recentSessions = listOf(listOf(60, 55, 50), listOf(60, 60, 60)),
                targetSets = 3,
                targetTimeSeconds = 60,
            ),
        )
        // Y con una sola sesión tampoco.
        assertNull(
            ProgressionAdvisor.suggestTime(
                recentSessions = listOf(listOf(60, 60, 60)),
                targetSets = 3,
                targetTimeSeconds = 60,
            ),
        )
    }
}
