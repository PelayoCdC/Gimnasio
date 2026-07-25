package com.pelayo.gimnasio.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pelayo.gimnasio.domain.calc.MuscleBalanceAnalyzer
import com.pelayo.gimnasio.domain.calc.MuscleVolumeEntry
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El aviso de descompensación es la seña de identidad de la app: si el panel
 * deja de enseñar los avisos, la funcionalidad desaparece sin que ningún test
 * de lógica se entere.
 *
 * Se usa `assertExists` en vez de `assertIsDisplayed` para el contenido que
 * puede quedar por debajo del alto de la ventana de test: lo que se comprueba
 * es que se renderiza, no que quepa en pantalla.
 */
// Con la Application de la app, Robolectric arrancaría Koin en cada test y el
// segundo fallaría por "already started"; estos componentes no necesitan DI.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BalancePanelTest {

    @get:Rule
    val compose = createComposeRule()

    private fun primary(sets: Int, groupId: Long) =
        MuscleVolumeEntry(sets, MuscleRole.PRIMARY, groupId)

    @Test
    fun una_rutina_sin_piernas_enseña_el_aviso_en_pantalla() {
        val report = MuscleBalanceAnalyzer.analyze(
            listOf(
                primary(10, MuscleGroups.PECHO.id),
                primary(10, MuscleGroups.ESPALDA.id),
                primary(8, MuscleGroups.HOMBROS.id),
                primary(6, MuscleGroups.BICEPS.id),
                primary(6, MuscleGroups.TRICEPS.id),
                primary(6, MuscleGroups.CORE.id),
            ),
        )

        compose.setContent { BalancePanel(report) }

        compose.onNodeWithText("Revisa el equilibrio").assertIsDisplayed()
        compose.onNodeWithText("No entrenas Cuádriceps en esta rutina.").assertExists()
        compose.onNodeWithText("No entrenas Glúteos en esta rutina.").assertExists()
        compose.onNodeWithText("No entrenas Gemelos en esta rutina.").assertExists()
    }

    @Test
    fun una_rutina_equilibrada_no_enseña_avisos() {
        val report = MuscleBalanceAnalyzer.analyze(MuscleGroups.all.map { primary(10, it.id) })

        compose.setContent { BalancePanel(report) }

        compose.onNodeWithText("Rutina equilibrada").assertIsDisplayed()
        compose.onAllNodes(hasText("No entrenas", substring = true)).assertCountEquals(0)
    }

    @Test
    fun se_pintan_todos_los_grupos_musculares_con_su_volumen() {
        val report = MuscleBalanceAnalyzer.analyze(listOf(primary(12, MuscleGroups.PECHO.id)))

        compose.setContent { BalancePanel(report) }

        // Cada grupo aparece con su nombre, se entrene o no.
        MuscleGroups.all.forEach { group ->
            compose.onNodeWithText(group.name).assertExists()
        }
        compose.onNodeWithText("12").assertExists()
    }

    @Test
    fun el_volumen_excesivo_se_avisa_como_informacion() {
        val report = MuscleBalanceAnalyzer.analyze(
            MuscleGroups.all.map { primary(10, it.id) } + primary(15, MuscleGroups.PECHO.id),
        )

        compose.setContent { BalancePanel(report) }

        compose.onAllNodes(hasText("Volumen muy alto para Pecho", substring = true))
            .assertCountEquals(1)
        // Cargar el pecho hasta ahí también descuadra la relación empuje/tirón,
        // así que la rutina deja de estar equilibrada: son dos avisos distintos.
        compose.onNodeWithText("Revisa el equilibrio").assertIsDisplayed()
        compose.onAllNodes(hasText("empuje", substring = true)).assertCountEquals(1)
    }
}
