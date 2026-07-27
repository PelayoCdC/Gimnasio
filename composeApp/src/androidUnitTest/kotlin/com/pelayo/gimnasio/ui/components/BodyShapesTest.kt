package com.pelayo.gimnasio.ui.components

import com.pelayo.gimnasio.domain.model.MuscleGroups
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La silueta se escribió como rutas SVG y se lee con un intérprete propio: estos
 * tests son los que garantizan que lo que se dibuja cae donde debe, ya que el
 * resultado visual no se puede comprobar desde aquí.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BodyShapesTest {

    @Test
    fun el_interprete_lee_lineas_rectas() {
        val bounds = BodyShapes.parse("M10 20 L30 20 L30 60 L10 60 Z").getBounds()

        assertEquals(10f, bounds.left)
        assertEquals(20f, bounds.top)
        assertEquals(30f, bounds.right)
        assertEquals(60f, bounds.bottom)
    }

    @Test
    fun el_interprete_lee_curvas() {
        // Una cúbica que sale de (0,0) y acaba en (10,0) pasando por arriba.
        val bounds = BodyShapes.parse("M0 0 C 0 -10, 10 -10, 10 0 Z").getBounds()

        assertEquals(0f, bounds.left)
        assertEquals(10f, bounds.right)
        assertTrue(bounds.top < 0f, "la curva debería subir por encima del origen")
    }

    @Test
    fun la_silueta_cabe_en_el_lienzo_y_lo_aprovecha() {
        val bounds = BodyShapes.silhouette.getBounds()

        assertTrue(bounds.left >= 0f && bounds.right <= 100f, "se sale de ancho: $bounds")
        assertTrue(bounds.top >= 0f && bounds.bottom <= 200f, "se sale de alto: $bounds")
        // Ocupa casi todo el alto y queda centrada.
        assertTrue(bounds.height > 160f, "la figura se queda corta: ${bounds.height}")
        assertTrue(kotlin.math.abs(bounds.center.x - 50f) < 2f, "no está centrada: ${bounds.center}")
    }

    @Test
    fun cada_zona_cae_dentro_de_la_silueta() {
        val body = BodyShapes.silhouette.getBounds()

        (BodyShapes.front + BodyShapes.back).forEach { (groupId, shape) ->
            val zone = shape.getBounds()
            assertTrue(
                zone.left >= body.left - 0.5f && zone.right <= body.right + 0.5f &&
                    zone.top >= body.top - 0.5f && zone.bottom <= body.bottom + 0.5f,
                "la zona del grupo $groupId se sale del cuerpo: $zone frente a $body",
            )
        }
    }

    @Test
    fun las_dos_vistas_reparten_todos_los_grupos_musculares() {
        val cubiertos = (BodyShapes.front + BodyShapes.back).map { it.first }.toSet()

        assertEquals(MuscleGroups.all.map { it.id }.toSet(), cubiertos)
    }

    @Test
    fun el_pecho_esta_arriba_y_los_gemelos_abajo() {
        val pecho = BodyShapes.front.first { it.first == MuscleGroups.PECHO.id }.second.getBounds()
        val core = BodyShapes.front.first { it.first == MuscleGroups.CORE.id }.second.getBounds()
        val cuadriceps = BodyShapes.front.first { it.first == MuscleGroups.CUADRICEPS.id }.second.getBounds()
        val gemelos = BodyShapes.front.first { it.first == MuscleGroups.GEMELOS.id }.second.getBounds()

        assertTrue(pecho.bottom <= core.top + 2f, "el pecho debería quedar sobre el abdomen")
        assertTrue(core.bottom <= cuadriceps.top + 2f, "el abdomen debería quedar sobre los muslos")
        assertTrue(cuadriceps.bottom <= gemelos.top + 2f, "los muslos deberían quedar sobre los gemelos")
    }

    @Test
    fun las_zonas_pares_son_simetricas() {
        // Cada grupo con dos lados tiene que quedar centrado en el eje del cuerpo.
        listOf(
            MuscleGroups.PECHO.id, MuscleGroups.BICEPS.id,
            MuscleGroups.CUADRICEPS.id, MuscleGroups.GEMELOS.id,
        ).forEach { groupId ->
            val bounds = BodyShapes.front.first { it.first == groupId }.second.getBounds()
            assertTrue(
                kotlin.math.abs(bounds.center.x - 50f) < 0.5f,
                "el grupo $groupId no es simétrico: ${bounds.center.x}",
            )
        }
    }
}
