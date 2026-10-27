package com.pelayo.gimnasio.ui.workout.active

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.platform.AppBackHandler
import com.pelayo.gimnasio.platform.KeepScreenOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.WorkoutExerciseProgress
import com.pelayo.gimnasio.domain.model.formatKg
import com.pelayo.gimnasio.util.formatShortEs
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    val stopwatchStarts by viewModel.stopwatchStarts.collectAsState()
    val finished by viewModel.finished.collectAsState()
    var showFinishDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Con las manos ocupadas y descansos de minutos, que la pantalla se apague
    // a mitad de serie es un fastidio real.
    KeepScreenOn()

    LaunchedEffect(finished) {
        if (finished) onFinished(sessionId)
    }

    // El gesto o botón de atrás sale igual que la X de la barra: sin esto la
    // sesión vacía quedaba huérfana y reaparecía en Inicio como "sin terminar".
    AppBackHandler {
        viewModel.discardIfEmpty()
        onCancel()
    }

    // Snackbar con deshacer en vez de diálogo: para una serie suelta, confirmar
    // molesta más de lo que protege, y reinsertarla recupera lo mismo.
    fun deleteWithUndo(set: com.pelayo.gimnasio.domain.model.SetLogItem) {
        viewModel.deleteSet(set.id)
        scope.launch {
            val result = snackbarHost.showSnackbar(
                message = "Serie ${set.setNumber} borrada",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreSet(set)
        }
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
        snackbarHost = { SnackbarHost(snackbarHost) },
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
                        stopwatchStartMillis = stopwatchStarts[exercise.exerciseId],
                        onStartStopwatch = { viewModel.startStopwatch(exercise.exerciseId) },
                        onStopStopwatch = { viewModel.stopStopwatch(exercise.exerciseId) },
                        onLogSet = { weight, reps, time, warmup ->
                            viewModel.logSet(
                                exerciseId = exercise.exerciseId,
                                setNumber = exercise.loggedSets.size + 1,
                                weightKg = weight,
                                reps = reps,
                                timeSeconds = time,
                                restSeconds = exercise.restSeconds,
                                exerciseName = exercise.exerciseName,
                                isWarmup = warmup,
                            )
                        },
                        onDeleteSet = ::deleteWithUndo,
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
    stopwatchStartMillis: Long?,
    onStartStopwatch: () -> Unit,
    onStopStopwatch: () -> Int,
    onLogSet: (weight: Double?, reps: Int?, time: Int?, isWarmup: Boolean) -> Unit,
    onDeleteSet: (com.pelayo.gimnasio.domain.model.SetLogItem) -> Unit,
    onOpenExercise: () -> Unit,
) {
    val isTimed = exercise.exerciseType == ExerciseType.TIME
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // A un crunch no hay kilos que ponerle, pero a unos fondos sí: el campo se
    // esconde y se puede sacar de un toque.
    var showWeight by rememberSaveable(exercise.exerciseId) { mutableStateOf(!exercise.isBodyweight) }
    // Se marca antes de registrar una serie de aproximación; se desmarca solo al
    // apuntarla (la clave incluye el número de series).
    var warmup by remember(exercise.loggedSets.size) { mutableStateOf(false) }

    // La tarjeta se pliega sola al completar el objetivo: una sesión de siete
    // ejercicios era un scroll kilométrico con todo abierto. Tocar la cabecera
    // la reabre para seguir apuntando series extra.
    var expanded by rememberSaveable(exercise.exerciseId) { mutableStateOf(true) }
    LaunchedEffect(exercise.isComplete) {
        if (exercise.isComplete) expanded = false
    }
    // Prerrelleno: manda lo último apuntado hoy (entre series casi siempre se
    // repite peso), después la serie equivalente de la última sesión y, si no,
    // el objetivo de la rutina. Los calentamientos no sirven de referencia: su
    // peso ligero no debe colarse como defecto de una serie de trabajo, y por
    // eso el índice contra la sesión anterior cuenta solo series efectivas.
    val reference = exercise.loggedSets.lastOrNull { !it.isWarmup }
        ?: exercise.previousSets.getOrNull(exercise.effectiveSets)
        ?: exercise.previousSets.lastOrNull()
    var weight by remember(exercise.loggedSets.size) {
        mutableStateOf(reference?.weightKg?.formatKg() ?: "")
    }
    var reps by remember(exercise.loggedSets.size) {
        mutableStateOf((reference?.reps ?: exercise.targetRepsMax)?.toString() ?: "")
    }
    var time by remember(exercise.loggedSets.size) {
        mutableStateOf((reference?.timeSeconds ?: exercise.targetTimeSeconds)?.toString() ?: "")
    }

    // El cambio de color al completar se anima en vez de saltar de golpe.
    val containerColor by animateColorAsState(
        targetValue = if (exercise.isComplete) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 400),
    )

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        "Objetivo: ${exercise.effectiveSets}/${exercise.targetSets} series",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // El check entra con un pequeño rebote: es el momento de premio.
                AnimatedVisibility(
                    visible = exercise.isComplete,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(),
                ) {
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
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Plegar" else "Desplegar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!expanded) return@Column

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

            // El equivalente para los isométricos: la plancha también progresa.
            exercise.timeProgression?.takeIf { exercise.loggedSets.isEmpty() }?.let { suggestion ->
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
                                "Toca subir a ${suggestion.newTimeSeconds} s",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                suggestion.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        TextButton(onClick = { time = suggestion.newTimeSeconds.toString() }) {
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
                        if (set.isWarmup) "${set.label} · calent." else set.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteSet(set) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Borrar serie",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Lo de la vez anterior se queda a la vista todo el rato, no solo antes
            // de empezar: es contra lo que compites serie a serie.
            if (exercise.previousSets.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            buildString {
                                append("Última vez")
                                exercise.previousDate?.let { append(" (${formatShortDate(it)})") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        exercise.previousSets.forEachIndexed { index, set ->
                            // Series efectivas: la lista anterior no trae
                            // calentamientos, así que apuntar uno no debe
                            // adelantar la marca.
                            val isNext = index == exercise.effectiveSets
                            Text(
                                "Serie ${index + 1}: ${set.label}",
                                style = MaterialTheme.typography.bodySmall,
                                // La que toca ahora va marcada: es la referencia
                                // que hay que igualar o superar.
                                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                                color = if (isNext) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
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
                    // Con las manos en el suelo no se teclea: se cuenta y se para.
                    StopwatchButton(
                        target = exercise.targetTimeSeconds,
                        startMillis = stopwatchStartMillis,
                        onStart = onStartStopwatch,
                        onStop = {
                            val seconds = onStopStopwatch()
                            if (seconds > 0) time = seconds.toString()
                        },
                    )
                } else {
                    if (showWeight) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("kg") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                        // Al registrar, fuera el foco y el teclado: la serie ya
                        // está apuntada y el teclado abierto tapa media lista.
                        focusManager.clearFocus()
                        keyboard?.hide()
                        if (isTimed) {
                            onLogSet(null, null, time.toIntOrNull(), warmup)
                        } else {
                            onLogSet(weight.toDoubleOrNull(), reps.toIntOrNull(), null, warmup)
                        }
                    },
                    enabled = if (isTimed) time.toIntOrNull() != null else reps.toIntOrNull() != null,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Registrar serie")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isTimed && !showWeight) {
                    TextButton(
                        onClick = { showWeight = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("Añadir peso") }
                }
                Spacer(Modifier.weight(1f))
                // Serie de aproximación: no cuenta para el objetivo ni el volumen,
                // no dispara el descanso completo y no engaña a la progresión.
                FilterChip(
                    selected = warmup,
                    onClick = { warmup = !warmup },
                    label = { Text("Calentamiento") },
                )
            }
        }
    }
}

/**
 * Cronómetro para las series que se miden en tiempo. Cuenta hacia arriba y, al
 * parar, deja los segundos en el campo: en una plancha no se teclea a la vez que
 * se aguanta, y contar de cabeza sale mal.
 *
 * El instante de arranque vive en el ViewModel (sobrevive a que el sistema
 * recree la actividad al cambiar de app) y aquí solo se pinta: el bucle va
 * enganchado al reloj de frames, que en segundo plano se para y al volver al
 * primer plano recalcula la cuenta contra el reloj de pared en el primer frame.
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun StopwatchButton(
    target: Int?,
    startMillis: Long?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val running = startMillis != null

    fun elapsedNow(): Int = if (startMillis == null) 0 else {
        ((Clock.System.now().toEpochMilliseconds() - startMillis) / 1000).toInt()
    }

    // Con clave: al replegarse y reabrirse la tarjeta, la cuenta sale bien
    // desde el primer pintado en vez de arrancar en cero.
    var elapsed by remember(startMillis) { mutableStateOf(elapsedNow()) }

    LaunchedEffect(startMillis) {
        if (startMillis == null) return@LaunchedEffect
        while (true) {
            withFrameMillis { elapsed = elapsedNow() }
            delay(200)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { if (running) onStop() else onStart() },
        ) {
            Icon(
                if (running) Icons.Filled.Stop else Icons.Filled.Timer,
                contentDescription = if (running) "Parar el cronómetro" else "Cronometrar la serie",
            )
        }
        if (running) {
            Text(
                formatSeconds(elapsed),
                style = MaterialTheme.typography.labelLarge,
                color = if (target != null && elapsed >= target) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
    val animatedProgress = animateFloatAsState(
        targetValue = timer.progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
    )
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
                // Se vacía de forma continua en vez de a saltos de segundo: la
                // animación lineal de 1 s empalma con el siguiente tick.
                progress = {
                    animatedProgress.value
                },
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

/** "25 jul": lo justo para situar de cuándo son los números anteriores. */
private fun formatShortDate(date: LocalDate): String = date.formatShortEs()

private fun formatSeconds(total: Int): String {
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "$seconds s"
}
