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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.ui.components.BalancePanel
import com.pelayo.gimnasio.ui.theme.WarningAmber
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineGeneratorScreen(
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RoutineGeneratorViewModel = koinViewModel(),
) {
    val daysPerWeek by viewModel.daysPerWeek.collectAsState()
    val goal by viewModel.goal.collectAsState()
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
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Descarga primero el catálogo de ejercicios desde Entrenamiento → Catálogo.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
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
                OutlinedButton(
                    onClick = viewModel::generate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !catalogEmpty,
                ) { Text(if (preview == null) "Generar propuesta" else "Regenerar") }
            }

            preview?.let { result ->
                item {
                    Text(result.routine.name, style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Text(
                        "${result.routine.prescription.sets} series de " +
                            "${result.routine.prescription.repsMin}-${result.routine.prescription.repsMax} " +
                            "repeticiones · ${result.routine.prescription.restSeconds} s de descanso",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

                item { BalancePanel(result.balance) }

                result.routine.days.forEach { day ->
                    item(key = "gen-${day.name}") {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(day.name, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                day.exerciseIds.forEach { id ->
                                    Text(
                                        result.exerciseNames[id] ?: "Ejercicio",
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
