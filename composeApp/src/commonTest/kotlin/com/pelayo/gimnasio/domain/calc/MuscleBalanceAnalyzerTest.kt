package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuscleBalanceAnalyzerTest {

    private fun primary(sets: Int, groupId: Long) = MuscleVolumeEntry(sets, MuscleRole.PRIMARY, groupId)
    private fun secondary(sets: Int, groupId: Long) = MuscleVolumeEntry(sets, MuscleRole.SECONDARY, groupId)

    @Test
    fun rutina_equilibrada_sin_avisos() {
        val entries = MuscleGroups.all.map { primary(10, it.id) }
        val report = MuscleBalanceAnalyzer.analyze(entries)
        assertTrue(report.isBalanced, "avisos: ${report.warnings}")
        assertTrue(report.scores.all { it.level == CoverageLevel.OK })
    }

    @Test
    fun rutina_sin_piernas_avisa_ausente() {
        val upperOnly = listOf(
            primary(10, MuscleGroups.PECHO.id),
            primary(10, MuscleGroups.ESPALDA.id),
            primary(8, MuscleGroups.HOMBROS.id),
            primary(6, MuscleGroups.BICEPS.id),
            primary(6, MuscleGroups.TRICEPS.id),
            primary(6, MuscleGroups.CORE.id),
        )
        val report = MuscleBalanceAnalyzer.analyze(upperOnly)
        assertFalse(report.isBalanced)
        val absent = report.scores.filter { it.level == CoverageLevel.AUSENTE }.map { it.group.name }
        assertEquals(listOf("Cuádriceps", "Isquios", "Glúteos", "Gemelos"), absent)
        assertTrue(report.warnings.any { it.message.contains("Cuádriceps") })
    }

    @Test
    fun secundario_puntua_la_mitad() {
        val entries = listOf(secondary(4, MuscleGroups.BICEPS.id))
        val report = MuscleBalanceAnalyzer.analyze(entries)
        val biceps = report.scores.first { it.group.id == MuscleGroups.BICEPS.id }
        assertEquals(2.0, biceps.effectiveSets)
        assertEquals(CoverageLevel.BAJO, biceps.level)
    }

    @Test
    fun mucho_empuje_poco_tiron_avisa() {
        val entries = listOf(
            primary(12, MuscleGroups.PECHO.id),
            primary(10, MuscleGroups.HOMBROS.id),
            primary(8, MuscleGroups.TRICEPS.id),
            primary(6, MuscleGroups.ESPALDA.id),
            primary(8, MuscleGroups.CUADRICEPS.id),
            primary(8, MuscleGroups.ISQUIOS.id),
            primary(8, MuscleGroups.GLUTEOS.id),
            primary(6, MuscleGroups.GEMELOS.id),
            primary(6, MuscleGroups.CORE.id),
            primary(6, MuscleGroups.BICEPS.id),
        )
        // push = 30, pull = 12 → ratio 2.5 > 1.5
        val report = MuscleBalanceAnalyzer.analyze(entries)
        assertTrue(report.warnings.any { it.message.contains("empuje") })
    }

    @Test
    fun volumen_excesivo_es_info_no_aviso() {
        val entries = MuscleGroups.all.map { primary(10, it.id) } + primary(15, MuscleGroups.PECHO.id)
        val report = MuscleBalanceAnalyzer.analyze(entries)
        val pecho = report.scores.first { it.group.id == MuscleGroups.PECHO.id }
        assertEquals(CoverageLevel.EXCESIVO, pecho.level)
        assertTrue(report.warnings.filter { it.message.contains("Pecho") }.all { it.severity == WarningSeverity.INFO })
    }

    @Test
    fun rutina_vacia_todo_ausente() {
        val report = MuscleBalanceAnalyzer.analyze(emptyList())
        assertTrue(report.scores.all { it.level == CoverageLevel.AUSENTE })
        assertFalse(report.isBalanced)
    }
}
