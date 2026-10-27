package com.pelayo.gimnasio.domain.calc

/**
 * 1RM estimado con la fórmula de Epley: peso × (1 + reps/30).
 *
 * Da una curva de fuerza comparable entre sesiones: la gráfica de "peso máximo"
 * a secas baja cuando se sube de repeticiones bajando el peso, aunque la fuerza
 * real haya mejorado. No sustituye a probar el 1RM de verdad; orienta.
 */
object OneRepMaxCalculator {

    /** Por encima de esto la fórmula ya no estima nada útil. */
    const val MAX_RELIABLE_REPS = 15

    fun estimate(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null) return null
        if (weightKg <= 0 || reps <= 0 || reps > MAX_RELIABLE_REPS) return null
        if (reps == 1) return weightKg
        return weightKg * (1 + reps / 30.0)
    }
}
