package com.pelayo.gimnasio.ui.workout.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import com.pelayo.gimnasio.ui.components.TagChip
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.calc.RoutineGenerator
import com.pelayo.gimnasio.domain.calc.RoutineSuggestion
import com.pelayo.gimnasio.domain.calc.SuggestionKind
import com.pelayo.gimnasio.domain.model.RoutineDayInfo
import com.pelayo.gimnasio.domain.model.RoutineDetail
import com.pelayo.gimnasio.domain.model.RoutineExerciseItem
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.RoutineSummary
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import com.pelayo.gimnasio.ui.components.BalancePanel
import com.pelayo.gimnasio.ui.components.ConfirmDeleteDialog
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
    val alternatives by viewModel.alternatives.collectAsState()
    val alternativesLoading by viewModel.alternativesLoading.collectAsState()
    val otherRoutines by viewModel.otherRoutines.collectAsState()
    var showAddDayDialog by remember { mutableStateOf(false) }
    var showEditRoutine by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<RoutineExerciseItem?>(null) }
    var exerciseToSwap by remember { mutableStateOf<RoutineExerciseItem?>(null) }
    var dayToEdit by remember { mutableStateOf<RoutineDayInfo?>(null) }
    var dayToCopy by remember { mutableStateOf<RoutineDayInfo?>(null) }
    var dayToDelete by remember { mutableStateOf<RoutineDayInfo?>(null) }
    var exerciseToRemove by remember { mutableStateOf<RoutineExerciseItem?>(null) }
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
                actions = {
                    IconButton(onClick = { showEditRoutine = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar rutina")
                    }
                    IconButton(
                        onClick = {
                            viewModel.exportRoutine { message ->
                                if (message.isNotEmpty()) {
                                    scope.launch { snackbarHost.showSnackbar(message) }
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Exportar rutina")
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
                    TagChip(label = current.goal.labelEs)
                    Spacer(Modifier.width(8.dp))
                    if (current.isActive) {
                        TagChip(label = "Activa", icon = Icons.Filled.CheckCircle)
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
                        onDeleteDay = { dayToDelete = day },
                        onEditExercise = { exerciseToEdit = it },
                        onOpenExercise = { onOpenExercise(it.exerciseId) },
                        onRemoveExercise = { exerciseToRemove = it },
                        onSwapExercise = {
                            exerciseToSwap = it
                            viewModel.loadAlternatives(it)
                        },
                        onReorder = { ids -> viewModel.reorderExercises(day.id, ids) },
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

    dayToDelete?.let { day ->
        ConfirmDeleteDialog(
            title = "¿Borrar \"${day.name}\"?",
            text = if (day.exercises.isEmpty()) {
                "El día está vacío y se puede borrar sin perder nada."
            } else {
                "Se borrará el día con sus ${day.exercises.size} ejercicios y sus series objetivo. " +
                    "El historial de entrenamientos no se toca."
            },
            onDismiss = { dayToDelete = null },
            onConfirm = {
                viewModel.deleteDay(day.id)
                dayToDelete = null
            },
        )
    }

    exerciseToRemove?.let { exercise ->
        ConfirmDeleteDialog(
            title = "¿Quitar ${exercise.exerciseName}?",
            text = "Se quita de este día con sus series objetivo. El historial no se toca.",
            confirmLabel = "Quitar",
            onDismiss = { exerciseToRemove = null },
            onConfirm = {
                viewModel.removeExercise(exercise.id)
                exerciseToRemove = null
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

    if (showEditRoutine) {
        routine?.let { current ->
            EditRoutineDialog(
                routine = current,
                onDismiss = { showEditRoutine = false },
                onSave = { name, goal, sets ->
                    viewModel.renameRoutine(name, goal, sets)
                    showEditRoutine = false
                },
            )
        }
    }

    exerciseToSwap?.let { exercise ->
        SwapExerciseDialog(
            exercise = exercise,
            alternatives = alternatives,
            loading = alternativesLoading,
            onDismiss = {
                exerciseToSwap = null
                viewModel.clearAlternatives()
            },
            onPick = { alternative ->
                viewModel.replaceExercise(exercise.id, alternative.exerciseId)
                exerciseToSwap = null
            },
        )
    }
}

/**
 * Cambia el nombre y el objetivo de la rutina. El objetivo no rehace lo que ya
 * está: solo manda en lo que se añada a partir de ahora, y por eso se dice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditRoutineDialog(
    routine: RoutineDetail,
    onDismiss: () -> Unit,
    onSave: (String, RoutineGoal, Int?) -> Unit,
) {
    var name by remember(routine.id) { mutableStateOf(routine.name) }
    var goal by remember(routine.id) { mutableStateOf(routine.goal) }

    // Las series que tiene ahora la rutina: la cifra que más se repite. Si hay
    // ejercicios con distintas series, se parte de null y no se toca nada salvo
    // que el usuario elija una.
    val currentSets = remember(routine) {
        routine.days.flatMap { day -> day.exercises.map { it.targetSets } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }
    var sets by remember(routine.id) { mutableStateOf(currentSets) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar rutina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                Text("Objetivo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineGoal.entries.forEach { option ->
                        FilterChip(
                            selected = goal == option,
                            onClick = { goal = option },
                            label = { Text(option.labelEs) },
                        )
                    }
                }
                if (goal != routine.goal) {
                    Text(
                        "Las repeticiones que ya tienes se quedan como están; el objetivo " +
                            "nuevo se aplica a lo que añadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text("Series por ejercicio", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineGenerator.SET_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = sets == option,
                            onClick = { sets = option },
                            label = { Text("$option") },
                        )
                    }
                }
                Text(
                    if (sets != null && sets != currentSets) {
                        "Se aplica a todos los ejercicios de la rutina; luego puedes " +
                            "ajustar los que quieras uno a uno."
                    } else if (currentSets == null) {
                        "Aún no hay ejercicios a los que ponérselas."
                    } else {
                        "Elige otra cifra para cambiárselas a todos los ejercicios."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), goal, sets.takeIf { it != currentSets }) },
                enabled = name.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Cambia un ejercicio por otro del mismo grupo muscular, conservando series,
 * descanso y posición. Lo de arriba se parece más a lo que había.
 */
@Composable
private fun SwapExerciseDialog(
    exercise: RoutineExerciseItem,
    alternatives: List<ExerciseAlternative>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onPick: (ExerciseAlternative) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar ${exercise.exerciseName}") },
        text = {
            if (loading && alternatives.isEmpty()) {
                // Sin esto el diálogo decía "no encuentro alternativas" durante el
                // instante en que aún se estaban buscando.
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (alternatives.isEmpty()) {
                Text(
                    "No encuentro otros ejercicios del mismo grupo muscular que no estén ya " +
                        "en la rutina.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.height(340.dp)) {
                    items(alternatives.size) { index ->
                        val alternative = alternatives[index]
                        Card(
                            onClick = { onPick(alternative) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(alternative.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    buildString {
                                        append(alternative.styleLabel)
                                        append(if (alternative.isCompound) " · compuesto" else " · aislamiento")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
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
    onSwapExercise: (RoutineExerciseItem) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onStartWorkout: () -> Unit,
) {
    // Reordenado por arrastre: el orden vive local mientras se arrastra y se
    // escribe de una vez al soltar, para no disparar recargas a mitad de gesto.
    var localOrder by remember(day.exercises) { mutableStateOf(day.exercises) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<Long, Int>() }

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
                localOrder.forEach { exercise ->
                    key(exercise.id) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .zIndex(if (exercise.id == draggingId) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (exercise.id == draggingId) dragOffset else 0f
                            }
                            .onSizeChanged { rowHeights[exercise.id] = it.height },
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

                        // Mantener pulsado y arrastrar sustituye a las flechas
                        // toque-a-toque; el orden se guarda al soltar.
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription =
                                "Mantén pulsado y arrastra para mover ${exercise.exerciseName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(36.dp)
                                .padding(6.dp)
                                .pointerInput(exercise.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = exercise.id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val height = rowHeights[exercise.id] ?: return@detectDragGesturesAfterLongPress
                                            val threshold = height * 0.66f
                                            var index = localOrder.indexOfFirst { it.id == exercise.id }
                                            while (dragOffset > threshold && index < localOrder.lastIndex) {
                                                localOrder = localOrder.toMutableList()
                                                    .apply { add(index + 1, removeAt(index)) }
                                                dragOffset -= height
                                                index++
                                            }
                                            while (dragOffset < -threshold && index > 0) {
                                                localOrder = localOrder.toMutableList()
                                                    .apply { add(index - 1, removeAt(index)) }
                                                dragOffset += height
                                                index--
                                            }
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                            onReorder(localOrder.map { it.id })
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                            localOrder = day.exercises
                                        },
                                    )
                                },
                        )
                        IconButton(onClick = { onSwapExercise(exercise) }) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = "Cambiar ${exercise.exerciseName} por otro",
                            )
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
            // Deshabilitado en vez de guardar en silencio el valor anterior: las
            // series vacías o un peso tipo "1.2.3" se ven, no se corrigen a escondidas.
            val setsValid = sets.toIntOrNull()?.let { it > 0 } == true
            val restValid = rest.toIntOrNull() != null
            val weightValid = weight.isBlank() || weight.toDoubleOrNull() != null
            TextButton(
                enabled = setsValid && restValid && weightValid,
                onClick = {
                    onSave(
                        sets.toIntOrNull() ?: exercise.targetSets,
                        repsMin.toIntOrNull(),
                        repsMax.toIntOrNull(),
                        timeSeconds.toIntOrNull(),
                        weight.toDoubleOrNull(),
                        rest.toIntOrNull() ?: exercise.restSeconds,
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
