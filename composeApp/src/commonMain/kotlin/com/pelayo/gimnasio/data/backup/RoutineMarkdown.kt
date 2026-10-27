package com.pelayo.gimnasio.data.backup

import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.RoutineDetail
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import com.pelayo.gimnasio.util.normalizeForSearch

/** Una rutina en texto, ya separada en piezas, antes de tocar la base de datos. */
data class ParsedRoutine(
    val name: String,
    val goal: RoutineGoal,
    val description: String?,
    val days: List<ParsedDay>,
) {
    val exerciseCount: Int get() = days.sumOf { it.exercises.size }
}

data class ParsedDay(
    val name: String,
    /** ISO, lunes = 1; null si el texto no dice qué día es. */
    val weekday: Int?,
    val exercises: List<ParsedExercise>,
)

/**
 * Un ejercicio leído del texto. Lo único obligatorio es el nombre: lo que falte se
 * completa con la prescripción del objetivo, para que valga un texto tan escueto
 * como "1. Press de banca".
 */
data class ParsedExercise(
    val name: String,
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val timeSeconds: Int?,
    val restSeconds: Int?,
    val weightKg: Double?,
    val notes: String?,
)

/**
 * Rutinas en Markdown, para que las lea una persona y las escriba un agente.
 *
 * Se eligió texto y no JSON justamente por el segundo caso: el usuario pega la
 * rutina en un chat, pide que se la mejoren y vuelve a importar lo que le
 * devuelvan. Por eso la escritura es canónica pero la lectura es tolerante: acepta
 * viñetas o numeración, "4x8-10" y "4 series × 8-10 reps", los guiones que sean, y
 * un ejercicio sin números se queda con lo que diga el objetivo.
 *
 * Lo que no se exporta es el historial: esto es la plantilla, no lo entrenado.
 */
object RoutineMarkdown {

    fun export(routine: RoutineDetail): String = buildString {
        appendLine("# ${routine.name}")
        appendLine()
        appendLine("Objetivo: ${routine.goal.labelEs}")
        routine.description?.takeIf { it.isNotBlank() }?.let { appendLine("Notas: $it") }
        appendLine()

        routine.days.forEach { day ->
            val weekday = day.weekday?.let { " (${weekdayNamesEs[it - 1].lowercase()})" }.orEmpty()
            appendLine("## ${day.name}$weekday")
            appendLine()
            if (day.exercises.isEmpty()) {
                appendLine("_Sin ejercicios._")
            } else {
                day.exercises.forEachIndexed { index, exercise ->
                    appendLine("${index + 1}. ${exerciseLine(exercise)}")
                    exercise.notes?.takeIf { it.isNotBlank() }?.let { appendLine("   Nota: $it") }
                }
            }
            appendLine()
        }

        appendLine("---")
        appendLine(
            "Generado por Gimnasio. Para volver a importarlo, respeta los encabezados " +
                "(`#` rutina, `##` día) y una línea por ejercicio.",
        )
    }

    private fun exerciseLine(exercise: com.pelayo.gimnasio.domain.model.RoutineExerciseItem): String =
        buildString {
            append(exercise.exerciseName)
            append(" — ")
            append(exercise.targetSets)
            append(" × ")
            if (exercise.exerciseType == ExerciseType.TIME && exercise.targetTimeSeconds != null) {
                append("${exercise.targetTimeSeconds} s")
            } else {
                val min = exercise.targetRepsMin
                val max = exercise.targetRepsMax
                when {
                    min != null && max != null && min != max -> append("$min-$max reps")
                    min != null -> append("$min reps")
                    else -> append("? reps")
                }
            }
            append(" — descanso ${exercise.restSeconds} s")
            exercise.targetWeight?.let { append(" — ${trimNumber(it)} kg") }
        }

    // --- Lectura --------------------------------------------------------------

