package com.pelayo.gimnasio.data.backup

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.ActivityLevel
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.Sex
import com.pelayo.gimnasio.domain.model.UserProfileData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Los tests de backup no tocan la red: la API de alimentos responde vacío. */
private fun offlineApi(): OpenFoodFactsApi {
    val engine = MockEngine {
        respond(
            content = """{"count":0,"products":[]}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
    return OpenFoodFactsApi(
        HttpClient(engine) { install(ContentNegotiation) { json(HttpClientFactory.json) } },
    )
}

class BackupServiceTest {

    private class Env(val db: GimnasioDb = createTestDatabase()) {
        val routines = RoutineRepository(db, Dispatchers.Unconfined)
        val workouts = WorkoutRepository(db, Dispatchers.Unconfined)
        val profile = ProfileRepository(db, Dispatchers.Unconfined)
        val nutrition = NutritionRepository(db, offlineApi(), Dispatchers.Unconfined)
        val backup = BackupService(db, Dispatchers.Unconfined)

        /** El catálogo de wger se siembra igual en ambas "instalaciones". */
        fun seedCatalog() {
            db.insertTestExercise(101, "Press de banca", listOf(4L), listOf(5L))
            db.insertTestExercise(102, "Sentadilla", listOf(10L), listOf(8L))
        }

        val benchId get() = db.catalogQueries.selectExerciseIdByWgerId(101).executeAsOne()
        val squatId get() = db.catalogQueries.selectExerciseIdByWgerId(102).executeAsOne()

        /** Deja un estado realista: perfil, medidas, rutina, entreno, alimento y comida. */
        suspend fun populate() {
            seedCatalog()

            profile.saveProfile(
                UserProfileData(
                    name = "Pelayo",
                    sex = Sex.M,
                    birthDate = LocalDate(1996, 3, 14),
                    heightCm = 178.0,
                    activityLevel = ActivityLevel.MODERADO,
                    goal = null,
                    targetKcalOverride = null,
                ),
            )
            profile.addMeasurement(weightKg = 80.5, neckCm = 38.0, waistCm = 85.0, date = LocalDate(2026, 7, 20))
            profile.addMeasurement(weightKg = 79.8, date = LocalDate(2026, 7, 25))

            val routineId = routines.createRoutine("Torso/Pierna", "Mi rutina", RoutineGoal.HIPERTROFIA)
            val dayId = routines.addDay(routineId, "Día 1 – Torso", weekday = 1)
            routines.addExerciseToDay(dayId, benchId, RoutineGoal.HIPERTROFIA)
            val legDay = routines.addDay(routineId, "Día 2 – Pierna", weekday = 4)
            routines.addExerciseToDay(legDay, squatId, RoutineGoal.HIPERTROFIA)

            val sessionId = workouts.startSession(dayId, LocalDate(2026, 7, 21))
            workouts.logSet(sessionId, benchId, 1, 60.0, 10, null)
            workouts.logSet(sessionId, benchId, 2, 62.5, 8, null)
            workouts.completeSession(sessionId, durationMinutes = 52, kcalBurned = 240.0, notes = null)

            val foodId = nutrition.createCustomFood("Avena", "Hacendado", 380.0, 13.0, 60.0, 7.0)
            nutrition.logFood(LocalDate(2026, 7, 25), MealType.DESAYUNO, foodId, grams = 80.0)

            val recipeId = nutrition.createRecipe("Tortilla", servings = 2, notes = "Con cebolla")
            nutrition.addIngredient(recipeId, foodId, grams = 150.0)

            val planId = nutrition.createDietPlan("Volumen", 2800.0, 160.0, 350.0, 78.0)
            nutrition.addFoodToDietPlan(planId, MealType.DESAYUNO, foodId, grams = 120.0)
            nutrition.addRecipeToDietPlan(planId, MealType.CENA, recipeId, servings = 1.0)
        }

        suspend fun routineNames(): List<String> = routines.routines().first().map { it.name }
    }

    @Test
    fun exportar_y_restaurar_en_una_instalacion_limpia_conserva_los_datos() = runTest {
        val origin = Env()
        origin.populate()
        val json = origin.backup.exportToJson()

        // Segunda "instalación": mismo catálogo de wger, sin datos del usuario.
        val restored = Env()
        restored.seedCatalog()
        val summary = restored.backup.importFromJson(json).getOrThrow()

        assertEquals(0, summary.skippedExercises, "no debería omitirse nada con el mismo catálogo")

        // Perfil y medidas.
        val profile = assertNotNull(restored.profile.profile())
        assertEquals("Pelayo", profile.name)
        assertEquals(178.0, profile.heightCm)
        assertEquals(ActivityLevel.MODERADO, profile.activityLevel)
        assertEquals(listOf(79.8, 80.5), restored.profile.measurements().map { it.weightKg })

        // Rutina con sus días y ejercicios, resueltos por wgerId.
        val routine = restored.routines.routines().first().single()
        val detail = assertNotNull(restored.routines.routineDetail(routine.id))
        assertEquals("Torso/Pierna", detail.name)
        assertEquals(listOf("Día 1 – Torso", "Día 2 – Pierna"), detail.days.map { it.name })
        assertEquals(listOf(1, 4), detail.days.map { it.weekday })
        assertEquals("Press de banca", detail.days.first().exercises.single().exerciseName)
        assertTrue(detail.isActive)

        // Historial de entrenamiento.
        val history = restored.workouts.progressHistory(restored.benchId)
        assertEquals(1, history.size)
        assertEquals(LocalDate(2026, 7, 21), history.single().date)
        assertEquals(62.5, history.single().maxWeight)

        // Nutrición: alimento propio, diario y receta.
        val day = restored.nutrition.dayNutrition(LocalDate(2026, 7, 25))
        assertEquals(304.0, day.total.kcal, "80 g de avena a 380 kcal/100 g")
        val recipe = restored.nutrition.recipes().single()
        assertEquals("Tortilla", recipe.name)
        assertEquals(2, recipe.servings)
        assertEquals(150.0, recipe.ingredients.single().grams)
    }

    @Test
    fun los_planes_de_dieta_sobreviven_a_la_copia() = runTest {
        val origin = Env()
        origin.populate()
        val json = origin.backup.exportToJson()

        val restored = Env()
        restored.seedCatalog()
        val summary = restored.backup.importFromJson(json).getOrThrow()

        assertEquals(1, summary.dietPlans)
        val plan = assertNotNull(restored.nutrition.activeDietPlan())
        assertEquals("Volumen", plan.name)
        assertEquals(2800.0, plan.targetKcal)
        assertEquals(160.0, plan.targetProteinG)

        // El alimento se reengancha por nombre y la receta por su nombre.
        val desayuno = plan.mealsFor(MealType.DESAYUNO).single()
        assertEquals("Avena", desayuno.name)
        assertEquals(120.0, desayuno.grams)

        val cena = plan.mealsFor(MealType.CENA).single()
        assertEquals("Tortilla", cena.name)
        assertEquals(1.0, cena.servings)
    }

    @Test
    fun restaurar_no_destruye_los_planes_que_venian_en_la_copia() = runTest {
        // Este era el fallo: la restauración borraba los planes y no los reponía.
        val origin = Env()
        origin.populate()
        val json = origin.backup.exportToJson()

        // Restaurar sobre la misma instalación no debe dejarla sin planes.
        origin.backup.importFromJson(json).getOrThrow()

        assertEquals(1, origin.nutrition.dietPlans().size)
        assertNotNull(origin.nutrition.activeDietPlan())
    }

    @Test
    fun una_copia_de_la_version_1_se_sigue_leyendo() = runTest {
        // Formato antiguo: sin la clave dietPlans.
        val oldFormat = """
        {
          "version": 1,
          "exportedAt": "2026-07-20T10:00:00Z",
          "measurements": [
            {"date":"2026-07-20","weightKg":80.0,"neckCm":null,"waistCm":null,
             "hipCm":null,"bodyFatPercent":null,"notes":null}
          ]
        }
        """.trimIndent()

        val env = Env()
        val summary = env.backup.importFromJson(oldFormat).getOrThrow()

        assertEquals(0, summary.dietPlans)
        assertEquals(1, summary.measurements)
        assertEquals(80.0, env.profile.measurements().single().weightKg)
    }

    @Test
    fun restaurar_sustituye_los_datos_que_hubiera() = runTest {
        val origin = Env()
        origin.populate()
        val json = origin.backup.exportToJson()

        // Destino con datos propios distintos que deben desaparecer.
        val target = Env()
        target.seedCatalog()
        target.routines.createRoutine("Rutina vieja", null, RoutineGoal.FUERZA)
        target.profile.addMeasurement(weightKg = 95.0, date = LocalDate(2026, 1, 1))

        target.backup.importFromJson(json).getOrThrow()

        assertEquals(listOf("Torso/Pierna"), target.routineNames(), "la rutina previa debe desaparecer")
        assertTrue(target.profile.measurements().none { it.weightKg == 95.0 })
    }

    @Test
    fun sin_el_catalogo_descargado_se_omiten_los_ejercicios_y_se_avisa() = runTest {
        val origin = Env()
        origin.populate()
        val json = origin.backup.exportToJson()

        // Instalación nueva sin catálogo de wger.
        val empty = Env()
        val summary = empty.backup.importFromJson(json).getOrThrow()

        assertTrue(summary.skippedExercises > 0, "debe avisar de lo que no pudo enlazar")
        // Lo que no depende del catálogo sí se restaura.
        assertEquals(2, empty.profile.measurements().size)
        assertEquals(1, summary.recipes)
    }

    @Test
    fun el_ejercicio_creado_a_mano_sobrevive_al_viaje() = runTest {
        val origin = Env()
        origin.seedCatalog()
        origin.db.catalogQueries.insertCustomExercise(
            "Remo invertido", "Bajo la barra", 3.5, "REPS", "remo invertido",
        )
        val customId = origin.db.catalogQueries.lastInsertRowId().executeAsOne()
        origin.db.catalogQueries.insertExerciseMuscle(customId, 12L, "PRIMARY")

        val routineId = origin.routines.createRoutine("Casa", null, RoutineGoal.HIPERTROFIA)
        val dayId = origin.routines.addDay(routineId, "Tirón", null)
        origin.routines.addExerciseToDay(dayId, customId, RoutineGoal.HIPERTROFIA)

        val restored = Env()
        restored.seedCatalog()
        val summary = restored.backup.importFromJson(origin.backup.exportToJson()).getOrThrow()

        assertEquals(0, summary.skippedExercises)
        val routine = restored.routines.routines().first().single()
        val detail = assertNotNull(restored.routines.routineDetail(routine.id))
        assertEquals("Remo invertido", detail.days.single().exercises.single().exerciseName)
    }

    @Test
    fun un_json_invalido_falla_sin_tocar_los_datos() = runTest {
        val env = Env()
        env.populate()

        val result = env.backup.importFromJson("{ esto no es un backup }")

        assertTrue(result.isFailure)
        assertEquals(2, env.profile.measurements().size, "los datos deben seguir intactos")
    }

    @Test
    fun un_backup_de_version_futura_se_rechaza() = runTest {
        val env = Env()
        val result = env.backup.importFromJson("""{"version":99,"exportedAt":"2026-07-25T00:00:00Z"}""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("versión") == true)
    }

    @Test
    fun el_csv_de_series_lleva_cabecera_y_una_fila_por_serie() = runTest {
        val env = Env()
        env.populate()

        val csv = env.backup.exportSetsCsv().trim().lines()

        assertEquals("fecha;ejercicio;serie;peso_kg;repeticiones;segundos;calentamiento", csv.first())
        assertEquals(3, csv.size, "cabecera + 2 series")
        assertTrue(csv[1].startsWith("2026-07-21;Press de banca;1;60.0;10;;no"), "fila: ${csv[1]}")
    }

    @Test
    fun el_csv_de_medidas_incluye_la_grasa_calculada() = runTest {
        val env = Env()
        env.populate()

        val csv = env.backup.exportMeasurementsCsv().trim().lines()
        assertEquals("fecha;peso_kg;cuello_cm;cintura_cm;cadera_cm;grasa_pct;notas", csv.first())
        assertEquals(3, csv.size)
        assertTrue(csv.any { it.startsWith("2026-07-20;80.5;38.0;85.0") })
    }
}
