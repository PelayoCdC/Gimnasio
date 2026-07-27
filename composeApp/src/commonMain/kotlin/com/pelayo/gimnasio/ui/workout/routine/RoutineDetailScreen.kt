package com.pelayo.gimnasio.ui.workout.routine

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.calc.RoutineSuggestion
import com.pelayo.gimnasio.domain.calc.SuggestionKind
import com.pelayo.gimnasio.domain.model.RoutineDayInfo
import com.pelayo.gimnasio.domain.model.RoutineExerciseItem
import com.pelayo.gimnasio.domain.model.RoutineSummary
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import com.pelayo.gimnasio.ui.components.BalancePanel
import com.pelayo.gimnasio.ui.components.MuscleMap
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    routineId: Long,
    onBack: () -> Unit,
    onAddExercise: (dayId: Long) -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
    onStartWorkout: (sessionId: Long) -> Unit,
    viewModel: RoutineDetailViewModel = koinViewModel { parametersOf(routineId) },
) {
    val routine by viewModel.routine.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val otherRoutines by viewModel.otherRoutines.collectAsState()
    var showAddDayDialog by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<RoutineExerciseItem?>(null) }
    var dayToEdit by remember { mutableStateOf<RoutineDayInfo?>(null) }
    var dayToCopy by remember { mutableStateOf<RoutineDayInfo?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Al volver del selector de ejercicios hay que refrescar la rutina y su balance.
    LifecycleResumeEffect(routineId) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.name ?: "Rutina") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val current = routine
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(current.goal.labelEs) })
                    Spacer(Modifier.width(8.dp))
                    if (current.isActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Activa") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    } else {
                        TextButton(onClick = viewModel::setActive) { Text("Marcar como activa") }
                    }
                }
            }

            balance?.let { report ->
                item { BalancePanel(report) }

                if (suggestions.isNotEmpty()) {
                    item {
                        SuggestionsCard(
                            suggestions = suggestions,
                            onApply = viewModel::applySuggestion,
                            onDismiss = viewModel::dismissSuggestion,
                        )
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Mapa muscular", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Volumen semanal por zona",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            MuscleMap(
                                volumeByGroup = report.scores.associate { it.group.id to it.effectiveSets },
                            )
                        }
                    }
                }
            }

            current.days.forEach { day ->
                item(key = "day-${day.id}") {
                    DayCard(
                        day = day,
                        onAddExercise = { onAddExercise(day.id) },
                        onCopyDay = { dayToCopy = day },
                        onEditDay = { dayToEdit = day },
                        onDeleteDay = { viewModel.deleteDay(day.id) },
                        onEditExercise = { exerciseToEdit = it },
                        onOpenExercise = { onOpenExercise(it.exerciseId) },
                        onRemoveExercise = { viewModel.removeExercise(it.id) },
                        onMoveExercise = { item, delta -> viewModel.moveExercise(item.id, delta) },
                        onStartWorkout = { viewModel.startWorkout(day.id, onStartWorkout) },
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddDayDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir día")
                }
            }
        }
    }

    if (showAddDayDialog) {
        DayDialog(
            title = "Nuevo día",
            initialName = "Día ${(routine?.days?.size ?: 0) + 1}",
            initialWeekday = null,
            confirmLabel = "Añadir",
            onDismiss = { showAddDayDialog = false },
            onConfirm = { name, weekday ->
                showAddDayDialog = false
                viewModel.addDay(name, weekday)
            },
        )
    }

    dayToEdit?.let { day ->
        DayDialog(
            title = "Editar día",
            initialName = day.name,
            initialWeekday = day.weekday,
            confirmLabel = "Guardar",
            onDismiss = { dayToEdit = null },
            onConfirm = { name, weekday ->
                viewModel.updateDay(day.id, name, weekday)
                dayToEdit = null
            },
        )
    }

    dayToCopy?.let { day ->
        CopyDayDialog(
            day = day,
            routines = otherRoutines,
            onDismiss = { dayToCopy = null },
            onPick = { target ->
                dayToCopy = null
                viewModel.copyDayTo(day.id, target.id) {
                    scope.launch { snackbarHost.showSnackbar("\"${day.name}\" copiado a ${target.name}") }
                }
            },
        )
    }

    exerciseToEdit?.let { exercise ->
        EditExerciseDialog(
            exercise = exercise,
            onDismiss = { exerciseToEdit = null },
            onSave = { sets, repsMin, repsMax, timeSeconds, weight, rest ->
                viewModel.updateExercise(
                    id = exercise.id,
                    sets = sets,
                    repsMin = repsMin,
                    repsMax = repsMax,
                    timeSeconds = timeSeconds,
                    weight = weight,
                    restSeconds = rest,
                    position = exercise.position,
                )
                exerciseToEdit = null
            },
        )
    }
}

/**
 * Convierte los avisos de descompensación en cosas que se pueden aplicar de un
 * toque. Cada sugerencia se puede descartar: son consejos, no deberes.
 */
@Composable
private fun SuggestionsCard(
    suggestions: List<RoutineSuggestion>,
    onApply: (RoutineSuggestion) -> Unit,
    onDismiss: (RoutineSuggestion) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Sugerencias", style = MaterialTheme.typography.titleMedium)
            }

            suggestions.forEach { suggestion ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(suggestion.message, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onApply(suggestion) }) {
                            Text(
                                if (suggestion.kind == SuggestionKind.CAMBIAR) "Cambiar" else "Añadir",
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDismiss(suggestion) }) { Text("Ahora no") }
                    }
                }
            }
        }
    }
}

