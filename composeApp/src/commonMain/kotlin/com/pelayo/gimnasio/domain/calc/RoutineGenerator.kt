package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseComplexity
import com.pelayo.gimnasio.domain.model.ExperienceLevel
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.TrainingPrescription
import com.pelayo.gimnasio.domain.model.labelEs

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

    val complexity: ExerciseComplexity
        get() = ExerciseComplexityClassifier.classify(name, equipmentIds)
}

/** Hueco a rellenar dentro de un día de la plantilla. */
data class ExerciseSlot(
    val group: MuscleGroupInfo,
    val preferCompound: Boolean,
)

data class GeneratedDay(
    val name: String,
    val exerciseIds: List<Long>,
    val estimatedMinutes: Int = 0,
)

data class GeneratedRoutine(
    val name: String,
    val goal: RoutineGoal,
    val days: List<GeneratedDay>,
    val prescription: TrainingPrescription,
    /** Grupos que no se pudieron cubrir con el material disponible. */
    val uncoveredGroups: List<String>,
    val level: ExperienceLevel = ExperienceLevel.INTERMEDIO,
    /** Minutos por sesión que pidió el usuario. */
    val minutesPerSession: Int = RoutineGenerator.DEFAULT_MINUTES_PER_SESSION,
    /** Ejercicios del catálogo descartados por ser demasiado técnicos para el nivel. */
    val excludedForLevel: Int = 0,
) {
    /** Duración estimada de la sesión más larga. */
    val estimatedMinutes: Int get() = days.maxOfOrNull { it.estimatedMinutes } ?: 0

    /** Resumen para la descripción de la rutina guardada. */
    val description: String
        get() = "${level.labelEs} · sesiones de unos $estimatedMinutes min"
}

/**
 * Genera rutinas equilibradas a partir de plantillas deterministas por número de
 * días y nivel. Rellena cada hueco con un ejercicio del catálogo que trabaje ese
 * grupo como primario, que se pueda hacer con el material disponible y que no
 * pase de la exigencia técnica que tolera el nivel, sin repetir ejercicio en toda
 * la semana.
 *
 * El tiempo disponible decide cuántos huecos se rellenan: las plantillas van
 * ordenadas de más a menos importante, así que recortar por el final deja fuera
 * el aislamiento antes que los compuestos grandes.
 */
object RoutineGenerator {

    const val DEFAULT_MINUTES_PER_SESSION = 60

    /** Opciones de duración que ofrece la pantalla. */
    val SESSION_LENGTHS = listOf(30, 45, 60, 75, 90)

    private val G = MuscleGroups

