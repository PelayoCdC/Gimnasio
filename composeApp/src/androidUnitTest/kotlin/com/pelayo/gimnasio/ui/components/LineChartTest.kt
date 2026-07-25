package com.pelayo.gimnasio.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La gráfica se dibuja en Canvas, así que lo comprobable son sus etiquetas: el
 * máximo, el mínimo y las fechas de los extremos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LineChartTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun enseña_maximo_minimo_y_fechas_extremas() {
        compose.setContent {
            LineChart(
                values = listOf(60.0, 65.0, 62.5),
                labels = listOf("2026-07-01", "2026-07-08", "2026-07-15"),
                unit = " kg",
            )
        }

        compose.onNodeWithText("65 kg").assertIsDisplayed()
        compose.onNodeWithText("60 kg").assertIsDisplayed()
        compose.onNodeWithText("2026-07-01").assertIsDisplayed()
        compose.onNodeWithText("2026-07-15").assertIsDisplayed()
    }

    @Test
    fun con_un_solo_punto_no_revienta() {
        compose.setContent {
            LineChart(values = listOf(80.0), labels = listOf("2026-07-25"), unit = " kg")
        }

        compose.onNodeWithText("2026-07-25").assertIsDisplayed()
    }

    @Test
    fun con_todos_los_valores_iguales_tampoco_revienta() {
        // El rango sería cero: hay que evitar la división entre cero al escalar.
        compose.setContent {
            LineChart(
                values = listOf(80.0, 80.0, 80.0),
                labels = listOf("a", "b", "c"),
                unit = " kg",
            )
        }

        // Máximo y mínimo coinciden, así que la etiqueta sale dos veces.
        compose.onAllNodes(hasText("80 kg")).assertCountEquals(2)
    }

    @Test
    fun sin_datos_no_pinta_nada() {
        compose.setContent { LineChart(values = emptyList(), labels = emptyList()) }
        compose.onAllNodes(hasText("", substring = true))
            .fetchSemanticsNodes()
            .let { assert(it.isEmpty()) { "no debería renderizar nada sin datos" } }
    }
}
