package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.TrainingPrescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineGeneratorTest {

    private var nextId = 1L

    private fun exercise(
        name: String,
        primary: Long,
        secondary: List<Long> = emptyList(),
        equipment: Set<Long> = emptySet(),
    ) = CandidateExercise(
        id = nextId++,
        name = name,
        primaryGroupIds = setOf(primary),
        secondaryGroupIds = secondary.toSet(),
        equipmentIds = equipment,
    )

    /** Catálogo con al menos tres opciones por grupo, compuestas y de aislamiento. */
    private fun fullCatalog(equipment: Set<Long> = emptySet()): List<CandidateExercise> {
        nextId = 1L
        return MuscleGroups.all.flatMap { group ->
            listOf(
                exercise(
                    "${group.name} compuesto",
                    group.id,
                    secondary = MuscleGroups.all.filter { it.id != group.id }.take(2).map { it.id },
                    equipment = equipment,
                ),
                exercise("${group.name} aislamiento 1", group.id, equipment = equipment),
                exercise("${group.name} aislamiento 2", group.id, equipment = equipment),
                exercise("${group.name} aislamiento 3", group.id, equipment = equipment),
            )
        }
    }

    private fun GeneratedRoutine.volumeEntries(catalog: List<CandidateExercise>): List<MuscleVolumeEntry> {
        val byId = catalog.associateBy { it.id }
        val sets = TrainingPrescription.forGoal(goal).sets
        return days.flatMap { day ->
            day.exerciseIds.flatMap { id ->
                val e = byId.getValue(id)
                e.primaryGroupIds.map { MuscleVolumeEntry(sets, MuscleRole.PRIMARY, it) } +
                    e.secondaryGroupIds.map { MuscleVolumeEntry(sets, MuscleRole.SECONDARY, it) }
            }
        }
    }

    @Test
    fun tres_dias_genera_empuje_tiron_pierna() {
        val routine = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, fullCatalog())
        assertEquals(listOf("Empuje", "Tirón", "Pierna"), routine.days.map { it.name })
    }

    @Test
    fun el_numero_de_dias_coincide_con_lo_pedido() {
        val catalog = fullCatalog()
        (1..6).forEach { days ->
            assertEquals(
                days,
                RoutineGenerator.generate(days, RoutineGoal.HIPERTROFIA, catalog).days.size,
                "fallo con $days días",
            )
        }
    }

    @Test
    fun no_repite_ejercicios_en_toda_la_semana() {
        val catalog = fullCatalog()
        val routine = RoutineGenerator.generate(6, RoutineGoal.HIPERTROFIA, catalog)
        val all = routine.days.flatMap { it.exerciseIds }
        assertEquals(all.size, all.distinct().size, "hay ejercicios repetidos: $all")
    }

    @Test
    fun una_rutina_de_tres_dias_no_deja_grupos_ausentes() {
        val catalog = fullCatalog()
        val routine = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, catalog)

        val report = MuscleBalanceAnalyzer.analyze(routine.volumeEntries(catalog))
        val ausentes = report.scores.filter { it.level == CoverageLevel.AUSENTE }.map { it.group.name }
        assertTrue(ausentes.isEmpty(), "grupos sin entrenar: $ausentes")
    }

    @Test
    fun respeta_el_material_disponible() {
        // Catálogo entero con mancuernas (id 3) salvo un ejercicio con barra (id 1).
        // El de barra se crea después para que no colisionen los ids del contador.
        val dumbbellOnly = fullCatalog(equipment = setOf(3L))
        val withBarbell = exercise("Sentadilla con barra", MuscleGroups.CUADRICEPS.id, equipment = setOf(1L))
        val catalog = listOf(withBarbell) + dumbbellOnly

        val routine = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            availableEquipmentIds = setOf(3L),
        )

        assertTrue(
            routine.days.flatMap { it.exerciseIds }.none { it == withBarbell.id },
            "ha elegido un ejercicio que necesita barra",
        )
    }

    @Test
    fun los_ejercicios_sin_material_valen_para_cualquier_equipamiento() {
        nextId = 1L
        val bodyweight = exercise("Flexiones", MuscleGroups.PECHO.id, equipment = emptySet())
        val routine = RoutineGenerator.generate(
            daysPerWeek = 1,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = listOf(bodyweight),
            availableEquipmentIds = setOf(99L),
        )
        assertTrue(bodyweight.id in routine.days.single().exerciseIds)
    }

    @Test
    fun avisa_de_los_grupos_que_no_puede_cubrir() {
        nextId = 1L
        // Catálogo solo de pecho: el resto de grupos no tiene con qué cubrirse.
        val catalog = listOf(exercise("Press", MuscleGroups.PECHO.id))
        val routine = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, catalog)

        assertTrue(routine.uncoveredGroups.isNotEmpty())
        assertTrue("Espalda" in routine.uncoveredGroups, "obtenido: ${routine.uncoveredGroups}")
        assertTrue("Pecho" !in routine.uncoveredGroups)
    }

    @Test
    fun la_prescripcion_depende_del_objetivo() {
        val catalog = fullCatalog()
        val fuerza = RoutineGenerator.generate(3, RoutineGoal.FUERZA, catalog).prescription
        assertEquals(4, fuerza.sets)
        assertEquals(3, fuerza.repsMin)
        assertEquals(180, fuerza.restSeconds)

        val resistencia = RoutineGenerator.generate(3, RoutineGoal.RESISTENCIA, catalog).prescription
        assertEquals(20, resistencia.repsMax)
        assertEquals(60, resistencia.restSeconds)
    }

    @Test
    fun el_nombre_por_defecto_describe_la_division() {
        val catalog = fullCatalog()
        assertEquals(
            "Empuje/Tirón/Pierna · 3 días · hipertrofia",
            RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, catalog).name,
        )
        assertEquals(
            "Torso/Pierna · 4 días · fuerza",
            RoutineGenerator.generate(4, RoutineGoal.FUERZA, catalog).name,
        )
    }

    @Test
    fun un_catalogo_vacio_no_revienta() {
        val routine = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, emptyList())
        assertEquals(3, routine.days.size)
        assertTrue(routine.days.all { it.exerciseIds.isEmpty() })
        assertTrue(routine.uncoveredGroups.isNotEmpty())
    }

    @Test
    fun prefiere_compuestos_en_los_huecos_que_lo_piden() {
        val catalog = fullCatalog()
        val routine = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, catalog)
        val byId = catalog.associateBy { it.id }

        // El primer ejercicio del día de empuje es el slot de pecho compuesto.
        val firstPush = routine.days.first { it.name == "Empuje" }.exerciseIds.first()
        assertTrue(byId.getValue(firstPush).isCompound, "el primer ejercicio debería ser compuesto")
    }
}