    private fun fullBody() = listOf(
        ExerciseSlot(G.CUADRICEPS, preferCompound = true),
        ExerciseSlot(G.PECHO, preferCompound = true),
        ExerciseSlot(G.ESPALDA, preferCompound = true),
        ExerciseSlot(G.ISQUIOS, preferCompound = false),
        ExerciseSlot(G.HOMBROS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
        ExerciseSlot(G.TRICEPS, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
    )

    private fun push() = listOf(
        ExerciseSlot(G.PECHO, preferCompound = true),
        ExerciseSlot(G.HOMBROS, preferCompound = true),
        ExerciseSlot(G.PECHO, preferCompound = false),
        ExerciseSlot(G.TRICEPS, preferCompound = false),
        ExerciseSlot(G.HOMBROS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
        ExerciseSlot(G.TRICEPS, preferCompound = false),
        ExerciseSlot(G.PECHO, preferCompound = false),
    )

    private fun pull() = listOf(
        ExerciseSlot(G.ESPALDA, preferCompound = true),
        ExerciseSlot(G.ESPALDA, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
        ExerciseSlot(G.ESPALDA, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
    )

    private fun legs() = listOf(
        ExerciseSlot(G.CUADRICEPS, preferCompound = true),
        ExerciseSlot(G.ISQUIOS, preferCompound = true),
        ExerciseSlot(G.GLUTEOS, preferCompound = false),
        ExerciseSlot(G.GEMELOS, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
        ExerciseSlot(G.CUADRICEPS, preferCompound = false),
        ExerciseSlot(G.ISQUIOS, preferCompound = false),
        ExerciseSlot(G.GLUTEOS, preferCompound = false),
    )

    private fun upper() = listOf(
        ExerciseSlot(G.PECHO, preferCompound = true),
        ExerciseSlot(G.ESPALDA, preferCompound = true),
        ExerciseSlot(G.HOMBROS, preferCompound = false),
        ExerciseSlot(G.BICEPS, preferCompound = false),
        ExerciseSlot(G.TRICEPS, preferCompound = false),
        ExerciseSlot(G.ESPALDA, preferCompound = false),
        ExerciseSlot(G.PECHO, preferCompound = false),
        ExerciseSlot(G.CORE, preferCompound = false),
    )

    private data class DayTemplate(val name: String, val slots: List<ExerciseSlot>)

    /**
     * Plantillas de división semanal. Quien empieza entrena cada grupo con más
     * frecuencia y menos ejercicios por sesión: cuerpo completo hasta tres días
     * y torso/pierna a partir de cuatro, en vez de las divisiones por patrón que
     * concentran mucho volumen en un solo día.
     */
    private fun templateFor(daysPerWeek: Int, level: ExperienceLevel): List<DayTemplate> {
        val days = daysPerWeek.coerceIn(1, 6)
        if (level == ExperienceLevel.PRINCIPIANTE) {
            return when (days) {
                1 -> listOf(DayTemplate("Cuerpo completo", fullBody()))
                2 -> listOf(
                    DayTemplate("Cuerpo completo A", fullBody()),
                    DayTemplate("Cuerpo completo B", fullBody()),
                )
                3 -> listOf(
                    DayTemplate("Cuerpo completo A", fullBody()),
                    DayTemplate("Cuerpo completo B", fullBody()),
                    DayTemplate("Cuerpo completo C", fullBody()),
                )
                4 -> listOf(
                    DayTemplate("Torso A", upper()),
                    DayTemplate("Pierna A", legs()),
                    DayTemplate("Torso B", upper()),
                    DayTemplate("Pierna B", legs()),
                )
                5 -> listOf(
                    DayTemplate("Torso A", upper()),
                    DayTemplate("Pierna A", legs()),
                    DayTemplate("Torso B", upper()),
                    DayTemplate("Pierna B", legs()),
                    DayTemplate("Cuerpo completo", fullBody()),
                )
                else -> listOf(
                    DayTemplate("Torso A", upper()),
                    DayTemplate("Pierna A", legs()),
                    DayTemplate("Torso B", upper()),
                    DayTemplate("Pierna B", legs()),
                    DayTemplate("Torso C", upper()),
                    DayTemplate("Pierna C", legs()),
                )
            }
        }
        return when (days) {
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
    }

    /**
     * Cuántos ejercicios por sesión admite cada nivel, por mucho tiempo que haya.
     * Un principiante con dos horas libres no necesita diez ejercicios: necesita
     * hacer bien seis.
     */
    private fun exerciseCountRange(level: ExperienceLevel): IntRange = when (level) {
        ExperienceLevel.PRINCIPIANTE -> 3..6
        ExperienceLevel.INTERMEDIO -> 3..7
        ExperienceLevel.AVANZADO -> 3..8
    }

    /** Lo más técnico que se le propone a cada nivel. */
    private fun maxComplexity(level: ExperienceLevel): ExerciseComplexity = when (level) {
        ExperienceLevel.PRINCIPIANTE -> ExerciseComplexity.INTERMEDIO
        else -> ExerciseComplexity.AVANZADO
    }

    /**
     * Penalización por complejidad al ordenar candidatos: a igualdad de todo lo
     * demás se elige lo más sencillo de ejecutar, y solo se recurre a lo técnico
     * cuando no hay otra cosa que trabaje ese grupo.
     */
    private fun penalty(complexity: ExerciseComplexity, level: ExperienceLevel): Int = when (level) {
        ExperienceLevel.PRINCIPIANTE -> when (complexity) {
            ExerciseComplexity.BASICO -> 0
            else -> 1
        }
        ExperienceLevel.INTERMEDIO -> if (complexity == ExerciseComplexity.AVANZADO) 1 else 0
        ExperienceLevel.AVANZADO -> 0
    }

    fun generate(
        daysPerWeek: Int,
        goal: RoutineGoal,
        catalog: List<CandidateExercise>,
        availableEquipmentIds: Set<Long>? = null,
        name: String? = null,
        level: ExperienceLevel = ExperienceLevel.INTERMEDIO,
        minutesPerSession: Int = DEFAULT_MINUTES_PER_SESSION,
    ): GeneratedRoutine {
        val prescription = TrainingPrescription.forGoal(goal, level)

        val withEquipment = catalog.filter { exercise ->
            availableEquipmentIds == null ||
                exercise.equipmentIds.isEmpty() ||
                availableEquipmentIds.containsAll(exercise.equipmentIds)
        }
        // La complejidad se calcula una sola vez por ejercicio: el getter recorre
        // listas de palabras clave y aquí se consulta en cada comparación.
        val complexityById = withEquipment.associate { it.id to it.complexity }
        val limit = maxComplexity(level)
        val usable = withEquipment.filter { complexityById.getValue(it.id) <= limit }
        val excludedForLevel = withEquipment.size - usable.size
        val penaltyById = usable.associate { it.id to penalty(complexityById.getValue(it.id), level) }

        val exercisesPerDay = SessionTimeEstimator.exercisesThatFit(
            minutes = minutesPerSession,
            prescription = prescription,
            allowed = exerciseCountRange(level),
        )

        val templates = templateFor(daysPerWeek, level).map { template ->
            template.copy(slots = template.slots.take(exercisesPerDay))
        }

        // Un grupo sin ningún ejercicio utilizable es un problema de material o de
        // nivel y hay que avisar. Que se agoten a mitad de semana los ejercicios de
        // un grupo no lo es: el grupo ya está entrenado, así que el hueco sobrante
        // se deja pasar antes que repetir ejercicio.
        val coverable = usable.flatMapTo(mutableSetOf()) { it.primaryGroupIds }
        val uncovered = mutableSetOf<String>()

        val used = mutableSetOf<Long>()
        val days = templates.map { template ->
            val chosen = template.slots.mapNotNull { slot ->
                val picked = pick(slot, usable, used, penaltyById)
                if (picked == null && slot.group.id !in coverable) uncovered += slot.group.name
                picked?.also { used += it.id }?.id
            }
            GeneratedDay(
                name = template.name,
                exerciseIds = chosen,
                estimatedMinutes = SessionTimeEstimator.minutesFor(chosen.size, prescription),
            )
        }

        return GeneratedRoutine(
            name = name ?: defaultName(daysPerWeek, goal, level),
            goal = goal,
            days = days,
            prescription = prescription,
            uncoveredGroups = uncovered.sorted(),
            level = level,
            minutesPerSession = minutesPerSession,
            excludedForLevel = excludedForLevel,
        )
    }

    /**
     * Elige el mejor candidato para un hueco: descarta los ya usados, prefiere lo
     * menos técnico para el nivel y luego el carácter compuesto o de aislamiento
     * que pide el slot.
     */
    private fun pick(
        slot: ExerciseSlot,
        catalog: List<CandidateExercise>,
        used: Set<Long>,
        penaltyById: Map<Long, Int>,
    ): CandidateExercise? {
        val forGroup = catalog.filter { slot.group.id in it.primaryGroupIds && it.id !in used }
        if (forGroup.isEmpty()) return null
        return forGroup.sortedWith(
            compareBy<CandidateExercise> { penaltyById[it.id] ?: 0 }
                .thenByDescending { it.isCompound == slot.preferCompound }
                .thenBy { it.id },
        ).first()
    }

    private fun defaultName(daysPerWeek: Int, goal: RoutineGoal, level: ExperienceLevel): String {
        val days = daysPerWeek.coerceIn(1, 6)
        val split = if (level == ExperienceLevel.PRINCIPIANTE) {
            if (days <= 3) "Cuerpo completo" else "Torso/Pierna"
        } else {
            when (days) {
                1, 2 -> "Cuerpo completo"
                3 -> "Empuje/Tirón/Pierna"
                4 -> "Torso/Pierna"
                5 -> "Mixta"
                else -> "PPL x2"
            }
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
