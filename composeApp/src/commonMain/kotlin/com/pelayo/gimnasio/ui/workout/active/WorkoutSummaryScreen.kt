package com.pelayo.gimnasio.ui.workout.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.pelayo.gimnasio.domain.model.SetLogItem
import com.pelayo.gimnasio.domain.model.formatKg
import com.pelayo.gimnasio.ui.components.MuscleMap
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutSummaryScreen(
    sessionId: Long,
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val summary by viewModel.summary.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    var setToEdit by remember { mutableStateOf<SetLogItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entrenamiento") },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar entrenamiento")
                    }
                },
            )
        },
    ) { padding ->
        val current = summary
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(current.dayName, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            StatColumn("Duración", current.durationMinutes?.let { "$it min" } ?: "—")
                            StatColumn("Series", current.totalSets.toString())
                            StatColumn("Volumen", "${current.totalVolume.formatKg()} kg")
                        }
                        current.kcalBurned?.let { kcal ->
                            Spacer(Modifier.height(12.dp))
                            StatColumn("Gasto estimado", "${kcal.toInt()} kcal")
                        }
                    }
                }
            }

            if (current.volumeByMuscleGroup.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Músculos trabajados", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            MuscleMap(volumeByGroup = current.volumeByMuscleGroup)
                        }
                    }
                }
            }

            current.exercises.forEach { exercise ->
                item(key = "sum-${exercise.exerciseId}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(exercise.exerciseName, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            exercise.sets.forEach { set ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { setToEdit = set },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Serie ${set.setNumber}: ${set.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Corregir serie",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Toca una serie para corregirla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Hecho") }
            }
        }
    }

    setToEdit?.let { set ->
        EditSetDialog(
            set = set,
            onDismiss = { setToEdit = null },
            onSave = { weight, reps, time ->
                viewModel.updateSet(set.id, weight, reps, time)
                setToEdit = null
            },
            onDelete = {
                viewModel.deleteSet(set.id)
                setToEdit = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar entrenamiento") },
            text = {
                Text(
                    "Se borrará esta sesión y todas sus series. Dejará de contar para tus " +
                        "gráficas, tu racha y el gasto calórico de ese día.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSession()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun EditSetDialog(
    set: SetLogItem,
    onDismiss: () -> Unit,
    onSave: (Double?, Int?, Int?) -> Unit,
    onDelete: () -> Unit,
) {
    val isTimed = set.timeSeconds != null
    var weight by remember { mutableStateOf(set.weightKg?.formatKg() ?: "") }
    var reps by remember { mutableStateOf(set.reps?.toString() ?: "") }
    var time by remember { mutableStateOf(set.timeSeconds?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Serie ${set.setNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTimed) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it.filter(Char::isDigit) },
                        label = { Text("Segundos") },
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit) },
                        label = { Text("Repeticiones") },
                        singleLine = true,
                    )
                }
                TextButton(onClick = onDelete) { Text("Borrar esta serie") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isTimed) {
                    onSave(null, null, time.toIntOrNull())
                } else {
                    onSave(weight.toDoubleOrNull(), reps.toIntOrNull(), null)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
