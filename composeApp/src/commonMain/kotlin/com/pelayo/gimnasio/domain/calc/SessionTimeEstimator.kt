package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.TrainingPrescription

/**
 * Traduce tiempo disponible en número de ejercicios, y al revés.
 *
 * El coste de un ejercicio es el de sus series más los descansos que hay entre
 * ellas (no el de después de la última, que se solapa con montar el siguiente)
 * más un margen fijo para cambiar de sitio y ajustar la máquina. Las series de
 * tiempo se cuentan igual que las de repeticiones: la diferencia queda dentro
 * del error de una estimación así.
 */
object SessionTimeEstimator {

    /** Calentamiento y movilidad antes de la primera serie. */
    const val WARMUP_MINUTES = 8

    /** Cambiar de ejercicio: desplazarse, ajustar la máquina y colocar los discos. */
    const val TRANSITION_SECONDS = 90

    /** Duración media de una serie de trabajo. */
    const val SECONDS_PER_SET = 40

    fun secondsPerExercise(prescription: TrainingPrescription): Int =
        prescription.sets * SECONDS_PER_SET +
            (prescription.sets - 1).coerceAtLeast(0) * prescription.restSeconds +
            TRANSITION_SECONDS

    /**
     * Cuántos ejercicios caben en [minutes]. Redondea al más cercano en vez de
     * truncar: pasarse tres minutos es mejor que dejar el entreno cojo, y luego
     * se enseña la duración estimada real.
     */
    fun exercisesThatFit(minutes: Int, prescription: TrainingPrescription, allowed: IntRange): Int {
        val trainingSeconds = (minutes - WARMUP_MINUTES).coerceAtLeast(0) * 60
        val perExercise = secondsPerExercise(prescription)
        val fit = (trainingSeconds + perExercise / 2) / perExercise
        return fit.coerceIn(allowed)
    }

    /** Duración estimada de una sesión de [exercises] ejercicios, en minutos. */
    fun minutesFor(exercises: Int, prescription: TrainingPrescription): Int {
        val seconds = exercises * secondsPerExercise(prescription)
        return WARMUP_MINUTES + (seconds + 30) / 60
    }
}
