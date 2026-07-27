package com.pelayo.gimnasio.ui.components

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El lector de rutas SVG es lo que sostiene el mapa muscular y el logotipo; si
 * se come un comando, el dibujo sale mal sin que nadie se entere hasta verlo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SvgPathTest {

    @Test
    fun lee_lineas() {
        val bounds = svgPath("M10 20 L30 20 L30 60 L10 60 Z").getBounds()

        assertEquals(10f, bounds.left)
        assertEquals(20f, bounds.top)
        assertEquals(30f, bounds.right)
        assertEquals(60f, bounds.bottom)
    }

    @Test
    fun lee_curvas_cubicas() {
        val bounds = svgPath("M0 0 C 0 -10, 10 -10, 10 0 Z").getBounds()

        assertEquals(0f, bounds.left)
        assertEquals(10f, bounds.right)
        assertTrue(bounds.top < 0f, "la curva debería subir por encima del origen")
    }

    @Test
    fun lee_curvas_cuadraticas() {
        // Las esquinas redondeadas del logotipo son cuadráticas: vienen del
        // pathData del icono de Android, que las usa en vez de cúbicas.
        val bounds = svgPath("M25,46 L28,46 Q31,46 31,49 L31,59 Q31,62 28,62 L25,62 Z").getBounds()

        assertEquals(25f, bounds.left)
        assertEquals(46f, bounds.top)
        assertEquals(31f, bounds.right)
        assertEquals(62f, bounds.bottom)
    }

    @Test
    fun un_comando_desconocido_no_revienta_el_resto() {
        // "A" (arco) no está soportado; debe ignorarse sin lanzar.
        val bounds = svgPath("M0 0 L10 0 A 5 5 0 0 1 20 0 L10 10 Z").getBounds()

        assertTrue(bounds.width > 0f && bounds.height > 0f, "se ha perdido el dibujo entero")
    }

    @Test
    fun una_ruta_vacia_da_una_forma_vacia() {
        assertTrue(svgPath("").getBounds().isEmpty)
    }
}
