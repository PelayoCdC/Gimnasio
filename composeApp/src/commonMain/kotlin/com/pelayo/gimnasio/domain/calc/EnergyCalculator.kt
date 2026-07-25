package com.pelayo.gimnasio.domain.calc

/**
 * Estimación de gasto calórico con METs (Compendium of Physical Activities):
 * kcal = MET × peso(kg) × horas.
 *
 * Aproximación del tiempo activo de un ejercicio de fuerza: cada serie cuenta
 * su tiempo de ejecución (~40 s si es de repeticiones, o el tiempo objetivo si
 * es de duración) más la mitad del descanso, porque el metabolismo sigue
 * elevado entre series. El resto del descanso se ignora.
 */
object EnergyCalculator {

    const val DEFAULT_SECONDS_PER_REP_SET = 40.0

    fun kcalBurned(met: Double, weightKg: Double, hours: Double): Double {
        require(met > 0 && weightKg > 0 && hours >= 0)
        return met * weightKg * hours
    }

    /** Horas activas estimadas de un ejercicio dentro de una sesión. */
    fun estimateActiveHours(
        sets: Int,
        secondsPerSet: Double = DEFAULT_SECONDS_PER_REP_SET,
        restSeconds: Int = 90,
    ): Double {
        require(sets >= 0)
        return sets * (secondsPerSet + restSeconds * 0.5) / 3600.0
    }

    /** Kcal de un ejercicio: sets ejecutados con su MET y descanso configurado. */
    fun exerciseKcal(
        met: Double,
        weightKg: Double,
        sets: Int,
        secondsPerSet: Double = DEFAULT_SECONDS_PER_REP_SET,
        restSeconds: Int = 90,
    ): Double = kcalBurned(met, weightKg, estimateActiveHours(sets, secondsPerSet, restSeconds))
}
