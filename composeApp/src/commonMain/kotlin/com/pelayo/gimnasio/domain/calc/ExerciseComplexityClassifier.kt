package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseComplexity
import com.pelayo.gimnasio.util.containsWordStarting
import com.pelayo.gimnasio.util.normalizeForSearch

/**
 * Estima lo exigente que es un ejercicio en técnica y, sobre todo, lo fácil que
 * es hacerse daño con él sin experiencia.
 *
 * El catálogo de wger no trae ningún campo de dificultad, así que se deduce del
 * nombre y del material: un movimiento con barra libre pide más control que el
 * mismo patrón en máquina, y los derivados del halterofilia o los que cargan la
 * columna en flexión están fuera del alcance de quien empieza.
 *
 * Ante la duda se clasifica como básico: el filtro por nivel se combina siempre
 * con la preferencia por lo más sencillo, así que un falso negativo suelto no
 * arruina la propuesta, pero un catálogo vacío sí.
 */
object ExerciseComplexityClassifier {

    // Ids de equipamiento de wger, los mismos que siembra WgerMappings.
    private const val BARRA = 1L
    private const val BARRA_Z = 2L
    private const val BARRA_DOMINADAS = 6L
    private const val KETTLEBELL = 10L

    private val technicalEquipment = setOf(BARRA, BARRA_Z, BARRA_DOMINADAS, KETTLEBELL)

    /**
     * Movimientos balísticos, invertidos o que llevan la articulación a rangos
     * comprometidos. Se descartan por completo para principiantes.
     */
    private val advancedKeywords = listOf(
        // Halterofilia y derivados
        "arrancada", "snatch", "cargada", "clean", "envion", "jerk", "dos tiempos",
        "cargada de fuerza", "power clean",
        // Balísticos y pliométricos
        "swing", "salto", "jump", "pliometric", "plyo", "explosiv", "lanzamiento", "slam",
        // Calistenia avanzada e invertidos
        "muscle up", "kipping", "pino", "handstand", "anillas", "rings", "bandera",
        "pistol", "una sola pierna con salto",
        // Patrones con riesgo de columna o de hombro
        "buenos dias", "good morning", "tras nuca", "behind the neck", "detras de la nuca",
        "sissy", "sobre la cabeza con barra", "overhead squat", "sentadilla sobre la cabeza",
        "turco", "turkish",
    )

    /**
     * Los básicos de toda la vida: eficaces, pero con una técnica que hay que
     * aprender. Para principiantes se permiten solo si no hay nada más sencillo.
     */
    private val intermediateKeywords = listOf(
        "peso muerto", "deadlift", "hip thrust",
        "sentadilla con barra", "sentadilla frontal", "front squat", "back squat",
        "sentadilla bulgara", "bulgarian",
        "dominada", "pull up", "pull-up", "chin up", "chin-up", "jalon en barra",
        "fondos", "dips", "paralelas",
        "remo con barra", "remo inclinado", "bent over", "pendlay",
        "press militar", "press de pie", "overhead press", "press sobre la cabeza",
        "zancada", "lunge", "estocada", "subida al cajon", "step up",
        "a una pierna", "a un brazo", "unilateral", "single leg",
        "arrastre", "rueda abdominal", "ab wheel",
    )

    fun classify(name: String, equipmentIds: Set<Long>): ExerciseComplexity {
        val normalized = normalizeForSearch(name)

        // En límite de palabra, no como subcadena: "pino" aparecía dentro de
        // "supino" y cualquier curl con ese agarre quedaba vetado a principiantes.
        if (advancedKeywords.any { normalized.containsWordStarting(it) }) {
            return ExerciseComplexity.AVANZADO
        }
        if (equipmentIds.any { it in technicalEquipment }) return ExerciseComplexity.INTERMEDIO
        if (intermediateKeywords.any { normalized.containsWordStarting(it) }) {
            return ExerciseComplexity.INTERMEDIO
        }

        return ExerciseComplexity.BASICO
    }
}
