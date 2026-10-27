package com.pelayo.gimnasio.ui.workout.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.domain.calc.RoutineGenerator
import com.pelayo.gimnasio.domain.model.ExerciseComplexity
import com.pelayo.gimnasio.domain.model.ExperienceLevel
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.hintEs
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.ui.components.BalancePanel
import com.pelayo.gimnasio.ui.theme.WarningAmber
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineGeneratorScreen(
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit = {},
    viewModel: RoutineGeneratorViewModel = koinViewModel(),
) {
    val daysPerWeek by viewModel.daysPerWeek.collectAsState()
    val goal by viewModel.goal.collectAsState()
    val level by viewModel.level.collectAsState()
    val minutesPerSession by viewModel.minutesPerSession.collectAsState()
    val setsPerExercise by viewModel.setsPerExercise.collectAsState()
    val equipment by viewModel.equipment.collectAsState()
    val selectedEquipment by viewModel.selectedEquipment.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val catalogEmpty by viewModel.catalogEmpty.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generar rutina") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (catalogEmpty) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Descarga primero el catálogo de ejercicios: sin él no hay " +
                                        "nada con lo que montar la rutina.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onOpenCatalog) { Text("Ir al catálogo") }
                        }
                    }
                }
            }

            item {
                Text("¿Cuántos días entrenas a la semana?", style = MaterialTheme.typography.titleMedium)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..6).forEach { days ->
                        FilterChip(
                            selected = daysPerWeek == days,
                            onClick = { viewModel.setDays(days) },
                            label = { Text("$days") },
                        )
                    }
                }
            }

            item { Text("¿Cuánto tiempo tienes por sesión?", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineGenerator.SESSION_LENGTHS.forEach { minutes ->
                        FilterChip(
                            selected = minutesPerSession == minutes,
                            onClick = { viewModel.setMinutesPerSession(minutes) },
                            label = { Text("$minutes min") },
                        )
                    }
                }
            }

            item { Text("¿Cuál es tu nivel?", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExperienceLevel.entries.forEach { option ->
                        FilterChip(
                            selected = level == option,
                            onClick = { viewModel.setLevel(option) },
                            label = { Text(option.labelEs) },
                        )
                    }
                }
            }
            item {
                Text(
                    level.hintEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { Text("¿Cuál es tu objetivo?", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineGoal.entries.forEach { option ->
                        FilterChip(
                            selected = goal == option,
                            onClick = { viewModel.setGoal(option) },
                            label = { Text(option.labelEs) },
                        )
                    }
                }
            }

            // Las series van después del objetivo: es el ajuste fino de lo que ya
            // se ha decidido, y así las preguntas de arriba no se mueven de sitio.
            item { Text("¿Cuántas series por ejercicio?", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = setsPerExercise == null,
                        onClick = { viewModel.setSetsPerExercise(null) },
                        label = { Text("Automático") },
                    )
                    RoutineGenerator.SET_OPTIONS.forEach { sets ->
                        FilterChip(
                            selected = setsPerExercise == sets,
                            onClick = { viewModel.setSetsPerExercise(sets) },
                            label = { Text("$sets") },
                        )
                    }
                }
            }
            item {
                Text(
                    if (setsPerExercise == null) {
                        "Automático: las que pidan el objetivo y tu nivel."
                    } else {
                        "Con más series caben menos ejercicios en la misma sesión."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (equipment.isNotEmpty()) {
                item {
                    Column {
                        Text("Material disponible", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Si no marcas nada, usaré todo el catálogo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        equipment.forEach { item ->
                            FilterChip(
                                selected = item.id in selectedEquipment,
                                onClick = { viewModel.toggleEquipment(item.id) },
                                label = { Text(item.nameEs) },
                            )
                        }
                    }
                }
            }

            item {
                val generating by viewModel.generating.collectAsState()
                OutlinedButton(
                    onClick = viewModel::generate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !catalogEmpty && !generating,
                ) {
                    if (generating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Generando…")
                    } else {
                        Text(if (preview == null) "Generar propuesta" else "Regenerar")
                    }
                }
            }

            preview?.let { result ->
                item {
                    Text(result.routine.name, style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Column {
                        Text(
                            "${result.routine.prescription.sets} series de " +
                                "${result.routine.prescription.repsMin}-${result.routine.prescription.repsMax} " +
                                "repeticiones · ${result.routine.prescription.restSeconds} s de descanso",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${result.routine.level.labelEs} · unos ${result.routine.estimatedMinutes} min " +
                                "por sesión, calentamiento incluido",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (result.routine.excludedForLevel > 0) {
                    item {
                        Text(
                            "He dejado fuera ${result.routine.excludedForLevel} ejercicios demasiado " +
                                "técnicos o con riesgo de lesión para tu nivel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (result.routine.uncoveredGroups.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Con el material elegido no hay ejercicios para: " +
                                        result.routine.uncoveredGroups.joinToString(", ") + ".",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // La propuesta ya viene reequilibrada; si aún quedan avisos es que
                // no cabe más entrenamiento en los días y minutos elegidos, y lo
                // honesto es decir qué hay que subir en vez de callarlo.
                if (!result.balance.isBalanced && result.routine.uncoveredGroups.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "He repartido lo mejor que he podido, pero con " +
                                        "${result.routine.days.size} días de " +
                                        "${result.routine.minutesPerSession} min no da para cubrir " +
                                        "todos los grupos. Sube a tres días o alarga la sesión y " +
                                        "los avisos de abajo desaparecen.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                item { BalancePanel(result.balance) }

                result.routine.days.forEach { day ->
                    item(key = "gen-${day.name}") {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(day.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "≈ ${day.estimatedMinutes} min",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                day.exerciseIds.forEach { id ->
                                    val complexity = result.exerciseComplexity[id]
                                    Text(
                                        (result.exerciseNames[id] ?: "Ejercicio") +
                                            if (complexity != null && complexity != ExerciseComplexity.BASICO) {
                                                " · ${complexity.labelEs.lowercase()}"
                                            } else {
                                                ""
                                            },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.save(onSaved) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Guardar rutina") }
                }
            }
        }
    }
}
