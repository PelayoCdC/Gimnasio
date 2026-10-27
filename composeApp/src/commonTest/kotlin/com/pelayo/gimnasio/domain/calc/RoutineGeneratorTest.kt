package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExperienceLevel
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

    /**
     * Catálogo con pinta de gimnasio de verdad: nombres reales, músculos
     * secundarios donde los hay y un par de actividades que no son de sala. El
     * catálogo sintético de arriba tiene un compuesto perfecto por grupo y hace que
     * cualquier reparto salga bien, así que no sirve para probar el equilibrio.
     */
    private fun gymCatalog(): List<CandidateExercise> {
        nextId = 1L
        val G = MuscleGroups
        return listOf(
            exercise("Press de banca con barra", G.PECHO.id, listOf(G.TRICEPS.id, G.HOMBROS.id), setOf(1L, 8L)),
            exercise("Press inclinado con mancuernas", G.PECHO.id, listOf(G.TRICEPS.id, G.HOMBROS.id), setOf(3L, 9L)),
            exercise("Press de pecho en maquina", G.PECHO.id, listOf(G.TRICEPS.id)),
            exercise("Cruces en polea", G.PECHO.id),
            exercise("Aperturas con mancuernas", G.PECHO.id, equipment = setOf(3L, 8L)),
            exercise("Jalon al pecho", G.ESPALDA.id, listOf(G.BICEPS.id)),
            exercise("Remo sentado en polea", G.ESPALDA.id, listOf(G.BICEPS.id)),
            exercise("Remo con barra", G.ESPALDA.id, listOf(G.BICEPS.id), setOf(1L)),
            exercise("Remo con mancuerna", G.ESPALDA.id, listOf(G.BICEPS.id), setOf(3L)),
            exercise("Pullover en polea", G.ESPALDA.id),
            exercise("Press militar en maquina", G.HOMBROS.id, listOf(G.TRICEPS.id)),
            exercise("Press de hombros con mancuernas", G.HOMBROS.id, listOf(G.TRICEPS.id), setOf(3L)),
            exercise("Elevaciones laterales", G.HOMBROS.id, equipment = setOf(3L)),
            exercise("Face pull en polea", G.HOMBROS.id, listOf(G.ESPALDA.id)),
            exercise("Curl con barra Z", G.BICEPS.id, equipment = setOf(2L)),
            exercise("Curl con mancuernas", G.BICEPS.id, equipment = setOf(3L)),
            exercise("Curl en polea", G.BICEPS.id),
            exercise("Pushdown en polea", G.TRICEPS.id),
            exercise("Extension de triceps con mancuerna", G.TRICEPS.id, equipment = setOf(3L)),
            exercise("Fondos en maquina", G.TRICEPS.id, listOf(G.PECHO.id)),
            exercise(
                "Sentadilla con barra",
                G.CUADRICEPS.id,
                listOf(G.GLUTEOS.id, G.ISQUIOS.id, G.CORE.id),
                setOf(1L),
            ),
            exercise("Prensa de piernas", G.CUADRICEPS.id, listOf(G.GLUTEOS.id)),
            exercise("Extension de cuadriceps en maquina", G.CUADRICEPS.id),
            exercise("Zancadas con mancuernas", G.CUADRICEPS.id, listOf(G.GLUTEOS.id), setOf(3L)),
            exercise("Curl femoral tumbado", G.ISQUIOS.id),
            exercise(
                "Peso muerto rumano con mancuernas",
                G.ISQUIOS.id,
                listOf(G.GLUTEOS.id, G.ESPALDA.id),
                setOf(3L),
            ),
            exercise("Curl femoral sentado", G.ISQUIOS.id),
            exercise("Hip thrust en maquina", G.GLUTEOS.id, listOf(G.ISQUIOS.id)),
            exercise("Patada de gluteo en polea", G.GLUTEOS.id),
            exercise("Puente de gluteos", G.GLUTEOS.id, listOf(G.ISQUIOS.id)),
            exercise("Elevacion de gemelos sentado", G.GEMELOS.id),
            exercise("Elevacion de gemelos de pie en maquina", G.GEMELOS.id),
            exercise("Crunch en polea", G.CORE.id),
            exercise("Plancha", G.CORE.id),
            exercise("Elevacion de piernas colgado", G.CORE.id, equipment = setOf(6L)),
        )
    }

    private fun cardio(name: String, primary: Long) = CandidateExercise(
        id = nextId++,
        name = name,
        primaryGroupIds = setOf(primary),
        secondaryGroupIds = emptySet(),
        equipmentIds = emptySet(),
        categoryId = GymExerciseFilter.CARDIO_CATEGORY_ID,
    )

    @Test
    fun con_tres_dias_y_sesiones_normales_la_rutina_nace_sin_avisos() {
        // Es lo que se pedía: que la propuesta no llegue con el aviso ya puesto.
        val catalog = gymCatalog()
        for (level in ExperienceLevel.entries) {
            for (minutes in listOf(60, 75, 90)) {
                for (days in 3..6) {
                    val routine = RoutineGenerator.generate(
                        daysPerWeek = days,
                        goal = RoutineGoal.HIPERTROFIA,
                        catalog = catalog,
                        level = level,
                        minutesPerSession = minutes,
                    )
                    val report = MuscleBalanceAnalyzer.analyze(routine.volumeEntries(catalog))
                    assertTrue(
                        report.isBalanced,
                        "$level, $days días de $minutes min: ${report.warnings.map { it.message }}",
                    )
                }
            }
        }
    }

    @Test
    fun el_reequilibrado_no_toca_el_compuesto_con_el_que_abre_cada_dia() {
        val catalog = gymCatalog()
        val routine = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            minutesPerSession = 60,
        )
        val byId = catalog.associateBy { it.id }

        // Cada día abre con el grupo que le da nombre: el reequilibrado cambia el
        // relleno, no el ejercicio sobre el que se monta la sesión.
        val esperado = mapOf(
            "Empuje" to MuscleGroups.PECHO.id,
            "Tirón" to MuscleGroups.ESPALDA.id,
            "Pierna" to MuscleGroups.CUADRICEPS.id,
        )
        routine.days.forEach { day ->
            val first = byId.getValue(day.exerciseIds.first())
            assertTrue(
                esperado.getValue(day.name) in first.primaryGroupIds,
                "el día ${day.name} abre con ${first.name}",
            )
        }
    }

    @Test
    fun no_propone_ejercicios_que_no_son_de_gimnasio() {
        nextId = 1L
        val catalog = gymCatalog() + listOf(
            cardio("Nadar a crol", MuscleGroups.ESPALDA.id),
            cardio("Correr en cinta", MuscleGroups.CUADRICEPS.id),
        ) + exercise("Saltar a la comba", MuscleGroups.GEMELOS.id)

        val routine = RoutineGenerator.generate(6, RoutineGoal.HIPERTROFIA, catalog)

        val byId = catalog.associateBy { it.id }
        val names = routine.days.flatMap { it.exerciseIds }.map { byId.getValue(it).name }
        assertTrue(names.none { it.startsWith("Nadar") }, "ha metido natación: $names")
        assertTrue(names.none { it.startsWith("Correr") }, "ha metido carrera: $names")
        assertTrue(names.none { it.startsWith("Saltar") }, "ha metido comba: $names")
    }

    @Test
    fun prefiere_maquina_y_polea_antes_que_la_barra() {
        nextId = 1L
        // Mismo grupo y mismo carácter: solo cambia el material.
        val barra = exercise("Remo con barra", MuscleGroups.ESPALDA.id, equipment = setOf(1L))
        val polea = exercise("Remo sentado en polea", MuscleGroups.ESPALDA.id)
        val routine = RoutineGenerator.generate(
            daysPerWeek = 1,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = listOf(barra, polea),
        )

        val elegidos = routine.days.single().exerciseIds
        assertEquals(polea.id, elegidos.first(), "debería abrir con el de polea")
    }

    @Test
    fun las_series_elegidas_mandan_sobre_las_del_objetivo() {
        val catalog = fullCatalog()
        val automatica = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, catalog)
        assertEquals(4, automatica.prescription.sets, "hipertrofia son 4 series por defecto")

        val cinco = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            setsPerExercise = 5,
        )
        assertEquals(5, cinco.prescription.sets)
        // El resto de la prescripción no se toca.
        assertEquals(automatica.prescription.repsMin, cinco.prescription.repsMin)
        assertEquals(automatica.prescription.restSeconds, cinco.prescription.restSeconds)
    }

    @Test
    fun con_mas_series_caben_menos_ejercicios_en_la_misma_sesion() {
        val catalog = fullCatalog()
        val dos = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            minutesPerSession = 60,
            setsPerExercise = 2,
        )
        val seis = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            minutesPerSession = 60,
            setsPerExercise = 6,
        )

        assertTrue(
            dos.days.first().exerciseIds.size > seis.days.first().exerciseIds.size,
            "con 2 series salieron ${dos.days.first().exerciseIds.size} ejercicios y " +
                "con 6 salieron ${seis.days.first().exerciseIds.size}",
        )
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
    fun con_menos_tiempo_propone_menos_ejercicios() {
        val catalog = fullCatalog()
        val corta = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            minutesPerSession = 30,
        )
        val larga = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            minutesPerSession = 90,
        )

        val ejerciciosCortos = corta.days.first().exerciseIds.size
        val ejerciciosLargos = larga.days.first().exerciseIds.size
        assertTrue(
            ejerciciosCortos < ejerciciosLargos,
            "30 min ha dado $ejerciciosCortos ejercicios y 90 min $ejerciciosLargos",
        )
    }

    @Test
    fun la_duracion_estimada_se_acerca_al_tiempo_pedido() {
        val catalog = fullCatalog()
        listOf(30, 45, 60, 90).forEach { minutos ->
            val routine = RoutineGenerator.generate(
                daysPerWeek = 3,
                goal = RoutineGoal.HIPERTROFIA,
                catalog = catalog,
                minutesPerSession = minutos,
            )
            val estimado = routine.days.first().estimatedMinutes
            assertTrue(
                estimado <= minutos + 10,
                "pedidos $minutos min y la sesión sale de $estimado min",
            )
        }
    }

    @Test
    fun un_descanso_largo_deja_menos_hueco_para_ejercicios() {
        val catalog = fullCatalog()
        // Mismo tiempo, pero fuerza descansa 180 s entre series e hipertrofia 90 s.
        val fuerza = RoutineGenerator.generate(3, RoutineGoal.FUERZA, catalog, minutesPerSession = 60)
        val hipertrofia = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, catalog, minutesPerSession = 60)

        assertTrue(
            fuerza.days.first().exerciseIds.size < hipertrofia.days.first().exerciseIds.size,
            "fuerza: ${fuerza.days.first().exerciseIds.size}, " +
                "hipertrofia: ${hipertrofia.days.first().exerciseIds.size}",
        )
    }

    @Test
    fun a_un_principiante_no_le_propone_ejercicios_avanzados() {
        // El catálogo base se crea primero para que los ids no colisionen.
        val base = fullCatalog()
        val avanzado = exercise("Cargada de fuerza", MuscleGroups.CUADRICEPS.id, equipment = setOf(1L))
        val catalog = base + avanzado

        val routine = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            level = ExperienceLevel.PRINCIPIANTE,
        )

        val elegidos = routine.days.flatMap { it.exerciseIds }
        assertTrue(avanzado.id !in elegidos, "ha propuesto una cargada a un principiante")
        assertTrue(routine.excludedForLevel > 0)
    }

    @Test
    fun entre_dos_opciones_el_principiante_recibe_la_mas_sencilla() {
        nextId = 1L
        // El técnico va primero para descartar que solo esté respetando el orden de id.
        val tecnico = exercise("Sentadilla con barra", MuscleGroups.CUADRICEPS.id, equipment = setOf(1L))
        val basico = exercise("Prensa de piernas", MuscleGroups.CUADRICEPS.id)

        val routine = RoutineGenerator.generate(
            daysPerWeek = 1,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = listOf(tecnico, basico),
            level = ExperienceLevel.PRINCIPIANTE,
        )

        assertEquals(basico.id, routine.days.single().exerciseIds.first())
    }

    @Test
    fun el_avanzado_si_puede_recibir_ejercicios_tecnicos() {
        nextId = 1L
        val avanzado = exercise("Cargada de fuerza", MuscleGroups.CUADRICEPS.id, equipment = setOf(1L))

        val routine = RoutineGenerator.generate(
            daysPerWeek = 1,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = listOf(avanzado),
            level = ExperienceLevel.AVANZADO,
        )

        assertTrue(avanzado.id in routine.days.single().exerciseIds)
        assertEquals(0, routine.excludedForLevel)
    }

    @Test
    fun el_principiante_entrena_a_cuerpo_completo_en_vez_de_dividir_por_patron() {
        val catalog = fullCatalog()
        val routine = RoutineGenerator.generate(
            daysPerWeek = 3,
            goal = RoutineGoal.HIPERTROFIA,
            catalog = catalog,
            level = ExperienceLevel.PRINCIPIANTE,
        )

        assertEquals(
            listOf("Cuerpo completo A", "Cuerpo completo B", "Cuerpo completo C"),
            routine.days.map { it.name },
        )
        assertTrue(routine.name.startsWith("Cuerpo completo"))
    }

    @Test
    fun el_principiante_hace_menos_series_y_repeticiones_mas_seguras() {
        val catalog = fullCatalog()
        val principiante = RoutineGenerator.generate(
            3, RoutineGoal.FUERZA, catalog, level = ExperienceLevel.PRINCIPIANTE,
        ).prescription
        assertEquals(3, principiante.sets)
        assertEquals(5, principiante.repsMin)

        val avanzado = RoutineGenerator.generate(
            3, RoutineGoal.HIPERTROFIA, catalog, level = ExperienceLevel.AVANZADO,
        ).prescription
        assertEquals(5, avanzado.sets)
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

    @Test
    fun sin_el_chip_de_maquinas_no_entran_prensas_ni_jalones() {
        // Máquinas y poleas llegan de wger sin equipamiento: solo el nombre las
        // delata, y a quien entrena en casa con mancuernas no se le proponen.
        val catalog = fullCatalog(equipment = setOf(3L)) +
            exercise("Prensa de piernas", MuscleGroups.CUADRICEPS.id) +
            exercise("Jalón al pecho", MuscleGroups.ESPALDA.id)

        val home = RoutineGenerator.generate(
            3, RoutineGoal.HIPERTROFIA, catalog,
            availableEquipmentIds = setOf(3L),
        )
        val byId = catalog.associateBy { it.id }
        val chosen = home.days.flatMap { it.exerciseIds }.map { byId.getValue(it).name }
        assertTrue(chosen.none { it == "Prensa de piernas" || it == "Jalón al pecho" }, "$chosen")

        // Con el chip sintético de máquinas la prensa vuelve a estar disponible.
        val gym = RoutineGenerator.generate(
            3, RoutineGoal.HIPERTROFIA, catalog,
            availableEquipmentIds = setOf(3L, RoutineGenerator.MACHINE_EQUIPMENT_ID),
        )
        val gymChosen = gym.days.flatMap { it.exerciseIds }.map { byId.getValue(it).name }
        assertTrue("Prensa de piernas" in gymChosen, "$gymChosen")
    }

    @Test
    fun el_material_de_un_ejercicio_son_alternativas_no_un_conjunto() {
        // Un press con {barra, banco} vale con cualquiera de los dos: exigir
        // ambos vaciaba el catálogo de quien solo marcó banco.
        val press = exercise("Press en banco", MuscleGroups.PECHO.id, equipment = setOf(1L, 8L))
        val catalog = fullCatalog(equipment = setOf(8L)) + press

        val routine = RoutineGenerator.generate(
            3, RoutineGoal.HIPERTROFIA, catalog,
            availableEquipmentIds = setOf(8L),
        )
        val allIds = routine.days.flatMap { it.exerciseIds }
        // No se garantiza que lo elija, pero sí que no está excluido: lo probamos
        // pidiendo un catálogo donde es la única opción de pecho.
        val onlyPress = listOf(press) + fullCatalog(equipment = setOf(8L))
            .filter { MuscleGroups.PECHO.id !in it.primaryGroupIds }
        val forced = RoutineGenerator.generate(
            3, RoutineGoal.HIPERTROFIA, onlyPress,
            availableEquipmentIds = setOf(8L),
        )
        assertTrue(
            forced.days.flatMap { it.exerciseIds }.contains(press.id),
            "el press con {barra, banco} debe valer con solo banco",
        )
        assertTrue(allIds.isNotEmpty())
    }

    @Test
    fun los_dias_generados_traen_dia_de_la_semana_propuesto() {
        val routine = RoutineGenerator.generate(3, RoutineGoal.HIPERTROFIA, fullCatalog())
        // 3 días → lunes, miércoles y viernes.
        assertEquals(listOf(1, 3, 5), routine.days.map { it.weekday })
    }
}
