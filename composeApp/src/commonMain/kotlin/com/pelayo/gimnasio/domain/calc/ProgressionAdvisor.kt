package com.pelayo.gimnasio.domain.calc

/** Resumen de lo que se hizo en un ejercicio dentro de una sesión pasada. */
data class PastPerformance(
    val weightKg: Double?,
    val reps: List<Int>,
)

data class ProgressionSuggestion(
    val newWeightKg: Double,
    val previousWeightKg: Double,
    val reason: String,
) {
    val incrementKg: Double get() = newWeightKg - previousWeightKg
}

/**
 * Sugiere subir peso cuando el objetivo se ha cumplido con holgura.
 *
 * Regla (doble progresión, la habitual en rutinas de hipertrofia): si en las dos
 * últimas sesiones se completaron todas las series objetivo alcanzando el tope
 * del rango de repeticiones, y siempre con el mismo peso, toca subir.
 *
 * El incremento depende del tipo de ejercicio: los compuestos con barra admiten
 * saltos mayores que los aislamientos con mancuerna.
 */
object ProgressionAdvisor {

    const val COMPOUND_INCREMENT_KG = 5.0
    const val ISOLATION_INCREMENT_KG = 2.5
    const val SESSIONS_REQUIRED = 2

    fun suggest(
        recentSessions: List<PastPerformance>,
        targetSets: Int,
        targetRepsMax: Int?,
        isCompound: Boolean,
    ): ProgressionSuggestion? {
        if (targetRepsMax == null || targetSets <= 0) return null
        // Solo se miran las dos últimas, y hacen falta al menos esas dos.
        if (recentSessions.size < SESSIONS_REQUIRED) return null
        val considered = recentSessions.take(SESSIONS_REQUIRED)

        val weight = considered.first().weightKg ?: return null
        if (weight <= 0.0) return null

        val allAtTop = considered.all { session ->
            session.weightKg == weight &&
                session.reps.size >= targetSets &&
                session.reps.take(targetSets).all { it >= targetRepsMax }
        }
        if (!allAtTop) return null

        val increment = if (isCompound) COMPOUND_INCREMENT_KG else ISOLATION_INCREMENT_KG
        return ProgressionSuggestion(
            newWeightKg = weight + increment,
            previousWeightKg = weight,
            reason = "Has completado ${targetSets}×$targetRepsMax con ${weight.trim()} kg " +
                "en las últimas $SESSIONS_REQUIRED sesiones.",
        )
    }

    private fun Double.trim(): String =
        if (this == toLong().toDouble()) toLong().toString() else ((this * 10).toLong() / 10.0).toString()
}
