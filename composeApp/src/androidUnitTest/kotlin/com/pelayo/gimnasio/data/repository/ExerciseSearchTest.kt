package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.createTestDatabase
import com.pelayo.gimnasio.data.db.DatabaseSeeder
import com.pelayo.gimnasio.data.insertTestExercise
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.MuscleGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Búsqueda por palabras sueltas, sin importar orden, mayúsculas ni tildes. */
class ExerciseSearchTest {

    private fun db(): GimnasioDb = createTestDatabase().apply {
        insertTestExercise(1, "Press de banca inclinado", listOf(4L))
        insertTestExercise(2, "Press militar con mancuernas", listOf(2L))
        insertTestExercise(3, "Elevación lateral de hombro", listOf(2L))
        insertTestExercise(4, "Zancada búlgara", listOf(10L))
        insertTestExercise(5, "Curl de bíceps en banco inclinado", listOf(1L))
    }

    private suspend fun search(db: GimnasioDb, query: String, groupId: Long? = null): List<String> =
        ExerciseRepository(db, Dispatchers.Unconfined)
            .searchExercises(query, groupId, null)
            .first()
            .map { it.name }

    @Test
    fun dos_palabras_no_tienen_que_ir_seguidas() = runTest {
        val db = db()
        // "press inclinado" no aparece literal en ningún nombre.
        assertEquals(listOf("Press de banca inclinado"), search(db, "press inclinado"))
    }

    @Test
    fun el_orden_de_las_palabras_da_igual() = runTest {
        val db = db()
        assertEquals(listOf("Press de banca inclinado"), search(db, "inclinado press"))
    }

    @Test
    fun no_distingue_mayusculas() = runTest {
        val db = db()
        assertEquals(listOf("Press militar con mancuernas"), search(db, "PRESS MILITAR"))
    }

    @Test
    fun no_distingue_tildes_ni_en_la_consulta_ni_en_el_nombre() = runTest {
        val db = db()
        assertEquals(listOf("Elevación lateral de hombro"), search(db, "elevacion"))
        assertEquals(listOf("Zancada búlgara"), search(db, "bulgara"))
        assertEquals(listOf("Zancada búlgara"), search(db, "búlgara"))
        assertEquals(listOf("Curl de bíceps en banco inclinado"), search(db, "BÍCEPS"))
    }

    @Test
    fun cada_palabra_recorta_los_resultados() = runTest {
        val db = db()
        assertEquals(2, search(db, "press").size)
        assertEquals(2, search(db, "inclinado").size)
        assertEquals(listOf("Press de banca inclinado"), search(db, "press banca inclinado"))
    }

    @Test
    fun una_palabra_que_no_esta_deja_la_busqueda_vacia() = runTest {
        val db = db()
        assertTrue(search(db, "press inexistente").isEmpty())
    }

    @Test
    fun la_busqueda_se_combina_con_el_filtro_de_grupo() = runTest {
        val db = db()
        assertEquals(
            listOf("Press militar con mancuernas"),
            search(db, "press", MuscleGroups.HOMBROS.id),
        )
    }

    @Test
    fun una_consulta_vacia_los_devuelve_todos() = runTest {
        val db = db()
        assertEquals(5, search(db, "   ").size)
    }

    @Test
    fun tambien_encuentra_por_el_nombre_en_ingles() = runTest {
        val db = createTestDatabase()
        db.catalogQueries.upsertExerciseByWgerId(
            nameEs = "Peso muerto",
            nameEn = "Deadlift",
            descriptionEs = null,
            categoryId = null,
            videoUrl = null,
            metValue = 3.5,
            exerciseType = "REPS",
            wgerId = 9,
            searchName = DatabaseSeeder.searchNameFor("Peso muerto", "Deadlift"),
        )

        assertEquals(listOf("Peso muerto"), search(db, "deadlift"))
    }

    @Test
    fun rellena_el_nombre_de_busqueda_de_las_bases_de_datos_viejas() = runTest {
        // Simula lo que deja la migración: la columna vacía. Sin normalizar, la
        // búsqueda cae en el nombre tal cual y las tildes dejan de casar.
        val db = db()
        val elevacionId = db.catalogQueries.selectExerciseIdByWgerId(3L).executeAsOne()
        db.catalogQueries.updateSearchName(null, elevacionId)
        assertTrue(search(db, "elevacion").isEmpty())

        DatabaseSeeder.fillMissingSearchNames(db)

        assertEquals(listOf("Elevación lateral de hombro"), search(db, "ELEVACION"))
    }
}
