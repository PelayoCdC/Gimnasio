package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExerciseComplexity
import com.pelayo.gimnasio.domain.model.ExerciseStyle
import com.pelayo.gimnasio.domain.model.ExperienceLevel
import com.pelayo.gimnasio.domain.model.MuscleGroupCategory
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleRole
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
    /** Categoría de wger; null en los ejercicios creados por el usuario. */
    val categoryId: Long? = null,
) {
    /** Consideramos compuesto el que implica a más de un grupo secundario. */
    val isCompound: Boolean get() = secondaryGroupIds.size >= 2

    val complexity: ExerciseComplexity
        get() = ExerciseComplexityClassifier.classify(name, equipmentIds)

    val style: ExerciseStyle
        get() = ExerciseStyleClassifier.classify(name, equipmentIds)

    /** Falso para nadar, correr y demás cosas que no se hacen en una sala de pesas. */
    val isGymExercise: Boolean get() = GymExerciseFilter.isGymExercise(name, categoryId)
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
    /** Día de la semana propuesto (ISO, lunes = 1), para que los recordatorios
     * y la rotación funcionen sin tener que asignarlos a mano después. */
    val weekday: Int? = null,
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

    /**
     * Ids sintéticos para material que wger no tiene: máquinas y poleas llegan
     * sin equipamiento y solo el nombre las delata. La pantalla los ofrece como
     * chips junto al material real, y sin ellos marcados el generador no propone
     * jalones ni prensas a quien entrena en casa con mancuernas.
     */
    const val MACHINE_EQUIPMENT_ID = -1L
    const val PULLEY_EQUIPMENT_ID = -2L

    /** Series por ejercicio que se pueden elegir; fuera de ahí no es entrenar. */
    const val MIN_SETS = 2
    const val MAX_SETS = 6
    val SET_OPTIONS = (MIN_SETS..MAX_SETS).toList()

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
        setsPerExercise: Int? = null,
    ): GeneratedRoutine {
        // Las series que pida el usuario mandan sobre las del objetivo, y como el
        // tiempo por sesión es el mismo, más series significa menos ejercicios.
        val prescription = TrainingPrescription.forGoal(goal, level)
            .let { base ->
                setsPerExercise?.let { base.copy(sets = it.coerceIn(MIN_SETS, MAX_SETS)) } ?: base
            }

        // Nadar, correr y demás fuera antes de nada: por muy bien que cuadren los
        // músculos, no son ejercicios de sala.
        val gymOnly = catalog.filter { it.isGymExercise }

        // El estilo se memoiza igual que la complejidad: los getters recorren
        // listas de palabras clave y se consultan en cada comparación del orden.
        val styleById = gymOnly.associate { it.id to it.style }
        val withEquipment = gymOnly.filter { exercise ->
            availableEquipmentIds == null ||
                fitsEquipment(exercise, styleById.getValue(exercise.id), availableEquipmentIds)
        }
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
        val chosenPerDay = templates.map { template ->
            template.slots.mapNotNullTo(mutableListOf()) { slot ->
                val picked = pick(slot, usable, used, penaltyById, styleById)
                if (picked == null && slot.group.id !in coverable) uncovered += slot.group.name
                picked?.also { used += it.id }
            }
        }

        rebalance(chosenPerDay, usable, used, prescription.sets, penaltyById, styleById, level)

        val weekdays = proposedWeekdays(templates.size)
        val days = templates.mapIndexed { index, template ->
            val chosen = chosenPerDay[index]
            GeneratedDay(
                name = template.name,
                exerciseIds = chosen.map { it.id },
                estimatedMinutes = SessionTimeEstimator.minutesFor(chosen.size, prescription),
                weekday = weekdays.getOrNull(index),
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
     * Si un ejercicio se puede hacer con el material marcado. Tres reglas:
     *
     * - Máquinas y poleas no existen como equipamiento en wger (llegan sin
     *   material), así que se detectan por el estilo y piden su chip sintético.
     * - La lista de material de wger son alternativas, no un conjunto: un press
     *   con {barra, banco} vale con cualquiera de los dos, no exige ambos.
     * - Sin material declarado y sin pinta de máquina, pasa: puede ser cualquier
     *   cosa y descartarlo vaciaría el catálogo.
     */
    private fun fitsEquipment(
        exercise: CandidateExercise,
        style: ExerciseStyle,
        available: Set<Long>,
    ): Boolean = when {
        style == ExerciseStyle.MAQUINA -> MACHINE_EQUIPMENT_ID in available
        style == ExerciseStyle.POLEA -> PULLEY_EQUIPMENT_ID in available
        exercise.equipmentIds.isEmpty() -> true
        else -> exercise.equipmentIds.any { it in available }
    }

    /** Reparto estándar de días por semana (ISO, lunes = 1): 3 días → L/X/V, etc. */
    private fun proposedWeekdays(count: Int): List<Int> = when (count) {
        1 -> listOf(1)
        2 -> listOf(1, 4)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 2, 4, 5)
        5 -> listOf(1, 2, 3, 4, 5)
        else -> listOf(1, 2, 3, 4, 5, 6)
    }

    /** Pases de reequilibrado: pasado esto, o está equilibrada o no cabe. */
    private const val MAX_REBALANCE_PASSES = 8

    /** Candidatos que se prueban por grupo necesitado en cada pase. */
    private const val CANDIDATES_PER_GROUP = 3

    /**
     * Cambia ejercicios que sobran por otros que faltan hasta que la rutina deje de
     * tener avisos de descompensación.
     *
     * Las plantillas reparten bien cuando caben enteras, pero el tiempo disponible
     * las recorta y ahí aparecen los huecos: la propuesta nacía con "no entrenas
     * gemelos" ya escrito. Esto lo arregla midiendo con el mismo analizador que
     * escribe el aviso —así lo que se optimiza es exactamente lo que se ve— y
     * probando sustituciones hasta que no baje el coste.
     *
     * El primer ejercicio de cada día no se toca: es el compuesto grande sobre el
     * que se construye la sesión, y cambiarlo por unas elevaciones de gemelos
     * equilibraría el papel a costa de arruinar el entrenamiento.
     */
    private fun rebalance(
        days: List<MutableList<CandidateExercise>>,
        usable: List<CandidateExercise>,
        used: MutableSet<Long>,
        sets: Int,
        penaltyById: Map<Long, Int>,
        styleById: Map<Long, ExerciseStyle>,
        level: ExperienceLevel,
    ) {
        repeat(MAX_REBALANCE_PASSES) {
            var bestCost = cost(days, sets, level)
            if (bestCost == 0) return

            val candidates = groupsToReinforce(days, sets, level).flatMap { groupId ->
                usable.asSequence()
                    .filter { groupId in it.primaryGroupIds && it.id !in used }
                    .sortedWith(preferenceOrder(penaltyById, styleById))
                    .take(CANDIDATES_PER_GROUP)
                    .toList()
            }
            if (candidates.isEmpty()) return

            var bestSwap: Triple<Int, Int, CandidateExercise>? = null
            for ((dayIndex, exercises) in days.withIndex()) {
                for (position in exercises.indices) {
                    if (position == 0) continue // el compuesto del día no se toca
                    val victim = exercises[position]
                    for (candidate in candidates) {
                        exercises[position] = candidate
                        val candidateCost = cost(days, sets, level)
                        exercises[position] = victim
                        if (candidateCost < bestCost) {
                            bestCost = candidateCost
                            bestSwap = Triple(dayIndex, position, candidate)
                        }
                    }
                }
            }

            val (dayIndex, position, replacement) = bestSwap ?: return
            used -= days[dayIndex][position].id
            used += replacement.id
            days[dayIndex][position] = replacement
        }
    }

    /**
     * Lo mal repartida que está la rutina: cada aviso pesa mucho más que los sets
     * que falten, pero contar también el déficit da una pendiente por la que bajar
     * cuando ningún cambio suelto quita un aviso entero.
     */
    private fun cost(days: List<List<CandidateExercise>>, sets: Int, level: ExperienceLevel): Int {
        val report = MuscleBalanceAnalyzer.analyze(volumeEntries(days, sets), level)
        val warnings = report.warnings.count { it.severity == WarningSeverity.AVISO }
        val deficit = report.scores.sumOf {
            (MuscleBalanceAnalyzer.lowThresholdFor(level) - it.effectiveSets).coerceAtLeast(0.0)
        }
        return warnings * 100 + deficit.toInt()
    }

    /**
     * Grupos por los que merece la pena cambiar algo: los que van cortos de volumen
     * y, si la rutina está torcida hacia un lado, los del lado que se queda corto.
     *
     * Sin lo segundo el reequilibrado no sabía arreglar "mucho empuje y poco
     * tirón": ningún grupo estaba por debajo del mínimo, así que no había nada que
     * probar y el aviso se quedaba puesto.
     */
    private fun groupsToReinforce(
        days: List<List<CandidateExercise>>,
        sets: Int,
        level: ExperienceLevel,
    ): List<Long> {
        val report = MuscleBalanceAnalyzer.analyze(volumeEntries(days, sets), level)
        val deficient = report.scores
            .filter { it.level == CoverageLevel.AUSENTE || it.level == CoverageLevel.BAJO }
            .sortedBy { it.effectiveSets }
            .map { it.group.id }

        fun volumeOf(vararg categories: MuscleGroupCategory) = report.scores
            .filter { it.group.category in categories }
            .sumOf { it.effectiveSets }

        fun idsOf(vararg categories: MuscleGroupCategory) =
            MuscleGroups.all.filter { it.category in categories }.map { it.id }

        val push = volumeOf(MuscleGroupCategory.PUSH)
        val pull = volumeOf(MuscleGroupCategory.PULL)
        val legs = volumeOf(MuscleGroupCategory.LEGS)

        val lagging = buildList {
            if (push > 0 && pull > 0) {
                val ratio = push / pull
                if (ratio > MuscleBalanceAnalyzer.PUSH_PULL_RATIO_RANGE.endInclusive) {
                    addAll(idsOf(MuscleGroupCategory.PULL))
                } else if (ratio < MuscleBalanceAnalyzer.PUSH_PULL_RATIO_RANGE.start) {
                    addAll(idsOf(MuscleGroupCategory.PUSH))
                }
            }
            val upper = push + pull
            if (upper > 0 && legs > 0) {
                val ratio = upper / legs
                if (ratio > MuscleBalanceAnalyzer.UPPER_LEGS_RATIO_RANGE.endInclusive) {
                    addAll(idsOf(MuscleGroupCategory.LEGS))
                } else if (ratio < MuscleBalanceAnalyzer.UPPER_LEGS_RATIO_RANGE.start) {
                    addAll(idsOf(MuscleGroupCategory.PUSH, MuscleGroupCategory.PULL))
                }
            }
        }

        return (deficient + lagging).distinct()
    }

    private fun volumeEntries(days: List<List<CandidateExercise>>, sets: Int): List<MuscleVolumeEntry> =
        days.flatten().flatMap { exercise ->
            exercise.primaryGroupIds.map { MuscleVolumeEntry(sets, MuscleRole.PRIMARY, it) } +
                exercise.secondaryGroupIds.map { MuscleVolumeEntry(sets, MuscleRole.SECONDARY, it) }
        }

    /**
     * Orden de preferencia entre candidatos igual de válidos: primero lo que tolera
     * el nivel, después el material que pidió el usuario (máquina y polea, luego
     * mancuernas y banco) y por último el id, para que la propuesta sea siempre la
     * misma con el mismo catálogo.
     */
    private fun preferenceOrder(
        penaltyById: Map<Long, Int>,
        styleById: Map<Long, ExerciseStyle>,
    ): Comparator<CandidateExercise> =
        compareBy<CandidateExercise> { penaltyById[it.id] ?: 0 }
            .thenBy { styleById[it.id]?.priority ?: ExerciseStyle.OTRO.priority }
            .thenBy { it.id }

    /**
     * Elige el mejor candidato para un hueco: descarta los ya usados, prefiere lo
     * menos técnico para el nivel, luego el carácter compuesto o de aislamiento que
     * pide el slot y, a igualdad, el material más cómodo (máquina y polea antes que
     * mancuernas, y estas antes que la barra).
     */
    private fun pick(
        slot: ExerciseSlot,
        catalog: List<CandidateExercise>,
        used: Set<Long>,
        penaltyById: Map<Long, Int>,
        styleById: Map<Long, ExerciseStyle>,
    ): CandidateExercise? {
        val forGroup = catalog.filter { slot.group.id in it.primaryGroupIds && it.id !in used }
        if (forGroup.isEmpty()) return null
        return forGroup.sortedWith(
            compareBy<CandidateExercise> { penaltyById[it.id] ?: 0 }
                .thenByDescending { it.isCompound == slot.preferCompound }
                .thenBy { styleById[it.id]?.priority ?: ExerciseStyle.OTRO.priority }
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