    private val headingRegex = Regex("""^#\s+(.+)$""")
    private val dayRegex = Regex("""^##+\s+(.+)$""")
    private val goalRegex = Regex("""^(objetivo|goal)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val notesRegex = Regex(
        """^(notas?|descripcion|descripción|description)\s*:\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val exerciseNoteRegex = Regex("""^\s*nota\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** Viñeta o numeración al principio de la línea: "-", "*", "1." o "1)". */
    private val bulletRegex = Regex("""^\s*(?:[-*+•]|\d+[.)])\s+""")

    /** "4 × 8-10", "4x8", "4 series x 10", "3 × 45 s". */
    private val setsRepsRegex = Regex(
        """(\d+)\s*(?:series|sets)?\s*[x×]\s*(\d+)(?:\s*[-–—a]\s*(\d+))?\s*(s|seg|segundos)?""",
        RegexOption.IGNORE_CASE,
    )
    private val setsOnlyRegex = Regex("""(\d+)\s*(?:series|sets)\b""", RegexOption.IGNORE_CASE)
    private val restRegex = Regex(
        """(?:descanso|rest)\s*:?\s*(\d+)\s*(min|m\b|s|seg|segundos)?""",
        RegexOption.IGNORE_CASE,
    )
    private val weightRegex = Regex("""([\d]+(?:[.,]\d+)?)\s*kg""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""(\d+)\s*(?:s|seg|segundos)\b""", RegexOption.IGNORE_CASE)

    /**
     * Separadores entre el nombre y los números: guiones largos, barra o dos
     * puntos. Los dos puntos no exigen espacio delante ("Peso muerto: 4x6") y el
     * guion corto sí, porque hay ejercicios que lo llevan en el nombre.
     */
    private val separatorRegex = Regex("""\s*[—–|]\s*|\s*:\s+|\s+-\s+""")

    /**
     * Devuelve null si el texto no tiene forma de rutina (ni encabezado ni días),
     * para poder distinguir "fichero equivocado" de "rutina vacía".
     */
    fun parse(text: String): ParsedRoutine? {
        val lines = text.lines()
        var name: String? = null
        var goal: RoutineGoal? = null
        var description: String? = null

        val days = mutableListOf<ParsedDay>()
        var currentDayName: String? = null
        var currentWeekday: Int? = null
        var currentExercises = mutableListOf<ParsedExercise>()

        fun closeDay() {
            val dayName = currentDayName ?: return
            days += ParsedDay(dayName, currentWeekday, currentExercises.toList())
            currentExercises = mutableListOf()
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("_")) continue

            // Una regla horizontal cierra el día: lo que venga detrás es el pie del
            // documento, no un ejercicio más.
            if (line.startsWith("---") || line.startsWith("***")) {
                closeDay()
                currentDayName = null
                continue
            }

            val dayMatch = dayRegex.find(line)
            if (dayMatch != null) {
                closeDay()
                val (dayName, weekday) = splitWeekday(dayMatch.groupValues[1].trim())
                currentDayName = dayName
                currentWeekday = weekday
                continue
            }

            val headingMatch = headingRegex.find(line)
            if (headingMatch != null) {
                if (name == null) {
                    name = headingMatch.groupValues[1].trim()
                        .removePrefix("Rutina:").removePrefix("Rutina").trim(':', ' ')
                }
                continue
            }

            val goalMatch = goalRegex.find(line)
            if (goalMatch != null) {
                goal = parseGoal(goalMatch.groupValues[2])
                continue
            }

            // Dentro de un día, "Nota:" cuelga del ejercicio anterior; fuera es la
            // descripción de la rutina. El orden importa porque los dos patrones
            // encajan con la misma línea.
            val noteMatch = exerciseNoteRegex.find(line)
            if (noteMatch != null && currentDayName != null) {
                val last = currentExercises.removeLastOrNull()
                if (last != null) currentExercises += last.copy(notes = noteMatch.groupValues[1].trim())
                continue
            }

            val notesMatch = notesRegex.find(line)
            if (notesMatch != null) {
                description = notesMatch.groupValues[2].trim()
                continue
            }

            // Cualquier otra línea dentro de un día es un ejercicio.
            if (currentDayName != null) {
                parseExercise(line)?.let { currentExercises += it }
            }
        }
        closeDay()

        if (name == null && days.isEmpty()) return null

        return ParsedRoutine(
            name = name?.takeIf { it.isNotBlank() } ?: "Rutina importada",
            goal = goal ?: RoutineGoal.HIPERTROFIA,
            description = description,
            days = days,
        )
    }

    /** "Empuje (lunes)" → "Empuje" + 1. Si el paréntesis no es un día, se conserva. */
    private fun splitWeekday(text: String): Pair<String, Int?> {
        val match = Regex("""^(.*?)\s*\(([^)]+)\)\s*$""").find(text) ?: return text to null
        val label = normalizeForSearch(match.groupValues[2])
        val index = weekdayNamesEs.indexOfFirst { normalizeForSearch(it) == label }
        if (index < 0) return text to null
        return match.groupValues[1].trim() to index + 1
    }

    private fun parseGoal(text: String): RoutineGoal? {
        val normalized = normalizeForSearch(text)
        return RoutineGoal.entries.firstOrNull {
            normalizeForSearch(it.labelEs) == normalized || normalizeForSearch(it.name) == normalized
        } ?: when {
            "fuerza" in normalized || "strength" in normalized -> RoutineGoal.FUERZA
            "grasa" in normalized || "definicion" in normalized -> RoutineGoal.PERDIDA_GRASA
            "resistencia" in normalized -> RoutineGoal.RESISTENCIA
            "hipertrofia" in normalized || "volumen" in normalized -> RoutineGoal.HIPERTROFIA
            else -> null
        }
    }

    private fun parseExercise(line: String): ParsedExercise? {
        val clean = line.replace(bulletRegex, "").trim()
        if (clean.isEmpty()) return null

        val separator = separatorRegex.find(clean)
        val name = (separator?.let { clean.substring(0, it.range.first) } ?: nameBeforeNumbers(clean))
            .trim()
            .trim('*', '_', '`')
            .trim()
        if (name.isEmpty()) return null

        val rest = separator?.let { clean.substring(it.range.last + 1) } ?: clean.removePrefix(name)

        // El descanso y el peso se sacan primero para que sus números no se
        // confundan con las series ("descanso 90 s" no son 90 repeticiones).
        val restMatch = restRegex.find(rest)
        val restSeconds = restMatch?.let {
            val value = it.groupValues[1].toIntOrNull() ?: return@let null
            if (it.groupValues[2].lowercase().startsWith("m")) value * 60 else value
        }
        val weight = weightRegex.find(rest)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()

        val withoutRest = restMatch?.let { rest.removeRange(it.range) } ?: rest
        val setsReps = setsRepsRegex.find(withoutRest)

        var sets: Int? = null
        var repsMin: Int? = null
        var repsMax: Int? = null
        var timeSeconds: Int? = null

        if (setsReps != null) {
            sets = setsReps.groupValues[1].toIntOrNull()
            val first = setsReps.groupValues[2].toIntOrNull()
            val second = setsReps.groupValues[3].toIntOrNull()
            val isTime = setsReps.groupValues[4].isNotEmpty()
            if (isTime) {
                timeSeconds = first
            } else {
                repsMin = first
                repsMax = second ?: first
            }
        } else {
            sets = setsOnlyRegex.find(withoutRest)?.groupValues?.get(1)?.toIntOrNull()
            timeSeconds = timeRegex.find(withoutRest)?.groupValues?.get(1)?.toIntOrNull()
        }

        return ParsedExercise(
            name = name,
            sets = sets,
            repsMin = repsMin,
            repsMax = repsMax,
            timeSeconds = timeSeconds,
            restSeconds = restSeconds,
            weightKg = weight,
            notes = null,
        )
    }

    /** Sin separador, el nombre es lo que hay antes del primer "4x8" o "4 series". */
    private fun nameBeforeNumbers(text: String): String {
        val cut = listOfNotNull(
            setsRepsRegex.find(text)?.range?.first,
            setsOnlyRegex.find(text)?.range?.first,
        ).minOrNull() ?: return text
        return text.substring(0, cut)
    }

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
