package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineAdvisorTest {

    private var nextId = 1L

    private fun exercise(
        name: String,
        primary: Long,
        secondary: List<Long> = emptyList(),
    ) = CandidateExercise(
        id = nextId++,
        name = name,
        primaryGroupIds = setOf(primary),
        secondaryGroupIds = secondary.toSet(),
        equipmentIds = emptySet(),
    )

    /** Un ejercicio por grupo, más uno compuesto para los grupos grandes. */
    private fun catalog(): List<CandidateExercise> {
        nextId = 1L
        return MuscleGroups.all.flatMap { group ->
            listOf(
                exercise(
                    "${group.name} compuesto",
                    group.id,
                    secondary = MuscleGroups.all.filter { it.id != group.id }.take(2).map { it.id },
                ),
                exercise("${group.name} aislamiento", group.id),
            )
        }
    }

    private fun refs(vararg items: Pair<CandidateExercise, String>): List<RoutineExerciseRef> =
        items.mapIndexed { index, (exercise, dayName) ->
            RoutineExerciseRef(
                routineExerciseId = 100L + index,
                exerciseId = exercise.id,
                name = exercise.name,
                dayId = if (dayName == "Empuje") 1L else 2L,
                dayName = dayName,
            )
        }

    private fun report(vararg volume: Pair<Long, Int>): BalanceReport =
        MuscleBalanceAnalyzer.analyze(
            volume.map { (groupId, sets) -> MuscleVolumeEntry(sets, MuscleRole.PRIMARY, groupId) },
        )

    @Test
    fun propone_anadir_un_ejercicio_para_el_grupo_que_falta() {
        val catalog = catalog()
        val pecho = catalog.first { it.name == "Pecho compuesto" }
        val report = report(MuscleGroups.PECHO.id to 12)

        val suggestions = RoutineAdvisor.suggest(
            report = report,
            routineExercises = refs(pecho to "Empuje"),
            catalog = catalog,
        )

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.kind == SuggestionKind.ANADIR })
        // Todas apuntan a un ejercicio real del catálogo y a un día existente.
        assertTrue(suggestions.all { s -> catalog.any { it.id == s.exerciseId } })
        assertTrue(suggestions.all { it.dayId == 1L })
    }

    @Test
    fun nunca_propone_un_ejercicio_que_ya_esta_en_la_rutina() {
        val catalog = catalog()
        val pecho = catalog.first { it.name == "Pecho compuesto" }
        val espalda = catalog.first { it.name == "Espalda compuesto" }

        val suggestions = RoutineAdvisor.suggest(
            report = report(MuscleGroups.PECHO.id to 12, MuscleGroups.ESPALDA.id to 4),
            routineExercises = refs(pecho to "Empuje", espalda to "Tirón"),
            catalog = catalog,
        )

        val yaEstan = setOf(pecho.id, espalda.id)
        assertTrue(suggestions.none { it.exerciseId in yaEstan }, "repite un ejercicio ya presente")
    }

    @Test
    fun no_repite_grupo_entre_sugerencias() {
        val suggestions = RoutineAdvisor.suggest(
            report = report(MuscleGroups.PECHO.id to 12),
            routineExercises = refs(catalog().first() to "Empuje"),
            catalog = catalog(),
        )

        val mencionados = suggestions.map { it.message.substringBefore(":") }
        assertEquals(mencionados.size, mencionados.distinct().size, "dos consejos para lo mismo")
    }

    @Test
    fun propone_cambiar_cuando_sobra_un_grupo_y_falta_otro() {
        val catalog = catalog()
        val pechoAislamiento = catalog.first { it.name == "Pecho aislamiento" }

        // 30 series de pecho es volumen excesivo; el resto se queda a cero.
        val suggestions = RoutineAdvisor.suggest(
            report = report(MuscleGroups.PECHO.id to 30),
            routineExercises = refs(pechoAislamiento to "Empuje"),
            catalog = catalog,
        )

        val cambio = suggestions.firstOrNull { it.kind == SuggestionKind.CAMBIAR }
        assertTrue(cambio != null, "no ha propuesto ningún cambio: $suggestions")
        assertEquals(pechoAislamiento.name, cambio.replacesName)
        assertEquals(100L, cambio.replacesRoutineExerciseId)
        assertTrue("Pecho" in cambio.message && "cambia" in cambio.message)
    }

    @Test
    fun no_sacrifica_un_ejercicio_compuesto() {
        val catalog = catalog()
        val pechoCompuesto = catalog.first { it.name == "Pecho compuesto" }

        val suggestions = RoutineAdvisor.suggest(
            report = report(MuscleGroups.PECHO.id to 30),
            routineExercises = refs(pechoCompuesto to "Empuje"),
            catalog = catalog,
        )

        assertTrue(
            suggestions.none { it.replacesRoutineExerciseId != null },
            "ha propuesto quitar un compuesto",
        )
    }

    @Test
    fun no_pasa_de_cuatro_sugerencias() {
        val catalog = catalog()
        val suggestions = RoutineAdvisor.suggest(
            report = report(MuscleGroups.PECHO.id to 12),
            routineExercises = refs(catalog.first() to "Empuje"),
            catalog = catalog,
        )

        assertTrue(suggestions.size <= RoutineAdvisor.MAX_SUGGESTIONS, "salen ${suggestions.size}")
    }

    @Test
    fun sin_catalogo_o_sin_rutina_no_sugiere_nada() {
        val vacio = RoutineAdvisor.suggest(report(), emptyList(), catalog())
        assertTrue(vacio.isEmpty())

        val sinCatalogo = RoutineAdvisor.suggest(
            report = report(MuscleGroups.PECHO.id to 12),
            routineExercises = refs(catalog().first() to "Empuje"),
            catalog = emptyList(),
        )
        assertTrue(sinCatalogo.isEmpty())
    }

    @Test
    fun una_rutina_equilibrada_no_genera_consejos() {
        val catalog = catalog()
        val volumen = MuscleGroups.all.map { it.id to 12 }.toTypedArray()

        val suggestions = RoutineAdvisor.suggest(
            report = report(*volumen),
            routineExercises = refs(catalog.first() to "Empuje"),
            catalog = catalog,
        )

        assertTrue(suggestions.isEmpty(), "sugiere cosas sobre una rutina equilibrada: $suggestions")
    }
}
