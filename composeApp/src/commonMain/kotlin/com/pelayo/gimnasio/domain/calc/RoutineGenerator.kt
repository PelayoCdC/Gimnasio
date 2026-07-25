package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.TrainingPrescription

/** Ejercicio candidato del catálogo, con lo necesario para elegirlo. */
data class CandidateExercise(
    val id: Long,
    val name: String,
    val primaryGroupIds: Set<Long>,
    val secondaryGroupIds: Set<Long>,
    val equipmentIds: Set<Long>,
) {
    /** Consideramos compuesto el que implica a más de un grupo secundario. */
    val isCompound: Boolean get() = secondaryGroupIds.size >= 2
}

/** Hueco a rellenar dentro de un día de la plantilla. */
data class ExerciseSlot(
    val group: MuscleGroupInfo,
    val preferCompound: Boolean,
)

data class GeneratedDay(
    val name: String,
    val exerciseIds: List<Long>,
)

data class GeneratedRoutine(
    val name: String,
    val goal: RoutineGoal,
    val days: List<GeneratedDay>,
    val prescription: TrainingPrescription,
    /** Grupos que no se pudieron cubrir con el material disponible. */
    val uncoveredGroups: List<String>,
)

/**
 * Genera rutinas equilibradas a partir de plantillas deterministas por número de
 * días. Rellena cada hueco con un ejercicio del catálogo que trabaje ese grupo
 * como primario y que se pueda hacer con el material disponible, sin repetir
 * ejercicio en toda la semana.
 */
object RoutineGenerator {

    private val G = MuscleGroups

