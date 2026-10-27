package com.pelayo.gimnasio.data.backup

import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.RoutineDayInfo
import com.pelayo.gimnasio.domain.model.RoutineDetail
import com.pelayo.gimnasio.domain.model.RoutineExerciseItem
import com.pelayo.gimnasio.domain.model.RoutineGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineMarkdownTest {

    private fun exercise(
        name: String,
        sets: Int = 4,
        repsMin: Int? = 8,
        repsMax: Int? = 10,
        timeSeconds: Int? = null,
        weight: Double? = null,
        rest: Int = 90,
        notes: String? = null,
    ) = RoutineExerciseItem(
        id = 1,
        exerciseId = 1,
        exerciseName = name,
        imageUrl = null,
        exerciseType = if (timeSeconds != null) ExerciseType.TIME else ExerciseType.REPS,
        metValue = 3.5,
        position = 0,
        targetSets = sets,
        targetRepsMin = repsMin,
        targetRepsMax = repsMax,
        targetTimeSeconds = timeSeconds,
        targetWeight = weight,
        restSeconds = rest,
        notes = notes,
    )

    private fun routine() = RoutineDetail(
        id = 1,
        name = "Torso/Pierna · 4 días",
        description = "Intermedio · sesiones de unos 60 min",
        goal = RoutineGoal.HIPERTROFIA,
        isActive = true,
        days = listOf(
            RoutineDayInfo(
                id = 1,
                routineId = 1,
                dayIndex = 0,
                name = "Torso A",
                weekday = 1,
                exercises = listOf(
                    exercise("Press de banca con barra", weight = 60.0),
                    exercise("Plancha", sets = 3, repsMin = null, repsMax = null, timeSeconds = 45, rest = 60),
                ),
            ),
            RoutineDayInfo(
                id = 2,
                routineId = 1,
                dayIndex = 1,
                name = "Pierna A",
                weekday = null,
                exercises = listOf(exercise("Prensa de piernas", notes = "Sin bloquear la rodilla")),
            ),
        ),
    )

    @Test
    fun exporta_algo_que_se_entiende_leyendolo() {
        val text = RoutineMarkdown.export(routine())

        assertTrue(text.startsWith("# Torso/Pierna · 4 días"), text)
        assertTrue("Objetivo: Hipertrofia" in text, text)
        assertTrue("## Torso A (lunes)" in text, text)
        assertTrue("1. Press de banca con barra — 4 × 8-10 reps — descanso 90 s — 60 kg" in text, text)
        assertTrue("2. Plancha — 3 × 45 s — descanso 60 s" in text, text)
        assertTrue("## Pierna A" in text && "## Pierna A (" !in text, "el día sin weekday no lo inventa")
        assertTrue("Nota: Sin bloquear la rodilla" in text, text)
    }

    @Test
    fun lo_exportado_se_vuelve_a_leer_igual() {
        val original = routine()
        val parsed = assertNotNull(RoutineMarkdown.parse(RoutineMarkdown.export(original)))

        assertEquals(original.name, parsed.name)
        assertEquals(RoutineGoal.HIPERTROFIA, parsed.goal)
        assertEquals(listOf("Torso A", "Pierna A"), parsed.days.map { it.name })
        assertEquals(1, parsed.days.first().weekday)
        assertNull(parsed.days.last().weekday)

        val press = parsed.days.first().exercises.first()
        assertEquals("Press de banca con barra", press.name)
        assertEquals(4, press.sets)
        assertEquals(8, press.repsMin)
        assertEquals(10, press.repsMax)
        assertEquals(90, press.restSeconds)
        assertEquals(60.0, press.weightKg)

        val plancha = parsed.days.first().exercises[1]
        assertEquals(45, plancha.timeSeconds)
        assertEquals(3, plancha.sets)
        assertEquals(60, plancha.restSeconds)

        assertEquals("Sin bloquear la rodilla", parsed.days.last().exercises.single().notes)
    }

    @Test
    fun lee_lo_que_escribiria_una_persona_o_un_agente() {
        // Viñetas en vez de números, "x" en vez de "×", minutos de descanso y
        // ejercicios sin ningún número.
        val text = """
        # Mi rutina de fuerza

        Objetivo: fuerza

        ## Lunes (lunes)
        - Sentadilla con barra 5x5 descanso 3 min
        * Press militar — 3 series x 8-12 reps
        1) Dominadas

        ## Jueves
        - Peso muerto: 4x6 — 100 kg
        """.trimIndent()

        val parsed = assertNotNull(RoutineMarkdown.parse(text))

        assertEquals("Mi rutina de fuerza", parsed.name)
        assertEquals(RoutineGoal.FUERZA, parsed.goal)
        assertEquals(2, parsed.days.size)
        assertEquals(1, parsed.days.first().weekday)

        val sentadilla = parsed.days.first().exercises[0]
        assertEquals("Sentadilla con barra", sentadilla.name)
        assertEquals(5, sentadilla.sets)
        assertEquals(5, sentadilla.repsMin)
        assertEquals(180, sentadilla.restSeconds, "3 min son 180 s")

        val militar = parsed.days.first().exercises[1]
        assertEquals("Press militar", militar.name)
        assertEquals(3, militar.sets)
        assertEquals(12, militar.repsMax)

        val dominadas = parsed.days.first().exercises[2]
        assertEquals("Dominadas", dominadas.name)
        assertNull(dominadas.sets, "sin números, ya los pone el objetivo al importar")

        val muerto = parsed.days.last().exercises.single()
        assertEquals("Peso muerto", muerto.name)
        assertEquals(100.0, muerto.weightKg)
        assertEquals(6, muerto.repsMin)
    }

    @Test
    fun el_descanso_no_se_confunde_con_las_repeticiones() {
        val parsed = assertNotNull(
            RoutineMarkdown.parse("# R\n## Día\n- Curl — descanso 60 s — 3 × 12 reps"),
        )
        val curl = parsed.days.single().exercises.single()
        assertEquals(3, curl.sets)
        assertEquals(12, curl.repsMin)
        assertEquals(60, curl.restSeconds)
    }

    @Test
    fun un_texto_que_no_es_una_rutina_devuelve_null() {
        assertNull(RoutineMarkdown.parse("Lista de la compra\n- leche\n- pan"))
    }

    @Test
    fun sin_objetivo_se_asume_hipertrofia_y_sin_nombre_se_pone_uno() {
        val parsed = assertNotNull(RoutineMarkdown.parse("## Día 1\n- Press de banca 4x10"))
        assertEquals("Rutina importada", parsed.name)
        assertEquals(RoutineGoal.HIPERTROFIA, parsed.goal)
        assertEquals(1, parsed.exerciseCount)
    }
}
