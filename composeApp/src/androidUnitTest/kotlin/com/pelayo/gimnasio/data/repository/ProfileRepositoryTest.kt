package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.model.ActivityLevel
import com.pelayo.gimnasio.domain.model.BmiCategory
import com.pelayo.gimnasio.domain.model.BodyFatMethod
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.Sex
import com.pelayo.gimnasio.domain.model.UserProfileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRepositoryTest {

    private fun repo() = ProfileRepository(createTestDatabase(), Dispatchers.Unconfined)

    private fun assertClose(expected: Double, actual: Double?, tolerance: Double = 0.5) {
        assertNotNull(actual)
        assertTrue(abs(expected - actual) < tolerance, "esperado $expected, obtenido $actual")
    }

    private suspend fun ProfileRepository.saveTestProfile(
        sex: Sex = Sex.M,
        heightCm: Double = 175.0,
        birthDate: LocalDate = LocalDate(1996, 1, 1),
        activity: ActivityLevel = ActivityLevel.MODERADO,
    ) = saveProfile(
        UserProfileData(
            name = "Pelayo",
            sex = sex,
            birthDate = birthDate,
            heightCm = heightCm,
            activityLevel = activity,
            goal = null,
            targetKcalOverride = null,
        ),
    )

    @Test
    fun guarda_y_recupera_el_perfil() = runTest {
        val repo = repo()
        repo.saveTestProfile()

        val profile = assertNotNull(repo.profile())
        assertEquals("Pelayo", profile.name)
        assertEquals(Sex.M, profile.sex)
        assertEquals(175.0, profile.heightCm)
        assertEquals(ActivityLevel.MODERADO, profile.activityLevel)
    }

    @Test
    fun el_perfil_es_unico_y_se_sobrescribe() = runTest {
        val repo = repo()
        repo.saveTestProfile(heightCm = 175.0)
        repo.saveTestProfile(heightCm = 180.0, sex = Sex.F)

        assertEquals(180.0, repo.profile()!!.heightCm)
        assertEquals(Sex.F, repo.profile()!!.sex)
    }

    @Test
    fun sin_medidas_de_cinta_usa_deurenberg() = runTest {
        val repo = repo()
        repo.saveTestProfile()
        repo.addMeasurement(weightKg = 80.0, date = LocalDate(2026, 7, 25))

        val metrics = assertNotNull(repo.currentMetrics())
        assertEquals(BodyFatMethod.DEURENBERG, metrics.bodyFatMethod)
        assertClose(26.12, metrics.bmi, 0.01)
        assertEquals(BmiCategory.SOBREPESO, metrics.bmiCategory)
    }

    @Test
    fun con_cuello_y_cintura_usa_el_metodo_marina() = runTest {
        val repo = repo()
        repo.saveTestProfile()
        repo.addMeasurement(weightKg = 80.0, neckCm = 38.0, waistCm = 85.0, date = LocalDate(2026, 7, 25))

        val metrics = assertNotNull(repo.currentMetrics())
        assertEquals(BodyFatMethod.NAVY, metrics.bodyFatMethod)
        assertClose(16.94, metrics.bodyFatPercent, 0.1)
        // Masa magra = 80 × (1 − 0.1694).
        assertClose(66.4, metrics.leanMassKg, 0.2)
    }

    @Test
    fun bmr_y_tdee_usan_la_ultima_medicion() = runTest {
        val repo = repo()
        repo.saveTestProfile(birthDate = LocalDate(1996, 1, 1), activity = ActivityLevel.MODERADO)
        repo.addMeasurement(weightKg = 70.0, date = LocalDate(2026, 7, 1))
        repo.addMeasurement(weightKg = 80.0, date = LocalDate(2026, 7, 25))

        val metrics = assertNotNull(repo.currentMetrics())
        assertEquals(80.0, metrics.weightKg, "debe usar la medición más reciente")
        // Con 30 años: 10·80 + 6.25·175 − 5·30 + 5 = 1748.75.
        assertClose(1748.75, metrics.bmr, 1.0)
        assertClose(1748.75 * 1.55, metrics.tdee, 2.0)
    }

    @Test
    fun sin_peso_registrado_no_hay_metricas() = runTest {
        val repo = repo()
        repo.saveTestProfile()
        assertNull(repo.currentMetrics())
    }

    @Test
    fun sin_altura_ni_fecha_no_hay_metricas() = runTest {
        val repo = repo()
        repo.saveProfile(
            UserProfileData(null, Sex.M, null, null, ActivityLevel.LIGERO, null, null),
        )
        repo.addMeasurement(weightKg = 80.0)
        assertNull(repo.currentMetrics())
    }

    @Test
    fun el_historial_llega_ordenado_de_mas_reciente_a_mas_antiguo() = runTest {
        val repo = repo()
        repo.saveTestProfile()
        repo.addMeasurement(weightKg = 82.0, date = LocalDate(2026, 7, 1))
        repo.addMeasurement(weightKg = 80.0, date = LocalDate(2026, 7, 25))

        val list = repo.measurements()
        assertEquals(listOf(80.0, 82.0), list.map { it.weightKg })
        assertEquals(LocalDate(2026, 7, 25), list.first().date)
    }
}

class SessionEnergyTest {

    @Test
    fun estima_kcal_de_la_sesion_con_el_peso_del_usuario() = runTest {
        val db = createTestDatabase()
        val routines = RoutineRepository(db, Dispatchers.Unconfined)
        val workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        // MET 6.0 para que la cuenta sea fácil de comprobar a mano.
        val benchId = db.insertTestExercise(1, "Press", listOf(4L), metValue = 6.0)

        val routineId = routines.createRoutine("Torso", null, RoutineGoal.HIPERTROFIA)
        val dayId = routines.addDay(routineId, "Día 1", null)
        routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)

        val sessionId = workouts.startSession(dayId, LocalDate(2026, 7, 25))
        repeat(3) { index ->
            workouts.logSet(sessionId, benchId, index + 1, 60.0, 10, null)
        }

        // 3 series × (40 s + 45 s de medio descanso) = 255 s = 0.070833 h.
        // 6.0 MET × 80 kg × 0.070833 h ≈ 34 kcal.
        val kcal = assertNotNull(workouts.estimateSessionKcal(sessionId, bodyWeightKg = 80.0))
        assertTrue(abs(34.0 - kcal) < 0.5, "esperado ~34 kcal, obtenido $kcal")
    }

    @Test
    fun sin_peso_corporal_no_se_puede_estimar() = runTest {
        val db = createTestDatabase()
        val workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        assertNull(workouts.estimateSessionKcal(1, bodyWeightKg = null))
    }

    @Test
    fun una_sesion_sin_series_gasta_cero() = runTest {
        val db = createTestDatabase()
        val routines = RoutineRepository(db, Dispatchers.Unconfined)
        val workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        val routineId = routines.createRoutine("Vacía", null, RoutineGoal.FUERZA)
        val dayId = routines.addDay(routineId, "Día", null)
        val sessionId = workouts.startSession(dayId, LocalDate(2026, 7, 25))

        assertEquals(0.0, workouts.estimateSessionKcal(sessionId, 80.0))
    }
}