    private fun fullBody() = listOf(
        ExerciseSlot(G.CUADRICEPS, preferCompound = true),
        ExerciseSlot(G.PECHO, preferCompound = true),
        ExerciseSlot(G.ESPALDA, preferCompound = true),
        ExerciseSlot(G.ISQUIOS, preferCompound = false),
        ExerciseSlot(G.HOMBROS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
    )

    private fun push() = listOf(
        ExerciseSlot(G.PECHO, preferCompound = true),
        ExerciseSlot(G.PECHO, preferCompound = false),
        ExerciseSlot(G.HOMBROS, preferCompound = true),
        ExerciseSlot(G.HOMBROS, preferCompound = false),
        ExerciseSlot(G.TRICEPS, preferCompound = false),
    )

    private fun pull() = listOf(
        ExerciseSlot(G.ESPALDA, preferCompound = true),
        ExerciseSlot(G.ESPALDA, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
    )

    private fun legs() = listOf(
        ExerciseSlot(G.CUADRICEPS, preferCompound = true),
        ExerciseSlot(G.ISQUIOS, preferCompound = true),
        ExerciseSlot(G.GLUTEOS, preferCompound = false),
        ExerciseSlot(G.GEMELOS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
    )

    private fun upper() = listOf(
        ExerciseSlot(G.PECHO, preferCompound = true),
        ExerciseSlot(G.ESPALDA, preferCompound = true),
        ExerciseSlot(G.HOMBROS, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
        ExerciseSlot(G.TRICEPS, preferCompound = false),
    )

    private data class DayTemplate(val name: String, val slots: List<ExerciseSlot>)

    /** Plantillas de división semanal según los días disponibles. */
    private fun templateFor(daysPerWeek: Int): List<DayTemplate> = when (daysPerWeek.coerceIn(1, 6)) {
        1 -> listOf(DayTemplate("Cuerpo completo", fullBody()))
        2 -> listOf(
            DayTemplate("Cuerpo completo A", fullBody()),
            DayTemplate("Cuerpo completo B", fullBody()),
        )
        3 -> listOf(
            DayTemplate("Empuje", push()),
            DayTemplate("Tirón", pull()),
            DayTemplate("Pierna", legs()),
        )
        4 -> listOf(
            DayTemplate("Torso A", upper()),
            DayTemplate("Pierna A", legs()),
            DayTemplate("Torso B", upper()),
            DayTemplate("Pierna B", legs()),
        )
        5 -> listOf(
            DayTemplate("Empuje", push()),
            DayTemplate("Tirón", pull()),
            DayTemplate("Pierna", legs()),
            DayTemplate("Torso", upper()),
            DayTemplate("Pierna B", legs()),
        )
        else -> listOf(
            DayTemplate("Empuje A", push()),
            DayTemplate("Tirón A", pull()),
            DayTemplate("Pierna A", legs()),
            DayTemplate("Empuje B", push()),
            DayTemplate("Tirón B", pull()),
            DayTemplate("Pierna B", legs()),
        )
    }

    fun generate(
        daysPerWeek: Int,
        goal: RoutineGoal,
        catalog: List<CandidateExercise>,
        availableEquipmentIds: Set<Long>? = null,
        name: String? = null,
    ): GeneratedRoutine {
        val usable = catalog.filter { exercise ->
            availableEquipmentIds == null ||
                exercise.equipmentIds.isEmpty() ||
                availableEquipmentIds.containsAll(exercise.equipmentIds)
        }

        val used = mutableSetOf<Long>()
        val uncovered = mutableSetOf<String>()

        val days = templateFor(daysPerWeek).map { template ->
            val chosen = template.slots.mapNotNull { slot ->
                pick(slot, usable, used)?.also { used += it.id }?.id
                    ?: run { uncovered += slot.group.name; null }
            }
            GeneratedDay(template.name, chosen)
        }

        // Segunda pasada: si un grupo quedó sin cubrir, se permite repetir
        // ejercicio antes que dejarlo fuera de la rutina por completo.
        val repaired = days.map { day ->
            val missing = templateFor(daysPerWeek)
                .first { it.name == day.name }.slots
                .filter { slot -> uncovered.contains(slot.group.name) }
                .distinctBy { it.group.id }
            val extras = missing.mapNotNull { slot -> pick(slot, usable, emptySet())?.id }
            if (extras.isEmpty()) day else day.copy(exerciseIds = day.exerciseIds + extras)
        }

        val stillUncovered = uncovered.filter { groupName ->
            val group = MuscleGroups.all.first { it.name == groupName }
            usable.none { group.id in it.primaryGroupIds }
        }

        return GeneratedRoutine(
            name = name ?: defaultName(daysPerWeek, goal),
            goal = goal,
            days = repaired,
            prescription = TrainingPrescription.forGoal(goal),
            uncoveredGroups = stillUncovered.sorted(),
        )
    }

    /**
     * Elige el mejor candidato para un hueco: prioriza los que aún no se han
     * usado y el carácter compuesto o de aislamiento que pide el slot.
     */
    private fun pick(
        slot: ExerciseSlot,
        catalog: List<CandidateExercise>,
        used: Set<Long>,
    ): CandidateExercise? {
        val forGroup = catalog.filter { slot.group.id in it.primaryGroupIds && it.id !in used }
        if (forGroup.isEmpty()) return null
        return forGroup.sortedWith(
            compareByDescending<CandidateExercise> { it.isCompound == slot.preferCompound }
                .thenBy { it.id },
        ).first()
    }

    private fun defaultName(daysPerWeek: Int, goal: RoutineGoal): String {
        val split = when (daysPerWeek.coerceIn(1, 6)) {
            1, 2 -> "Cuerpo completo"
            3 -> "Empuje/Tirón/Pierna"
            4 -> "Torso/Pierna"
            5 -> "Mixta"
            else -> "PPL x2"
        }
        val goalLabel = when (goal) {
            RoutineGoal.FUERZA -> "fuerza"
            RoutineGoal.HIPERTROFIA -> "hipertrofia"
            RoutineGoal.RESISTENCIA -> "resistencia"
            RoutineGoal.PERDIDA_GRASA -> "definición"
        }
        return "$split · $daysPerWeek días · $goalLabel"
    }
}
