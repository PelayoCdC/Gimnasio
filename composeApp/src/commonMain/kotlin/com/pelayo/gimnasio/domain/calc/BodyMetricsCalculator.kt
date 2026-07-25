package com.pelayo.gimnasio.domain.calc

import com.pelayo.gimnasio.domain.model.BmiCategory
import com.pelayo.gimnasio.domain.model.Sex
import kotlin.math.log10
import kotlin.math.pow

/**
 * Fórmulas de composición corporal. Todas las medidas en kg/cm, edad en años.
 * Los resultados son estimaciones orientativas, no diagnóstico médico.
 */
object BodyMetricsCalculator {

    /** Índice de masa corporal: peso / altura². */
    fun bmi(weightKg: Double, heightCm: Double): Double {
        require(weightKg > 0 && heightCm > 0)
        return weightKg / (heightCm / 100.0).pow(2)
    }

    /** Categorías OMS. */
    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.BAJO_PESO
        bmi < 25.0 -> BmiCategory.NORMAL
        bmi < 30.0 -> BmiCategory.SOBREPESO
        else -> BmiCategory.OBESIDAD
    }

    /** Gasto basal (kcal/día) según Mifflin-St Jeor. */
    fun bmrMifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
        require(weightKg > 0 && heightCm > 0 && ageYears > 0)
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return if (sex == Sex.M) base + 5.0 else base - 161.0
    }

    /** Gasto diario total estimado. */
    fun tdee(bmr: Double, activityFactor: Double): Double = bmr * activityFactor

    /**
     * % de grasa corporal por el método de la Marina de EE. UU. (cinta métrica).
     * Devuelve null si faltan medidas (cuello/cintura, y cadera en mujeres).
     */
    fun bodyFatNavy(
        sex: Sex,
        heightCm: Double,
        neckCm: Double?,
        waistCm: Double?,
        hipCm: Double? = null,
    ): Double? {
        if (neckCm == null || waistCm == null) return null
        return when (sex) {
            Sex.M -> {
                if (waistCm <= neckCm) return null
                495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
            }
            Sex.F -> {
                if (hipCm == null) return null
                if (waistCm + hipCm <= neckCm) return null
                495.0 / (1.29579 - 0.35004 * log10(waistCm + hipCm - neckCm) + 0.22100 * log10(heightCm)) - 450.0
            }
        }.coerceIn(2.0, 60.0)
    }

    /** % de grasa estimado por Deurenberg (solo BMI + edad + sexo); fallback sin cinta métrica. */
    fun bodyFatDeurenberg(bmi: Double, ageYears: Int, sex: Sex): Double {
        val sexFactor = if (sex == Sex.M) 1.0 else 0.0
        return (1.20 * bmi + 0.23 * ageYears - 10.8 * sexFactor - 5.4).coerceIn(2.0, 60.0)
    }
}
