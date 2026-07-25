package com.pelayo.gimnasio.domain.calc

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NextWorkoutPickerTest {

    // 2026-07-25 es sábado; 2026-07-27, lunes.
    private val saturday = LocalDate(2026, 7, 25)
    private val monday = LocalDate(2026, 7, 27)

    private fun day(id: Long, index: Int, name: String, weekday: Int? = null) =
        ScheduledDay(id, index, name, weekday)

    @Test
    fun sin_dias_no_hay_rutina() {
        assertIs<NextWorkout.NoRoutine>(NextWorkoutPicker.pick(emptyList(), saturday, null))
    }

    @Test
    fun con_dias_asignados_manda_el_dia_de_la_semana() {
        val days = listOf(
            day(1, 0, "Empuje", weekday = 1),
            day(2, 1, "Tirón", weekday = 3),
            day(3, 2, "Pierna", weekday = 5),
        )
        val result = NextWorkoutPicker.pick(days, monday, lastCompletedDayId = null)
        assertEquals("Empuje", assertIs<NextWorkout.Scheduled>(result).day.name)
    }

    @Test
    fun si_hoy_no_toca_avisa_de_descanso_y_del_siguiente() {
        val days = listOf(
            day(1, 0, "Empuje", weekday = 1),
            day(2, 1, "Tirón", weekday = 3),
        )
        // Sábado (6): no hay nada asignado, el siguiente es el lunes.
        val result = assertIs<NextWorkout.RestDay>(NextWorkoutPicker.pick(days, saturday, null))
        assertEquals("Empuje", result.nextDay?.name)
    }

    @Test
    fun el_siguiente_dia_asignado_puede_estar_mas_adelante_en_la_semana() {
        val days = listOf(
            day(1, 0, "Empuje", weekday = 3),
            day(2, 1, "Pierna", weekday = 5),
        )
        // Lunes (1): el siguiente asignado es el miércoles.
        val result = assertIs<NextWorkout.RestDay>(NextWorkoutPicker.pick(days, monday, null))
        assertEquals("Empuje", result.nextDay?.name)
    }

    @Test
    fun sin_dias_asignados_empieza_por_el_primero() {
        val days = listOf(day(1, 0, "A"), day(2, 1, "B"), day(3, 2, "C"))
        val result = NextWorkoutPicker.pick(days, saturday, lastCompletedDayId = null)
        assertEquals("A", assertIs<NextWorkout.NextInRotation>(result).day.name)
    }

    @Test
    fun sin_dias_asignados_rota_al_siguiente_del_ultimo_completado() {
        val days = listOf(day(1, 0, "A"), day(2, 1, "B"), day(3, 2, "C"))
        val result = NextWorkoutPicker.pick(days, saturday, lastCompletedDayId = 1)
        assertEquals("B", assertIs<NextWorkout.NextInRotation>(result).day.name)
    }

    @Test
    fun la_rotacion_vuelve_al_principio_tras_el_ultimo_dia() {
        val days = listOf(day(1, 0, "A"), day(2, 1, "B"), day(3, 2, "C"))
        val result = NextWorkoutPicker.pick(days, saturday, lastCompletedDayId = 3)
        assertEquals("A", assertIs<NextWorkout.NextInRotation>(result).day.name)
    }

    @Test
    fun un_dia_completado_que_ya_no_existe_reinicia_la_rotacion() {
        val days = listOf(day(1, 0, "A"), day(2, 1, "B"))
        val result = NextWorkoutPicker.pick(days, saturday, lastCompletedDayId = 99)
        assertEquals("A", assertIs<NextWorkout.NextInRotation>(result).day.name)
    }

    @Test
    fun con_un_solo_dia_asignado_y_hoy_libre_el_siguiente_es_ese_mismo() {
        val days = listOf(day(1, 0, "Full body", weekday = 3))
        val result = assertIs<NextWorkout.RestDay>(NextWorkoutPicker.pick(days, saturday, null))
        assertEquals("Full body", result.nextDay?.name)
    }

    @Test
    fun los_dias_desordenados_se_rotan_por_indice_no_por_id() {
        val days = listOf(day(30, 2, "C"), day(10, 0, "A"), day(20, 1, "B"))
        val result = NextWorkoutPicker.pick(days, saturday, lastCompletedDayId = 10)
        assertEquals("B", assertIs<NextWorkout.NextInRotation>(result).day.name)
    }
}
