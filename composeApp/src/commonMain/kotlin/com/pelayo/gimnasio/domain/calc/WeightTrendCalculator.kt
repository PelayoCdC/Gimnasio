package com.pelayo.gimnasio.domain.calc

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

/** Peso corporal suavizado y su ritmo de cambio. */
data class WeightTrend(
    /** Media de los pesajes de los últimos 7 días. */
    val averageKg: Double,
    /**
     * Cambio semanal estimado (media de esta semana menos la de la anterior);
     * null si no hay pesajes en la semana anterior con los que comparar.
     */
    val weeklyRateKg: Double?,
)

/**
 * Tendencia del peso corporal con media móvil de 7 días.
 *
 * Comparar las dos últimas mediciones a secas engaña dos veces: el peso diario
 * oscila más de medio kilo por agua y comida, y la "medición anterior" puede ser
 * de hace seis meses presentada como si fuera ayer.
 */
object WeightTrendCalculator {

    fun calculate(entries: List<Pair<LocalDate, Double>>, today: LocalDate): WeightTrend? {
        if (entries.isEmpty()) return null

        val weekStart = today.minus(DatePeriod(days = 6))
        val thisWeek = entries.filter { (date, _) -> date >= weekStart && date <= today }
        // Sin pesajes esta semana no hay tendencia actual que contar.
        if (thisWeek.isEmpty()) return null

        val previousStart = weekStart.minus(DatePeriod(days = 7))
        val previousWeek = entries.filter { (date, _) -> date >= previousStart && date < weekStart }

        val average = thisWeek.map { it.second }.average()
        val rate = previousWeek.takeIf { it.isNotEmpty() }
            ?.let { average - it.map { (_, kg) -> kg }.average() }

        return WeightTrend(averageKg = average, weeklyRateKg = rate)
    }

    /** No se proyecta más allá de esto: a un año vista ya no es una estimación. */
    const val MAX_PROJECTION_WEEKS = 52

    /** Por debajo de esto el ritmo es ruido de báscula, no una tendencia. */
    const val MIN_MEANINGFUL_RATE_KG = 0.05

    /**
     * Fecha estimada de llegada al peso objetivo al ritmo actual; null si no hay
     * objetivo, no hay ritmo, el ritmo aleja del objetivo o la fecha cae tan
     * lejos que la proyección no significa nada.
     */
    fun projectedDate(
        currentKg: Double,
        targetKg: Double?,
        weeklyRateKg: Double?,
        today: LocalDate,
    ): LocalDate? {
        if (targetKg == null || weeklyRateKg == null) return null
        if (abs(weeklyRateKg) < MIN_MEANINGFUL_RATE_KG) return null

        val remaining = targetKg - currentKg
        if (abs(remaining) < MIN_MEANINGFUL_RATE_KG) return null
        // El ritmo tiene que apuntar hacia el objetivo, no alejarse de él.
        if (remaining > 0 != weeklyRateKg > 0) return null

        val weeks = (remaining / weeklyRateKg)
        if (weeks > MAX_PROJECTION_WEEKS) return null
        return today.plus(DatePeriod(days = (weeks * 7).roundToInt()))
    }
}
