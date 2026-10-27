package com.pelayo.gimnasio.domain.calc

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreakCalculatorTest {

    // 2026-07-25 es sábado; su semana empieza el lunes 2026-07-20.
    private val saturday = LocalDate(2026, 7, 25)

    private fun d(day: Int) = LocalDate(2026, 7, day)

    @Test
    fun sin_entrenamientos_no_hay_racha() {
        val info = StreakCalculator.calculate(emptyList(), saturday, weeklyTarget = 3)
        assertEquals(0, info.weekStreak)
        assertEquals(0, info.sessionsThisWeek)
        assertNull(info.daysSinceLastWorkout)
        assertFalse(info.weekCompleted)
    }

    @Test
    fun cuenta_los_entrenos_de_la_semana_en_curso() {
        // Lunes 20, miércoles 22 y viernes 24 caen en la semana de hoy.
        val info = StreakCalculator.calculate(listOf(d(20), d(22), d(24)), saturday, weeklyTarget = 3)
        assertEquals(3, info.sessionsThisWeek)
        assertTrue(info.weekCompleted)
        assertEquals(1, info.weekStreak, "la semana en curso ya cumplida cuenta")
    }

    @Test
    fun la_semana_a_medias_no_rompe_la_racha_anterior() {
        // Dos semanas completas antes, y esta a medias: la racha se mantiene en 2.
        val dates = listOf(
            d(6), d(8), d(10), // semana del 6 al 12
            d(13), d(15), d(17), // semana del 13 al 19
            d(20), // esta semana, incompleta
        )
        val info = StreakCalculator.calculate(dates, saturday, weeklyTarget = 3)
        assertEquals(2, info.weekStreak)
        assertEquals(1, info.sessionsThisWeek)
        assertFalse(info.weekCompleted)
    }

    @Test
    fun completar_la_semana_en_curso_suma_a_la_racha() {
        val dates = listOf(
            d(6), d(8), d(10),
            d(13), d(15), d(17),
            d(20), d(22), d(24),
        )
        assertEquals(3, StreakCalculator.calculate(dates, saturday, weeklyTarget = 3).weekStreak)
    }

    @Test
    fun una_semana_fallada_corta_la_racha() {
        val dates = listOf(
            d(6), d(8), d(10), // completa
            d(13), // semana fallada
            d(20), d(22), d(24), // esta, completa
        )
        // Solo cuenta la actual: la anterior rompe la cadena.
        assertEquals(1, StreakCalculator.calculate(dates, saturday, weeklyTarget = 3).weekStreak)
    }

    @Test
    fun los_dias_repetidos_cuentan_una_sola_vez() {
        val info = StreakCalculator.calculate(listOf(d(20), d(20), d(22)), saturday, weeklyTarget = 3)
        assertEquals(2, info.sessionsThisWeek, "dos sesiones el mismo día son un día entrenado")
        assertEquals(3, info.totalSessions, "el total sí cuenta cada sesión")
    }

    @Test
    fun calcula_los_dias_desde_el_ultimo_entreno() {
        assertEquals(1, StreakCalculator.calculate(listOf(d(24)), saturday, 3).daysSinceLastWorkout)
        assertEquals(0, StreakCalculator.calculate(listOf(d(25)), saturday, 3).daysSinceLastWorkout)
        assertEquals(19, StreakCalculator.calculate(listOf(d(6)), saturday, 3).daysSinceLastWorkout)
    }

    @Test
    fun sin_rutina_activa_no_se_calcula_racha() {
        val info = StreakCalculator.calculate(listOf(d(20), d(22)), saturday, weeklyTarget = 0)
        assertEquals(0, info.weekStreak)
        assertFalse(info.weekCompleted)
        assertEquals(2, info.sessionsThisWeek, "los entrenos sí se cuentan")
    }

    @Test
    fun los_logros_se_desbloquean_por_numero_de_sesiones() {
        val info = StreakInfo(
            weekStreak = 0, sessionsThisWeek = 0, weeklyTarget = 3,
            daysSinceLastWorkout = 1, totalSessions = 10,
        )
        val unlocked = StreakCalculator.achievements(info).filter { it.unlocked }.map { it.id }
        assertEquals(listOf("first", "ten"), unlocked)
    }

    @Test
    fun los_logros_de_constancia_dependen_de_la_racha() {
        val info = StreakInfo(
            weekStreak = 4, sessionsThisWeek = 3, weeklyTarget = 3,
            daysSinceLastWorkout = 0, totalSessions = 12,
        )
        val unlocked = StreakCalculator.achievements(info).filter { it.unlocked }.map { it.id }
        assertTrue("month" in unlocked)
        assertTrue("quarter" !in unlocked)
    }

    @Test
    fun sin_nada_hecho_ningun_logro_esta_desbloqueado() {
        val info = StreakInfo(0, 0, 3, null, 0)
        assertTrue(StreakCalculator.achievements(info).none { it.unlocked })
    }

    @Test
    fun el_total_de_sesiones_llega_aparte_de_las_fechas() {
        // Las fechas vienen deduplicadas (DISTINCT): dos sesiones el mismo día
        // hacían que los logros fueran por detrás del contador de la portada.
        val info = StreakCalculator.calculate(
            sessionDates = listOf(saturday),
            today = saturday,
            weeklyTarget = 3,
            totalSessions = 2,
        )
        assertEquals(2, info.totalSessions)
    }

    @Test
    fun una_fecha_futura_no_da_dias_negativos() {
        val info = StreakCalculator.calculate(
            sessionDates = listOf(saturday.plus(kotlinx.datetime.DatePeriod(days = 3))),
            today = saturday,
            weeklyTarget = 3,
        )
        assertEquals(0, info.daysSinceLastWorkout)
    }
}
