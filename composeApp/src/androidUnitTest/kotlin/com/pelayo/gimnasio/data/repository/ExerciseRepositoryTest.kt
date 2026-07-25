package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseRepositoryTest {

    @Test
    fun busqueda_devuelve_imagen_principal_y_null_si_no_hay() = runTest {
        val db = createTestDatabase()
        val repo = ExerciseRepository(db, Dispatchers.Unconfined)

        db.insertTestExercise(
            wgerId = 1,
            name = "Press de banca",
            primaryMuscleIds = listOf(4L),
            imageUrls = listOf("https://ej.com/alt.png" to false, "https://ej.com/main.png" to true),
        )
        db.insertTestExercise(wgerId = 2, name = "Sentadilla sin foto", primaryMuscleIds = listOf(10L))

        val results = repo.searchExercises("", null, null).first()
        assertEquals(2, results.size)

        val bench = results.first { it.name == "Press de banca" }
        assertEquals("https://ej.com/main.png", bench.imageUrl, "debe ganar la imagen marcada como principal")

        val squat = results.first { it.name == "Sentadilla sin foto" }
        assertNull(squat.imageUrl)
    }

    @Test
    fun filtra_por_grupo_muscular_primario() = runTest {
        val db = createTestDatabase()
        val repo = ExerciseRepository(db, Dispatchers.Unconfined)

        // Pectoral (4) primario en press; en remo el pectoral no aparece y el dorsal (12) sí.
        db.insertTestExercise(1, "Press de banca", listOf(4L), secondaryMuscleIds = listOf(5L))
        db.insertTestExercise(2, "Remo con barra", listOf(12L), secondaryMuscleIds = listOf(1L))

        val pecho = repo.searchExercises("", MuscleGroups.PECHO.id, null).first()
        assertEquals(listOf("Press de banca"), pecho.map { it.name })

        // El tríceps solo aparece como secundario: no debe salir al filtrar por ese grupo.
        val triceps = repo.searchExercises("", MuscleGroups.TRICEPS.id, null).first()
        assertTrue(triceps.isEmpty())
    }

    @Test
    fun busqueda_por_texto_es_parcial_e_ignora_acentos_no() = runTest {
        val db = createTestDatabase()
        val repo = ExerciseRepository(db, Dispatchers.Unconfined)
        db.insertTestExercise(1, "Press militar", listOf(2L))
        db.insertTestExercise(2, "Curl de bíceps", listOf(1L))

        assertEquals(listOf("Press militar"), repo.searchExercises("press", null, null).first().map { it.name })
        assertEquals(listOf("Curl de bíceps"), repo.searchExercises("Curl", null, null).first().map { it.name })
    }

    @Test
    fun detalle_incluye_musculos_con_su_rol_e_imagenes() = runTest {
        val db = createTestDatabase()
        val repo = ExerciseRepository(db, Dispatchers.Unconfined)
        val id = db.insertTestExercise(
            wgerId = 1,
            name = "Press de banca",
            primaryMuscleIds = listOf(4L),
            secondaryMuscleIds = listOf(5L, 2L),
            imageUrls = listOf("https://ej.com/main.png" to true),
        )

        val detail = assertNotNull(repo.exerciseDetail(id))
        assertEquals("Press de banca", detail.name)
        assertEquals(1, detail.images.size)

        val primary = detail.muscles.filter { it.role == MuscleRole.PRIMARY }
        assertEquals(listOf("Pectoral mayor"), primary.map { it.nameEs })
        assertEquals(MuscleGroups.PECHO.id, primary.single().muscleGroupId)
        assertEquals(2, detail.muscles.count { it.role == MuscleRole.SECONDARY })
    }

    @Test
    fun detalle_de_id_inexistente_es_null() = runTest {
        val db = createTestDatabase()
        val repo = ExerciseRepository(db, Dispatchers.Unconfined)
        assertNull(repo.exerciseDetail(999))
    }

    @Test
    fun la_semilla_carga_grupos_musculos_y_equipamiento() = runTest {
        val db = createTestDatabase()
        assertEquals(10L, db.catalogQueries.countMuscleGroups().executeAsOne())
        assertEquals(15, db.catalogQueries.selectAllMuscles().executeAsList().size)
        assertEquals(10, db.catalogQueries.selectAllEquipment().executeAsList().size)

        // Todo músculo apunta a un grupo válido del catálogo canónico.
        db.catalogQueries.selectAllMuscles().executeAsList().forEach { muscle ->
            assertNotNull(MuscleGroups.byId(muscle.muscleGroupId), "grupo desconocido en ${muscle.nameEs}")
        }
    }
}
