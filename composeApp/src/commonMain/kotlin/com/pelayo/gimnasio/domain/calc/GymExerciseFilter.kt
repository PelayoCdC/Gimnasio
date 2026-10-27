package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.util.containsWordStarting
import com.pelayo.gimnasio.util.normalizeForSearch

/**
 * Decide si un ejercicio del catálogo es de gimnasio.
 *
 * wger mezcla en su catálogo actividades que no pintan nada en una rutina de sala:
 * nadar, correr, la bicicleta o el remo ergómetro salen con dorsal o cuádriceps
 * como músculo primario, así que el generador los daba por buenos y aparecía
 * "Nadar a crol" entre las series de espalda.
 *
 * Se descartan por categoría (la 15 de wger es cardio) y por nombre, porque hay
 * actividades catalogadas fuera de cardio. El catálogo propio del usuario no pasa
 * por aquí: si alguien se crea "Nadar" a mano, es porque lo quiere.
 */
object GymExerciseFilter {

    /** Categoría "Cardio" de wger. */
    const val CARDIO_CATEGORY_ID = 15L

    private val nonGymKeywords = listOf(
        // Agua
        "nadar", "natacion", "crol", "braza", "espalda en piscina", "waterpolo",
        // Carrera y marcha
        "correr", "carrera", "running", "trotar", "jogging", "footing", "sprint",
        "caminar", "andar", "marcha atletica", "cinta de correr", "treadmill",
        // Ruedas y máquinas de cardio puro
        "bicicleta", "ciclismo", "spinning", "eliptica", "step de cardio",
        "remo ergometro", "remoergometro", "ergometro", "escaladora",
        // Deportes y actividades
        "futbol", "baloncesto", "balonmano", "voleibol", "tenis", "padel", "golf",
        "esqui", "esquiar", "patinar", "patinaje", "escalada", "senderismo",
        "montanismo", "surf", "remar en kayak", "kayak", "piragua",
        // Clases dirigidas y baile
        "zumba", "aerobic", "aerobics", "baile", "danza", "bailar",
        // Otros que no son entrenamiento de fuerza
        "saltar a la comba", "comba", "cuerda de saltar", "jump rope",
        "estiramiento", "estirar", "yoga", "pilates", "meditacion", "calentamiento",
    )

    /**
     * [categoryId] es la categoría de wger; null en los ejercicios propios, que
     * siempre se aceptan.
     */
    fun isGymExercise(name: String, categoryId: Long?): Boolean {
        if (categoryId == CARDIO_CATEGORY_ID) return false
        val normalized = normalizeForSearch(name)
        // En límite de palabra: "andar" vive dentro de "estándar" y descartaba
        // cualquier "press estándar" del catálogo.
        return nonGymKeywords.none { normalized.containsWordStarting(it) }
    }
}
