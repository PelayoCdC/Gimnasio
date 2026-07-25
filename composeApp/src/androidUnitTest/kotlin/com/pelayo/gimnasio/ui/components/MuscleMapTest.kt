package com.pelayo.gimnasio.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pelayo.gimnasio.domain.model.MuscleGroups
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MuscleMapTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun separa_los_grupos_trabajados_de_los_que_no() {
        compose.setContent {
            MuscleMap(
                volumeByGroup = mapOf(
                    MuscleGroups.PECHO.id to 12.0,
                    MuscleGroups.TRICEPS.id to 6.0,
                ),
            )
        }

        compose.onNodeWithText("Trabajados").assertExists()
        compose.onNodeWithText("Sin trabajar").assertExists()
        compose.onNodeWithText("Delante").assertIsDisplayed()
        compose.onNodeWithText("Detrás").assertIsDisplayed()
    }

    @Test
    fun las_etiquetas_de_las_vistas_no_chocan_con_los_grupos_musculares() {
        // "Espalda" es un grupo muscular; si la vista trasera se llamara igual,
        // el nombre aparecería dos veces y sería confuso.
        compose.setContent { MuscleMap(volumeByGroup = emptyMap()) }

        compose.onAllNodes(hasText("Espalda")).assertCountEquals(1)
    }

    @Test
    fun sin_volumen_no_hay_seccion_de_trabajados() {
        compose.setContent { MuscleMap(volumeByGroup = emptyMap()) }

        compose.onAllNodes(hasText("Trabajados")).assertCountEquals(0)
        compose.onNodeWithText("Sin trabajar").assertExists()
    }

    @Test
    fun cada_grupo_muscular_aparece_una_sola_vez_en_la_leyenda() {
        compose.setContent {
            MuscleMap(volumeByGroup = mapOf(MuscleGroups.CUADRICEPS.id to 8.0))
        }

        MuscleGroups.all.forEach { group ->
            compose.onAllNodes(hasText(group.name)).assertCountEquals(1)
        }
    }
}
