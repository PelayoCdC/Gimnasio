package com.pelayo.gimnasio.domain.calc

import kotlin.math.abs
import kotlin.math.round

/** Resumen de lo que se hizo en un ejercicio dentro de una sesión pasada. */
data class PastPerformance(
    val weightKg: Double?,
    val reps: List<Int>,
)

data class ProgressionSuggestion(
    val newWeightKg: Double,
    val previousWeightKg: Double,
    val reason: String,
    /** true si la sugerencia es bajar peso por estancamiento, no subirlo. */
    val isDeload: Boolean = false,
) {
    val incrementKg: Double get() = newWeightKg - previousWeightKg
}

/** El equivalente para ejercicios que se miden en segundos (plancha, colgarse…). */
data class TimeProgressionSuggestion(
    val newTimeSeconds: Int,
    val previousTargetSeconds: Int,
    val reason: String,
)

/**
 * Sugiere subir peso cuando el objetivo se ha cumplido con holgura, y bajarlo
 * cuando el ejercicio lleva varias sesiones estancado.
 *
 * Subida (doble progresión, la habitual en rutinas de hipertrofia): si en las dos
 * últimas sesiones se completaron todas las series objetivo alcanzando el tope
 * del rango de repeticiones, y siempre con el mismo peso, toca subir.
 *
 * Bajada (deload): si en las tres últimas sesiones se usó el mismo peso y en
 * ninguna se llegó al tope, insistir no va a arreglarlo; se propone bajar en
 * torno a un 10 % y volver a subir con margen.
 *
 * El incremento depende del tipo de ejercicio: los compuestos con barra admiten
 * saltos mayores que los aislamientos con mancuerna.
 */
object ProgressionAdvisor {

    const val COMPOUND_INCREMENT_KG = 5.0
    const val ISOLATION_INCREMENT_KG = 2.5
    const val SESSIONS_REQUIRED = 2

    /** Sesiones seguidas sin llegar al objetivo que se consideran estancamiento. */
    const val STALL_SESSIONS = 3
    const val DELOAD_FACTOR = 0.9

    /** Cuánto se propone subir un objetivo de tiempo cumplido con holgura. */
    const val TIME_INCREMENT_SECONDS = 15

    fun suggest(
        recentSessions: List<PastPerformance>,
        targetSets: Int,
        targetRepsMax: Int?,
        isCompound: Boolean,
    ): ProgressionSuggestion? {
        if (targetRepsMax == null || targetSets <= 0) return null
        upgrade(recentSessions, targetSets, targetRepsMax, isCompound)?.let { return it }
        return deload(recentSessions, targetSets, targetRepsMax)
    }

    private fun upgrade(
        recentSessions: List<PastPerformance>,
        targetSets: Int,
        targetRepsMax: Int,
        isCompound: Boolean,
    ): ProgressionSuggestion? {
        // Solo se miran las dos últimas, y hacen falta al menos esas dos.
        if (recentSessions.size < SESSIONS_REQUIRED) return null
        val considered = recentSessions.take(SESSIONS_REQUIRED)

        val weight = considered.first().weightKg ?: return null
        if (weight <= 0.0) return null

        val allAtTop = considered.all { session ->
            session.weightKg.approx(weight) && sessionAtTop(session, targetSets, targetRepsMax)
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

    private fun deload(
        recentSessions: List<PastPerformance>,
        targetSets: Int,
        targetRepsMax: Int,
    ): ProgressionSuggestion? {
        if (recentSessions.size < STALL_SESSIONS) return null
        val considered = recentSessions.take(STALL_SESSIONS)

        val weight = considered.first().weightKg ?: return null
        if (weight <= 0.0) return null

        val stalled = considered.all { session ->
            session.weightKg.approx(weight) &&
                session.reps.isNotEmpty() &&
                !sessionAtTop(session, targetSets, targetRepsMax)
        }
        if (!stalled) return null

        // Al múltiplo de 2,5 más cercano, que es lo que hay en discos y máquinas.
        val lowered = (round(weight * DELOAD_FACTOR / 2.5) * 2.5)
            .let { if (it >= weight) weight - 2.5 else it }
        if (lowered <= 0.0) return null

        return ProgressionSuggestion(
            newWeightKg = lowered,
            previousWeightKg = weight,
            reason = "Llevas $STALL_SESSIONS sesiones con ${weight.trim()} kg sin llegar a " +
                "${targetSets}×$targetRepsMax: baja a ${lowered.trim()} kg y vuelve a subir con margen.",
            isDeload = true,
        )
    }

    /**
     * Doble progresión para ejercicios por tiempo: si en las dos últimas sesiones
     * todas las series aguantaron el objetivo, se propone subirlo. Los isométricos
     * no tenían ningún consejo y la plancha se quedaba en 60 s para siempre.
     *
     * [recentSessions] son los segundos de cada serie, por sesión y de la más
     * reciente a la más antigua.
     */
    fun suggestTime(
        recentSessions: List<List<Int>>,
        targetSets: Int,
        targetTimeSeconds: Int?,
    ): TimeProgressionSuggestion? {
        if (targetTimeSeconds == null || targetTimeSeconds <= 0 || targetSets <= 0) return null
        if (recentSessions.size < SESSIONS_REQUIRED) return null

        val allAtTarget = recentSessions.take(SESSIONS_REQUIRED).all { session ->
            session.size >= targetSets && session.take(targetSets).all { it >= targetTimeSeconds }
        }
        if (!allAtTarget) return null

        return TimeProgressionSuggestion(
            newTimeSeconds = targetTimeSeconds + TIME_INCREMENT_SECONDS,
            previousTargetSeconds = targetTimeSeconds,
            reason = "Has aguantado ${targetSets}×${targetTimeSeconds} s en las últimas " +
                "$SESSIONS_REQUIRED sesiones.",
        )
    }

    private fun sessionAtTop(session: PastPerformance, targetSets: Int, targetRepsMax: Int): Boolean =
        session.reps.size >= targetSets && session.reps.take(targetSets).all { it >= targetRepsMax }

    /** Igualdad con tolerancia: dos pesos de la báscula de discos no se comparan con ==. */
    private fun Double?.approx(other: Double): Boolean = this != null && abs(this - other) < 0.01

    private fun Double.trim(): String =
        if (this == toLong().toDouble()) toLong().toString() else (round(this * 10) / 10.0).toString()
}
