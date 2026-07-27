package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.MuscleGroupCategory
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleGroups

/** Qué hay que hacer con la sugerencia: añadir un ejercicio o cambiar uno por otro. */
enum class SuggestionKind { ANADIR, CAMBIAR }

/** Un ejercicio de la rutina, con lo justo para razonar sobre él. */
data class RoutineExerciseRef(
    /** Id de la fila de la rutina, no del ejercicio del catálogo. */
    val routineExerciseId: Long,
    val exerciseId: Long,
    val name: String,
    val dayId: Long,
    val dayName: String,
)

data class RoutineSuggestion(
    val kind: SuggestionKind,
    val message: String,
    /** Ejercicio del catálogo que se propone meter. */
    val exerciseId: Long,
    val exerciseName: String,
    val dayId: Long,
    val dayName: String,
    /** Fila de la rutina que se sustituye; null cuando solo hay que añadir. */
    val replacesRoutineExerciseId: Long? = null,
    val replacesName: String? = null,
)

/**
 * Convierte el análisis de equilibrio en cosas que se pueden hacer: qué ejercicio
 * añadir y dónde, y qué ejercicio cambiar por cuál.
 *
 * Avisar de que faltan glúteos está bien, pero el trabajo de buscar un ejercicio
 * de glúteos en un catálogo de ochocientos y decidir en qué día encaja lo puede
 * hacer la app. Es la misma lógica de elección del generador: se prefiere lo
 * compuesto para lo que falta del todo y no se repite nada que ya esté en la
 * rutina.
 */
object RoutineAdvisor {

    /** Más de cuatro sugerencias a la vez son una lista de tareas, no un consejo. */
    const val MAX_SUGGESTIONS = 4

    fun suggest(
        report: BalanceReport,
        routineExercises: List<RoutineExerciseRef>,
        catalog: List<CandidateExercise>,
    ): List<RoutineSuggestion> {
        if (catalog.isEmpty() || routineExercises.isEmpty()) return emptyList()

        val inRoutine = routineExercises.map { it.exerciseId }.toSet()
        val byId = catalog.associateBy { it.id }
        val categoryOf = MuscleGroups.all.associate { it.id to it.category }

        // Días ordenados por lo poco cargados que están, para repartir lo que se añade.
        val days = routineExercises.groupBy { it.dayId }
        val suggestions = mutableListOf<RoutineSuggestion>()
        val taken = mutableSetOf<Long>()

        val deficient = report.scores
            .filter { it.level == CoverageLevel.AUSENTE || it.level == CoverageLevel.BAJO }
            .sortedBy { it.effectiveSets }
        val excessive = report.scores.filter { it.level == CoverageLevel.EXCESIVO }
            .sortedByDescending { it.effectiveSets }

        // 1. Cambios: quitar volumen de donde sobra para dárselo a donde falta.
        //    Solo se toca lo que no es compuesto; un básico grande no se sacrifica.
        for (over in excessive) {
            if (suggestions.size >= MAX_SUGGESTIONS) break
            val under = deficient.firstOrNull { it.group.id !in taken } ?: break
            val victim = routineExercises.firstOrNull { ref ->
                val candidate = byId[ref.exerciseId] ?: return@firstOrNull false
                over.group.id in candidate.primaryGroupIds && !candidate.isCompound
            } ?: continue
            val replacement = pickFor(under.group, catalog, inRoutine + taken) ?: continue

            taken += under.group.id
            taken += replacement.id
            suggestions += RoutineSuggestion(
                kind = SuggestionKind.CAMBIAR,
                message = "Te sobra volumen de ${over.group.name} y te falta de ${under.group.name}: " +
                    "cambia ${victim.name} por ${replacement.name} en ${victim.dayName}.",
                exerciseId = replacement.id,
                exerciseName = replacement.name,
                dayId = victim.dayId,
                dayName = victim.dayName,
                replacesRoutineExerciseId = victim.routineExerciseId,
                replacesName = victim.name,
            )
        }

        // 2. Altas: lo que sigue faltando se añade al día que menos ejercicios tenga
        //    de entre los que ya trabajan esa zona del cuerpo.
        for (score in deficient) {
            if (suggestions.size >= MAX_SUGGESTIONS) break
            if (score.group.id in taken) continue
            val exercise = pickFor(score.group, catalog, inRoutine + taken) ?: continue
            val day = bestDayFor(score.group, days, byId, categoryOf) ?: continue

            taken += score.group.id
            taken += exercise.id
            val reason = if (score.level == CoverageLevel.AUSENTE) {
                "No entrenas ${score.group.name}"
            } else {
                "Te falta volumen de ${score.group.name}"
            }
            suggestions += RoutineSuggestion(
                kind = SuggestionKind.ANADIR,
                message = "$reason: añade ${exercise.name} a ${day.second}.",
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                dayId = day.first,
                dayName = day.second,
            )
        }

        return suggestions
    }

    /** El mejor candidato para un grupo: compuesto primero y nunca uno que ya esté. */
    private fun pickFor(
        group: MuscleGroupInfo,
        catalog: List<CandidateExercise>,
        excluded: Set<Long>,
    ): CandidateExercise? = catalog
        .filter { group.id in it.primaryGroupIds && it.id !in excluded }
        .minWithOrNull(
            compareByDescending<CandidateExercise> { it.isCompound }
                .thenBy { it.complexity }
                .thenBy { it.id },
        )

    /**
     * Día donde encaja el grupo: el que menos ejercicios tenga de entre los que ya
     * trabajan su misma zona (empuje, tirón, pierna o core). Si ninguno la trabaja,
     * el más corto de todos, que es donde mejor cabe uno más.
     */
    private fun bestDayFor(
        group: MuscleGroupInfo,
        days: Map<Long, List<RoutineExerciseRef>>,
        byId: Map<Long, CandidateExercise>,
        categoryOf: Map<Long, MuscleGroupCategory>,
    ): Pair<Long, String>? {
        if (days.isEmpty()) return null
        val sameCategory = days.filter { (_, exercises) ->
            exercises.any { ref ->
                byId[ref.exerciseId]?.primaryGroupIds.orEmpty().any { groupId ->
                    categoryOf[groupId] == group.category
                }
            }
        }
        val pool = sameCategory.ifEmpty { days }
        val (dayId, exercises) = pool.minByOrNull { (_, list) -> list.size } ?: return null
        return dayId to exercises.first().dayName
    }
}
