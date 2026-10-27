package com.pelayo.gimnasio.data.db

import com.pelayo.gimnasio.domain.calc.ExerciseFormatClassifier
import com.pelayo.gimnasio.domain.model.MuscleGroups

/**
 * Mapeos estáticos de los catálogos de wger (ids estables de su API pública)
 * a nuestro modelo: músculos → grupo muscular, nombres en español y METs por categoría.
 */
object WgerMappings {

    /** id de músculo wger → (nombre ES, es frontal, grupo muscular nuestro). */
    data class MuscleSeed(val nameEs: String, val isFront: Boolean, val groupId: Long)

    val muscles: Map<Long, MuscleSeed> = mapOf(
        1L to MuscleSeed("Bíceps braquial", true, MuscleGroups.BICEPS.id),
        2L to MuscleSeed("Deltoides anterior", true, MuscleGroups.HOMBROS.id),
        3L to MuscleSeed("Serrato anterior", true, MuscleGroups.PECHO.id),
        4L to MuscleSeed("Pectoral mayor", true, MuscleGroups.PECHO.id),
        5L to MuscleSeed("Tríceps braquial", false, MuscleGroups.TRICEPS.id),
        6L to MuscleSeed("Recto abdominal", true, MuscleGroups.CORE.id),
        7L to MuscleSeed("Gastrocnemio (gemelo)", false, MuscleGroups.GEMELOS.id),
        8L to MuscleSeed("Glúteo mayor", false, MuscleGroups.GLUTEOS.id),
        9L to MuscleSeed("Trapecio", false, MuscleGroups.ESPALDA.id),
        10L to MuscleSeed("Cuádriceps", true, MuscleGroups.CUADRICEPS.id),
        11L to MuscleSeed("Isquiotibiales", false, MuscleGroups.ISQUIOS.id),
        12L to MuscleSeed("Dorsal ancho", false, MuscleGroups.ESPALDA.id),
        13L to MuscleSeed("Braquial", true, MuscleGroups.BICEPS.id),
        14L to MuscleSeed("Oblicuos", true, MuscleGroups.CORE.id),
        15L to MuscleSeed("Sóleo", false, MuscleGroups.GEMELOS.id),
    )

    /** Traducciones de equipamiento wger conocido; lo desconocido conserva su nombre de API. */
    val equipmentNamesEs: Map<Long, String> = mapOf(
        1L to "Barra",
        2L to "Barra Z",
        3L to "Mancuernas",
        4L to "Esterilla",
        5L to "Fitball",
        6L to "Barra de dominadas",
        7L to "Sin material",
        8L to "Banco",
        9L to "Banco inclinado",
        10L to "Kettlebell",
    )

    /**
     * MET aproximado por categoría de ejercicio de wger
     * (8 Brazos, 9 Piernas, 10 Abdominales, 11 Pecho, 12 Espalda, 13 Hombros, 14 Gemelos, 15 Cardio).
     */
    fun metForCategory(categoryId: Long?): Double = when (categoryId) {
        15L -> 7.0 // cardio
        9L -> 5.0  // piernas: compuestos grandes
        10L -> 2.8 // abdominales/core
        else -> 3.5 // fuerza general intensidad moderada
    }

    /**
     * Cómo se registra el ejercicio. El cardio va por tiempo, pero también los
     * isométricos: wger no los distingue de nada y la plancha llegaba pidiendo
     * kilos y repeticiones. Lo decide [ExerciseFormatClassifier] por el nombre.
     */
    fun exerciseTypeFor(name: String, categoryId: Long?): String =
        if (ExerciseFormatClassifier.isTimeBased(name, categoryId)) "TIME" else "REPS"
}