/** Lista de rutinas de destino para copiar un día. */
@Composable
private fun CopyDayDialog(
    day: RoutineDayInfo,
    routines: List<RoutineSummary>,
    onDismiss: () -> Unit,
    onPick: (RoutineSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copiar \"${day.name}\"") },
        text = {
            if (routines.isEmpty()) {
                Text("No hay otra rutina a la que copiarlo. Crea otra rutina primero.")
            } else {
                Column {
                    Text(
                        "Se copiarán sus ${day.exercises.size} ejercicios con sus series y descansos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    routines.forEach { target ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(target) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(target.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${target.dayCount} días · ${target.exerciseCount} ejercicios",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun DayCard(
    day: RoutineDayInfo,
    onAddExercise: () -> Unit,
    onCopyDay: () -> Unit,
    onEditDay: () -> Unit,
    onDeleteDay: () -> Unit,
    onEditExercise: (RoutineExerciseItem) -> Unit,
    onOpenExercise: (RoutineExerciseItem) -> Unit,
    onRemoveExercise: (RoutineExerciseItem) -> Unit,
    onMoveExercise: (RoutineExerciseItem, Int) -> Unit,
    onStartWorkout: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        day.weekday?.let { weekdayNamesEs.getOrElse(it - 1) { "" } }
                            ?: "Sin día asignado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCopyDay) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar día a otra rutina")
                }
                IconButton(onClick = onEditDay) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar día")
                }
                IconButton(onClick = onDeleteDay) {
                    Icon(Icons.Filled.Close, contentDescription = "Borrar día")
                }
            }

            if (day.exercises.isEmpty()) {
                Text(
                    "Sin ejercicios todavía",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.exercises.forEachIndexed { index, exercise ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Toda la fila abre la ficha del ejercicio; las acciones van aparte.
                        Row(
                            Modifier.weight(1f).clickable { onOpenExercise(exercise) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (exercise.imageUrl != null) {
                                AsyncImage(
                                    model = exercise.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.FitnessCenter,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    exercise.exerciseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    exercise.targetLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column {
                            IconButton(
                                onClick = { onMoveExercise(exercise, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Subir",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = { onMoveExercise(exercise, 1) },
                                enabled = index < day.exercises.lastIndex,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Bajar",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onEditExercise(exercise) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar series")
                        }
                        IconButton(onClick = { onRemoveExercise(exercise) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar")
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddExercise) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir ejercicio")
                }
                Spacer(Modifier.weight(1f))
                if (day.exercises.isNotEmpty()) {
                    Button(onClick = onStartWorkout) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Empezar")
                    }
                }
            }
        }
    }
}

/** Mismo diálogo para crear y para editar: solo cambian título, valores y botón. */
@Composable
private fun DayDialog(
    title: String,
    initialName: String,
    initialWeekday: Int?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var weekday by remember { mutableStateOf(initialWeekday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Empuje") },
                    singleLine = true,
                )
                Text("Día de la semana (opcional)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Hace falta para que los recordatorios sepan cuándo avisarte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekdayNamesEs.forEachIndexed { index, dayName ->
                        val value = index + 1
                        FilterChip(
                            selected = weekday == value,
                            onClick = { weekday = if (weekday == value) null else value },
                            label = { Text(dayName) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, weekday) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EditExerciseDialog(
    exercise: RoutineExerciseItem,
    onDismiss: () -> Unit,
    onSave: (Int, Int?, Int?, Int?, Double?, Int) -> Unit,
) {
    var sets by remember { mutableStateOf(exercise.targetSets.toString()) }
    var repsMin by remember { mutableStateOf(exercise.targetRepsMin?.toString() ?: "") }
    var repsMax by remember { mutableStateOf(exercise.targetRepsMax?.toString() ?: "") }
    var timeSeconds by remember { mutableStateOf(exercise.targetTimeSeconds?.toString() ?: "") }
    var weight by remember { mutableStateOf(exercise.targetWeight?.toString() ?: "") }
    var rest by remember { mutableStateOf(exercise.restSeconds.toString()) }
    val isTimed = exercise.exerciseType == com.pelayo.gimnasio.domain.model.ExerciseType.TIME

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.exerciseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sets,
                    onValueChange = { sets = it.filter(Char::isDigit) },
                    label = { Text("Series") },
                    singleLine = true,
                )
                if (isTimed) {
                    OutlinedTextField(
                        value = timeSeconds,
                        onValueChange = { timeSeconds = it.filter(Char::isDigit) },
                        label = { Text("Segundos por serie") },
                        singleLine = true,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = repsMin,
                            onValueChange = { repsMin = it.filter(Char::isDigit) },
                            label = { Text("Reps mín.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = repsMax,
                            onValueChange = { repsMax = it.filter(Char::isDigit) },
                            label = { Text("Reps máx.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Peso objetivo (kg, opcional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rest,
                    onValueChange = { rest = it.filter(Char::isDigit) },
                    label = { Text("Descanso (s)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    sets.toIntOrNull() ?: exercise.targetSets,
                    repsMin.toIntOrNull(),
                    repsMax.toIntOrNull(),
                    timeSeconds.toIntOrNull(),
                    weight.toDoubleOrNull(),
                    rest.toIntOrNull() ?: exercise.restSeconds,
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
