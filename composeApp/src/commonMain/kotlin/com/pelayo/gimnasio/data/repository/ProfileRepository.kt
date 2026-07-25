package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.calc.BodyMetricsCalculator
import com.pelayo.gimnasio.domain.model.ActivityLevel
import com.pelayo.gimnasio.domain.model.BodyFatMethod
import com.pelayo.gimnasio.domain.model.BodyMeasurementEntry
import com.pelayo.gimnasio.domain.model.BodyMetrics
import com.pelayo.gimnasio.domain.model.Sex
import com.pelayo.gimnasio.domain.model.UserProfileData
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.periodUntil

class ProfileRepository(
    private val db: GimnasioDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.profileQueries

    suspend fun profile(): UserProfileData? = withContext(dispatcher) {
        q.selectProfile().executeAsOneOrNull()?.let { row ->
            UserProfileData(
                name = row.name,
                sex = Sex.valueOf(row.sex),
                birthDate = row.birthDate?.let { LocalDate.parse(it) },
                heightCm = row.heightCm,
                activityLevel = ActivityLevel.entries
                    .minByOrNull { kotlin.math.abs(it.factor - row.activityFactor) }
                    ?: ActivityLevel.LIGERO,
                goal = row.goal,
                targetKcalOverride = row.targetKcalOverride,
            )
        }
    }

    suspend fun saveProfile(profile: UserProfileData) = withContext(dispatcher) {
        q.upsertProfile(
            name = profile.name,
            sex = profile.sex.name,
            birthDate = profile.birthDate?.toString(),
            heightCm = profile.heightCm,
            activityFactor = profile.activityLevel.factor,
            goal = profile.goal,
            targetKcalOverride = profile.targetKcalOverride,
        )
    }

    /**
     * Guarda una medición y calcula su % de grasa: método Marina si hay medidas
     * de cinta suficientes, y si no la estimación por IMC.
     */
    suspend fun addMeasurement(
        weightKg: Double,
        neckCm: Double? = null,
        waistCm: Double? = null,
        hipCm: Double? = null,
        notes: String? = null,
        date: LocalDate = today(),
    ) = withContext(dispatcher) {
        val profile = profile()
        val height = profile?.heightCm
        val age = profile?.birthDate?.ageOn(date)

        val bodyFat = if (height != null && age != null) {
            BodyMetricsCalculator.bodyFatNavy(profile.sex, height, neckCm, waistCm, hipCm)
                ?: BodyMetricsCalculator.bodyFatDeurenberg(
                    BodyMetricsCalculator.bmi(weightKg, height),
                    age,
                    profile.sex,
                )
        } else {
            null
        }

        q.insertMeasurement(date.toString(), weightKg, neckCm, waistCm, hipCm, bodyFat, notes)
    }

    suspend fun deleteMeasurement(id: Long) = withContext(dispatcher) { q.deleteMeasurement(id) }

    suspend fun measurements(): List<BodyMeasurementEntry> = withContext(dispatcher) {
        q.selectMeasurements().executeAsList().map { it.toEntry() }
    }

    suspend fun latestMeasurement(): BodyMeasurementEntry? = withContext(dispatcher) {
        q.selectLatestMeasurement().executeAsOneOrNull()?.toEntry()
    }

    /** Métricas derivadas del perfil y la última medición; null si faltan datos básicos. */
    suspend fun currentMetrics(): BodyMetrics? = withContext(dispatcher) {
        val profile = profile() ?: return@withContext null
        val height = profile.heightCm ?: return@withContext null
        val birthDate = profile.birthDate ?: return@withContext null
        val measurement = latestMeasurement() ?: return@withContext null

        val age = birthDate.ageOn(today())
        if (age <= 0) return@withContext null

        val bmi = BodyMetricsCalculator.bmi(measurement.weightKg, height)
        val navy = BodyMetricsCalculator.bodyFatNavy(
            profile.sex, height, measurement.neckCm, measurement.waistCm, measurement.hipCm,
        )
        val bodyFat = navy ?: BodyMetricsCalculator.bodyFatDeurenberg(bmi, age, profile.sex)
        val bmr = BodyMetricsCalculator.bmrMifflinStJeor(measurement.weightKg, height, age, profile.sex)

        BodyMetrics(
            ageYears = age,
            weightKg = measurement.weightKg,
            heightCm = height,
            bmi = bmi,
            bmiCategory = BodyMetricsCalculator.bmiCategory(bmi),
            bodyFatPercent = bodyFat,
            bodyFatMethod = if (navy != null) BodyFatMethod.NAVY else BodyFatMethod.DEURENBERG,
            bmr = bmr,
            tdee = BodyMetricsCalculator.tdee(bmr, profile.activityLevel.factor),
        )
    }
}

/** Edad cumplida en una fecha dada. */
fun LocalDate.ageOn(date: LocalDate): Int = periodUntil(date).years

private fun com.pelayo.gimnasio.db.BodyMeasurement.toEntry() = BodyMeasurementEntry(
    id = id,
    date = LocalDate.parse(date),
    weightKg = weightKg,
    neckCm = neckCm,
    waistCm = waistCm,
    hipCm = hipCm,
    bodyFatPercent = bodyFatPercent,
    notes = notes,
)
