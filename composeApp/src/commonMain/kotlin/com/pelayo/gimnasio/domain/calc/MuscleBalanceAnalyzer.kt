package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.ExperienceLevel
import com.pelayo.gimnasio.domain.model.MuscleGroupCategory
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole

/** Una fila de volumen: sets objetivo de un ejercicio que tocan un grupo muscular. */
data class MuscleVolumeEntry(
    val sets: Int,
    val role: MuscleRole,
    val muscleGroupId: Long,
)

enum class CoverageLevel { AUSENTE, BAJO, OK, EXCESIVO }

enum class WarningSeverity { AVISO, INFO }

data class MuscleGroupScore(
    val group: MuscleGroupInfo,
    val effectiveSets: Double,
    val level: CoverageLevel,
)

data class BalanceWarning(
    val message: String,
    val severity: WarningSeverity,
)

data class BalanceReport(
    val scores: List<MuscleGroupScore>,
    val warnings: List<BalanceWarning>,
) {
    val isBalanced: Boolean get() = warnings.none { it.severity == WarningSeverity.AVISO }
}

/**
 * Analiza el volumen semanal de una rutina completa y avisa de grupos musculares
 * ausentes o descompensados. Sets efectivos: 1.0 por set como músculo primario,
 * 0.5 como secundario. Umbrales semanales orientativos de la literatura de
 * hipertrofia (~10-20 sets por grupo).
 */
object MuscleBalanceAnalyzer {

    const val PRIMARY_WEIGHT = 1.0
    const val SECONDARY_WEIGHT = 0.5
    const val LOW_THRESHOLD = 6.0
    const val HIGH_THRESHOLD = 20.0
    val PUSH_PULL_RATIO_RANGE = 0.6..1.5
    val UPPER_LEGS_RATIO_RANGE = 0.5..2.0

    /**
     * Umbral mínimo según el nivel: a un principiante con 3 series útiles no hay
     * que decirle "poco volumen", y un avanzado con 6 sí va corto. Sin nivel se
     * usan los umbrales genéricos de siempre.
     */
    fun lowThresholdFor(level: ExperienceLevel?): Double = when (level) {
        ExperienceLevel.PRINCIPIANTE -> 4.0
        ExperienceLevel.AVANZADO -> 8.0
        else -> LOW_THRESHOLD
    }

    fun highThresholdFor(level: ExperienceLevel?): Double = when (level) {
        ExperienceLevel.PRINCIPIANTE -> 15.0
        ExperienceLevel.AVANZADO -> 25.0
        else -> HIGH_THRESHOLD
    }

    fun analyze(entries: List<MuscleVolumeEntry>, level: ExperienceLevel? = null): BalanceReport {
        val low = lowThresholdFor(level)
        val high = highThresholdFor(level)
        val volumeByGroup = mutableMapOf<Long, Double>()
        for (entry in entries) {
            val weight = if (entry.role == MuscleRole.PRIMARY) PRIMARY_WEIGHT else SECONDARY_WEIGHT
            volumeByGroup[entry.muscleGroupId] =
                (volumeByGroup[entry.muscleGroupId] ?: 0.0) + entry.sets * weight
        }

        val scores = MuscleGroups.all.map { group ->
            val sets = volumeByGroup[group.id] ?: 0.0
            MuscleGroupScore(group, sets, coverageLevel(sets, low, high))
        }

        val warnings = buildList {
            scores.filter { it.level == CoverageLevel.AUSENTE }.forEach {
                add(BalanceWarning("No entrenas ${it.group.name} en esta rutina.", WarningSeverity.AVISO))
            }
            scores.filter { it.level == CoverageLevel.BAJO }.forEach {
                add(
                    BalanceWarning(
                        "Poco volumen para ${it.group.name} (${formatSets(it.effectiveSets)} sets/semana; se recomiendan al menos ${low.toInt()}).",
                        WarningSeverity.AVISO,
                    ),
                )
            }
            scores.filter { it.level == CoverageLevel.EXCESIVO }.forEach {
                add(
                    BalanceWarning(
                        "Volumen muy alto para ${it.group.name} (${formatSets(it.effectiveSets)} sets/semana): vigila la recuperación.",
                        WarningSeverity.INFO,
                    ),
                )
            }

            val push = scores.sumByCategory(MuscleGroupCategory.PUSH)
            val pull = scores.sumByCategory(MuscleGroupCategory.PULL)
            if (push > 0 && pull > 0) {
                val ratio = push / pull
                if (ratio !in PUSH_PULL_RATIO_RANGE) {
                    val msg = if (ratio > PUSH_PULL_RATIO_RANGE.endInclusive) {
                        "Mucho empuje y poco tirón: riesgo de descompensación de hombro."
                    } else {
                        "Mucho tirón y poco empuje: equilibra con más trabajo de pecho/hombro."
                    }
                    add(BalanceWarning(msg, WarningSeverity.AVISO))
                }
            }

            val upper = scores.sumByCategory(MuscleGroupCategory.PUSH) + scores.sumByCategory(MuscleGroupCategory.PULL)
            val legs = scores.sumByCategory(MuscleGroupCategory.LEGS)
            if (upper > 0 && legs > 0) {
                val ratio = upper / legs
                if (ratio !in UPPER_LEGS_RATIO_RANGE) {
                    val msg = if (ratio > UPPER_LEGS_RATIO_RANGE.endInclusive) {
                        "Piernas infraentrenadas respecto al tren superior."
                    } else {
                        "Tren superior infraentrenado respecto a las piernas."
                    }
                    add(BalanceWarning(msg, WarningSeverity.AVISO))
                }
            }
        }

        return BalanceReport(scores, warnings)
    }

    private fun coverageLevel(effectiveSets: Double, low: Double, high: Double): CoverageLevel = when {
        effectiveSets <= 0.0 -> CoverageLevel.AUSENTE
        effectiveSets < low -> CoverageLevel.BAJO
        effectiveSets <= high -> CoverageLevel.OK
        else -> CoverageLevel.EXCESIVO
    }

    private fun List<MuscleGroupScore>.sumByCategory(category: MuscleGroupCategory): Double =
        filter { it.group.category == category }.sumOf { it.effectiveSets }

    private fun formatSets(sets: Double): String =
        if (sets == sets.toLong().toDouble()) sets.toLong().toString() else (kotlin.math.round(sets * 10) / 10.0).toString()
}
