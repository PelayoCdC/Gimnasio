package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.util.containsWordStarting
import com.pelayo.gimnasio.util.normalizeForSearch

/**
 * Deduce cómo se registra un ejercicio: por tiempo o por repeticiones, y si tiene
 * sentido apuntarle un peso.
 *
 * wger solo distingue el cardio, así que una plancha llegaba como ejercicio de
 * repeticiones y pedía kilos y reps para algo que se mide en segundos. Y de un
 * crunch o unas elevaciones de piernas tampoco hay peso que apuntar: el campo
 * sobra y encima estorba.
 */
object ExerciseFormatClassifier {

    /** Categoría "Cardio" de wger, que siempre va por tiempo. */
    const val CARDIO_CATEGORY_ID = 15L

    // Ids de equipamiento de wger, los mismos que siembra WgerMappings.
    private const val ESTERILLA = 4L
    private const val FITBALL = 5L
    private const val BARRA_DOMINADAS = 6L
    private const val SIN_MATERIAL = 7L

    /** Material que no añade carga: quien lo usa se está moviendo a su peso. */
    private val bodyweightEquipment = setOf(ESTERILLA, FITBALL, BARRA_DOMINADAS, SIN_MATERIAL)

    /** Isométricos y todo lo que se aguanta o se recorre, no lo que se repite. */
    private val timeKeywords = listOf(
        "plancha", "plank", "isometric", "isometrico", "hollow", "vacuum",
        "wall sit", "sentadilla isometrica", "silla contra la pared",
        "colgarse", "dead hang", "suspension en barra",
        "paseo del granjero", "farmer", "carry", "transporte",
        "mantener", "sostener", "aguantar", "estatico",
        "puente lateral", "side plank", "superman",
    )

    /**
     * Ejercicios de peso corporal por nombre, para los que el material no dice
     * nada útil (wger deja muchos con "sin material" y otros sin nada).
     *
     * "flexiones" y "flexion de brazos", no "flexion" a secas: una "flexión de
     * piernas" es un curl femoral y una "flexión de muñeca con barra" lleva barra.
     */
    private val bodyweightKeywords = listOf(
        "crunch", "abdominal", "encogimiento abdominal", "elevacion de piernas",
        "elevaciones de piernas", "tijeras", "bicicleta abdominal", "russian twist",
        "giro ruso", "mountain climber", "escalador", "burpee", "flexiones",
        "flexion de brazos", "push up", "push-up", "sentadilla al aire", "air squat",
        "puente de gluteos", "superman", "plancha", "plank", "dominada", "pull up",
        "pull-up", "fondos", "dips", "zancada sin peso",
    )

    /**
     * Material con carga mencionado en el nombre. Manda sobre las palabras de peso
     * corporal: "abdominales en máquina" o "crunch en polea" llevan kilos que
     * apuntar por mucho crunch que digan.
     */
    private val loadedKeywords = listOf(
        "maquina", "polea", "cable", "mancuerna", "mancuernas", "kettlebell",
        "pesa rusa", "con barra", "con disco", "con lastre", "lastrada", "lastradas",
        "lastrado", "lastrados", "smith", "multipower", "machine", "dumbbell",
        "barbell", "weighted",
    )

    fun isTimeBased(name: String, categoryId: Long?): Boolean {
        if (categoryId == CARDIO_CATEGORY_ID) return true
        val normalized = normalizeForSearch(name)
        return timeKeywords.any { normalized.containsWordStarting(it) }
    }

    /**
     * true cuando no hay peso que apuntar. No es una prohibición: la pantalla deja
     * añadirlo a mano, porque un fondo o una dominada se pueden lastrar.
     */
    fun isBodyweight(name: String, equipmentIds: Set<Long>): Boolean {
        val normalized = normalizeForSearch(name)
        if (loadedKeywords.any { normalized.containsWordStarting(it) }) return false
        if (bodyweightKeywords.any { normalized.containsWordStarting(it) }) return true
        // Sin equipamiento **no** significa peso corporal: media catálogo de wger
        // viene sin material, incluido el press de banca. Solo cuenta cuando dice
        // explícitamente que se usa algo que no carga.
        return equipmentIds.isNotEmpty() && bodyweightEquipment.containsAll(equipmentIds)
    }
}
