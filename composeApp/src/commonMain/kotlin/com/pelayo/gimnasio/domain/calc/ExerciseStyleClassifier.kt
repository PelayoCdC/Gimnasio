package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseStyle
import com.pelayo.gimnasio.util.containsWordStarting
import com.pelayo.gimnasio.util.normalizeForSearch

/**
 * Deduce con qué se hace un ejercicio, para poder preferir el material de gimnasio
 * sobre el resto.
 *
 * wger no tiene equipamiento para "máquina" ni para "polea": esos ejercicios
 * llegan sin material o con uno genérico, y lo único que los delata es el nombre
 * ("jalón al pecho", "prensa de piernas", "cruces en polea"). Por eso se mira
 * primero el nombre y solo después el equipamiento.
 */
object ExerciseStyleClassifier {

    // Ids de equipamiento de wger, los mismos que siembra WgerMappings.
    private const val BARRA = 1L
    private const val BARRA_Z = 2L
    private const val MANCUERNAS = 3L
    private const val BARRA_DOMINADAS = 6L
    private const val SIN_MATERIAL = 7L
    private const val BANCO = 8L
    private const val BANCO_INCLINADO = 9L
    private const val KETTLEBELL = 10L

    private val machineKeywords = listOf(
        "maquina", "multipower", "smith", "prensa", "hack", "contractor", "contractora",
        "pec deck", "peck deck", "aductor", "abductor", "sentado en maquina",
        "extension de cuadriceps", "extensiones de cuadriceps", "curl femoral",
        "femoral tumbado", "femoral sentado", "elevacion de gemelos sentado",
    )

    private val pulleyKeywords = listOf(
        "polea", "poleas", "jalon", "cable", "cruces", "crossover", "pulldown",
        "pull down", "remo sentado", "remo en punta", "face pull", "pushdown",
        "push down", "extension de triceps en polea", "patada de triceps en polea",
    )

    fun classify(name: String, equipmentIds: Set<Long>): ExerciseStyle {
        val normalized = normalizeForSearch(name)

        if (pulleyKeywords.any { normalized.containsWordStarting(it) }) return ExerciseStyle.POLEA
        if (machineKeywords.any { normalized.containsWordStarting(it) }) return ExerciseStyle.MAQUINA

        if (MANCUERNAS in equipmentIds) return ExerciseStyle.MANCUERNA
        if (BANCO in equipmentIds || BANCO_INCLINADO in equipmentIds) return ExerciseStyle.BANCO
        if (BARRA in equipmentIds || BARRA_Z in equipmentIds || KETTLEBELL in equipmentIds) {
            return ExerciseStyle.BARRA
        }
        if (BARRA_DOMINADAS in equipmentIds) return ExerciseStyle.PESO_CORPORAL
        if (equipmentIds.isEmpty() || equipmentIds == setOf(SIN_MATERIAL)) {
            return ExerciseStyle.PESO_CORPORAL
        }

        return ExerciseStyle.OTRO
    }
}
