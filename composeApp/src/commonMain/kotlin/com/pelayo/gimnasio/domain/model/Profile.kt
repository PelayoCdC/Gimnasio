package com.pelayo.gimnasio.domain.model

import kotlinx.datetime.LocalDate

data class UserProfileData(
    val name: String?,
    val sex: Sex,
    val birthDate: LocalDate?,
    val heightCm: Double?,
    val activityLevel: ActivityLevel,
    val goal: String?,
    val targetKcalOverride: Double?,
    /** Peso al que se quiere llegar; alimenta la proyección de Inicio. */
    val targetWeightKg: Double? = null,
)

data class BodyMeasurementEntry(
    val id: Long,
    val date: LocalDate,
    val weightKg: Double,
    val neckCm: Double?,
    val waistCm: Double?,
    val hipCm: Double?,
    val bodyFatPercent: Double?,
    val notes: String?,
)

/** Método usado para estimar el porcentaje de grasa, para poder explicarlo en la UI. */
enum class BodyFatMethod(val labelEs: String) {
    NAVY("Método Marina (cinta métrica)"),
    DEURENBERG("Estimación por IMC (Deurenberg)"),
}

data class BodyMetrics(
    /** Nulos si el perfil no tiene fecha de nacimiento: el IMC y el método
     * Marina no la necesitan, así que el panel funciona igual sin ella. */
    val ageYears: Int?,
    val weightKg: Double,
    val heightCm: Double,
    val bmi: Double,
    val bmiCategory: BmiCategory,
    val bodyFatPercent: Double?,
    val bodyFatMethod: BodyFatMethod?,
    val bmr: Double?,
    val tdee: Double?,
) {
    /** Masa magra estimada; null si no hay estimación de grasa. */
    val leanMassKg: Double? get() = bodyFatPercent?.let { weightKg * (1 - it / 100.0) }
}

val BmiCategory.labelEs: String
    get() = when (this) {
        BmiCategory.BAJO_PESO -> "Bajo peso"
        BmiCategory.NORMAL -> "Peso normal"
        BmiCategory.SOBREPESO -> "Sobrepeso"
        BmiCategory.OBESIDAD -> "Obesidad"
    }

val Sex.labelEs: String get() = if (this == Sex.M) "Hombre" else "Mujer"
