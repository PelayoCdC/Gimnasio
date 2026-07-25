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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.WorkoutExerciseProgress
import com.pelayo.gimnasio.domain.model.formatKg
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    sessionId: Long,
    onFinished: (Long) -> Unit,
    onCancel: () -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val workout by viewModel.workout.collectAsState()
    val restTimer by viewModel.restTimer.collectAsState()
    val finished by viewModel.finished.collectAsState()
    var showFinishDialog by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) onFinished(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.dayName ?: "Entrenamiento") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.discardIfEmpty()
                        onCancel()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Salir")
                    }
                },
                actions = {
                    TextButton(onClick = { showFinishDialog = true }) { Text("Terminar") }
                },
            )
        },
        bottomBar = {
            restTimer?.let { timer ->
                RestTimerBar(
                    timer = timer,
                    exactAlarmsAllowed = viewModel.exactAlarmsAllowed,
                    onSkip = viewModel::skipRest,
                    onAddTime = { viewModel.addRestTime(30) },
                    onGrantExactAlarms = viewModel::openExactAlarmSettings,
                )
            }
        },
    ) { padding ->
        val current = workout
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (current.exercises.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Este día no tiene ejercicios", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Añádelos desde el detalle de la rutina.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            current.exercises.forEach { exercise ->
                item(key = "ex-${exercise.exerciseId}") {
                    ExerciseLogCard(
                        exercise = exercise,
                        onLogSet = { weight, reps, time ->
                            viewModel.logSet(
                                exerciseId = exercise.exerciseId,
                                setNumber = exercise.loggedSets.size + 1,
                                weightKg = weight,
                                reps = reps,
                                timeSeconds = time,
                                restSeconds = exercise.restSeconds,
                                exerciseName = exercise.exerciseName,
                            )
                        },
                        onDeleteSet = viewModel::deleteSet,
                        onOpenExercise = { onOpenExercise(exercise.exerciseId) },
                    )
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Terminar entrenamiento") },
            text = { Text("Se guardarán las series registradas.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.finishWorkout()
                }) { Text("Terminar") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Seguir entrenando") }
            },
        )
    }
}

@Composable
private fun ExerciseLogCard(
    exercise: WorkoutExerciseProgress,
    onLogSet: (Double?, Int?, Int?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onOpenExercise: () -> Unit,
) {
    val isTimed = exercise.exerciseType == ExerciseType.TIME
    // Prerrelleno: la serie equivalente de la última vez, o el objetivo de la rutina.
    val previous = exercise.previousSets.getOrNull(exercise.loggedSets.size)
        ?: exercise.previousSets.lastOrNull()
    var weight by remember(exercise.loggedSets.size) {
        mutableStateOf(previous?.weightKg?.formatKg() ?: "")
    }
    var reps by remember(exercise.loggedSets.size) {
        mutableStateOf((previous?.reps ?: exercise.targetRepsMax)?.toString() ?: "")
    }
    var time by remember(exercise.loggedSets.size) {
        mutableStateOf((previous?.timeSeconds ?: exercise.targetTimeSeconds)?.toString() ?: "")
    }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = if (exercise.isComplete) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (exercise.imageUrl != null) {
                    AsyncImage(
                        model = exercise.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        exercise.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Objetivo: ${exercise.loggedSets.size}/${exercise.targetSets} series",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (exercise.isComplete) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Completado",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Consultar la técnica sin salir del entrenamiento.
                IconButton(onClick = onOpenExercise) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Cómo se hace")
                }
            }

            // Solo tiene sentido proponerlo antes de empezar a registrar series.
            exercise.progression?.takeIf { exercise.loggedSets.isEmpty() }?.let { suggestion ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Toca subir a ${suggestion.newWeightKg.formatKg()} kg",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                suggestion.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        TextButton(onClick = { weight = suggestion.newWeightKg.formatKg() }) {
                            Text("Aplicar")
                        }
                    }
                }
            }

            exercise.loggedSets.forEach { set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Serie ${set.setNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        set.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteSet(set.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Borrar serie",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (previous != null && exercise.loggedSets.isEmpty()) {
                Text(
                    "Última vez: ${exercise.previousSets.joinToString(", ") { it.label }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isTimed) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it.filter(Char::isDigit) },
                        label = { Text("Segundos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit) },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        if (isTimed) {
                            onLogSet(null, null, time.toIntOrNull())
                        } else {
                            onLogSet(weight.toDoubleOrNull(), reps.toIntOrNull(), null)
                        }
                    },
                    enabled = if (isTimed) time.toIntOrNull() != null else reps.toIntOrNull() != null,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Registrar serie")
                }
            }
        }
    }
}

@Composable
private fun RestTimerBar(
    timer: RestTimerState,
    exactAlarmsAllowed: Boolean,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    onGrantExactAlarms: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Descanso", style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatSeconds(timer.secondsRemaining),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                OutlinedButton(onClick = onAddTime) { Text("+30 s") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSkip) { Text("Saltar") }
            }
            LinearProgressIndicator(
                progress = { timer.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!exactAlarmsAllowed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "El aviso puede llegar con retraso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onGrantExactAlarms) { Text("Permitir") }
                }
            }
        }
    }
}

private fun formatSeconds(total: Int): String {
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "$seconds s"
}
