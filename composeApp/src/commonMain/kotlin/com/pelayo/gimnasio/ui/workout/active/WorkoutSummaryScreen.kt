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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Entrenamiento completado") }) },
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
                                Text(
                                    "Serie ${set.setNumber}: ${set.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Hecho") }
            }
        }
    }
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
