package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pelayo.gimnasio.ui.components.HubCard
import com.pelayo.gimnasio.ui.components.LineChart
import com.pelayo.gimnasio.util.monthNumber
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * Portada de Nutrición, con la misma forma que la de Entrenamiento: desde aquí se
 * entra al diario, al catálogo, a las recetas y a los planes. Antes todo colgaba
 * del diario y había que saber que los iconos de arriba llevaban a otra parte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    onOpenDiary: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenDietPlans: () -> Unit,
    viewModel: NutritionHubViewModel = koinViewModel(),
) {
    val history by viewModel.history.collectAsState()
    val tdee by viewModel.tdee.collectAsState()
    val average by viewModel.average.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    // Scaffold + TopAppBar como las demás pestañas, para que el título no salte.
    Scaffold(
        topBar = { TopAppBar(title = { Text("Nutrición") }) },
    ) { padding ->
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HubCard(
                icon = Icons.Filled.Restaurant,
                title = "Diario de hoy",
                subtitle = "Apunta lo que comes y mira el balance de calorías",
                onClick = onOpenDiary,
            )
        }
        item {
            HubCard(
                icon = Icons.Filled.EventNote,
                title = "Planes de dieta",
                subtitle = "Tu menú semanal, listo para volcarlo al diario",
                onClick = onOpenDietPlans,
            )
        }
        item {
            HubCard(
                icon = Icons.Filled.MenuBook,
                title = "Mis recetas",
                subtitle = "Calcula las calorías por ración de lo que cocinas",
                onClick = onOpenRecipes,
            )
        }
        item {
            HubCard(
                icon = Icons.Filled.Inventory2,
                title = "Catálogo de alimentos",
                subtitle = "Lo que tienes guardado; añade escaneando o a mano",
                onClick = onOpenCatalog,
            )
        }

        if (history.isNotEmpty()) {
            item { CaloriesChartCard(history, tdee, average) }
        }
    }
    }
}

/** Calorías de las dos últimas semanas, con la media y el gasto de referencia. */
@Composable
private fun CaloriesChartCard(
    history: List<DailyCalories>,
    tdee: Double?,
    average: Double,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calorías por día", style = MaterialTheme.typography.titleMedium)

            LineChart(
                values = history.map { it.kcal },
                // Solo se etiquetan algunos días: catorce fechas no caben.
                labels = history.mapIndexed { index, day ->
                    if (index % 3 == 0) "${day.date.day}/${day.date.monthNumber}" else ""
                },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                unit = " kcal",
            )

            if (average > 0) {
                Text(
                    buildString {
                        append("Media de los días con registro: ${average.roundToInt()} kcal.")
                        tdee?.let {
                            val diff = average - it
                            append(
                                if (diff >= 0) {
                                    " Unas ${diff.roundToInt()} por encima de tu gasto."
                                } else {
                                    " Unas ${(-diff).roundToInt()} por debajo de tu gasto."
                                },
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Apunta lo que comes y aquí verás la evolución.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
