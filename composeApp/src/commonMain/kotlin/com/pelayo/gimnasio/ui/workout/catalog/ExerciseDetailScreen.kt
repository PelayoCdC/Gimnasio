package com.pelayo.gimnasio.ui.workout.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pelayo.gimnasio.ui.components.ConfirmDeleteDialog
import com.pelayo.gimnasio.ui.components.TagChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.MuscleRole
import com.pelayo.gimnasio.ui.components.LineChart
import com.pelayo.gimnasio.util.formatShortEs
import com.pelayo.gimnasio.util.oneDecimal
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Codificación mínima para meter el nombre del ejercicio en una URL de búsqueda. */
private fun String.urlEncode(): String = buildString {
    this@urlEncode.forEach { char ->
        when {
            char.isLetterOrDigit() && char.code < 128 -> append(char)
            char == ' ' -> append('+')
            else -> char.toString().encodeToByteArray().forEach { byte ->
                append('%')
                append(byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: Long,
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = koinViewModel { parametersOf(exerciseId) },
) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val records by viewModel.records.collectAsState()
    val uriHandler = LocalUriHandler.current
    var showEditDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteBlocked by remember { mutableStateOf(false) }

    val loadedDetail = (state as? ExerciseDetailUiState.Loaded)?.detail

    if (showEditDialog && loadedDetail != null) {
        CustomExerciseDialog(
            title = "Editar ejercicio",
            initial = CustomExerciseForm(
                name = loadedDetail.name,
                description = loadedDetail.description,
                exerciseType = loadedDetail.exerciseType,
                primaryGroupIds = loadedDetail.muscles
                    .filter { it.role == MuscleRole.PRIMARY }.map { it.muscleGroupId }.toSet(),
                secondaryGroupIds = loadedDetail.muscles
                    .filter { it.role == MuscleRole.SECONDARY }.map { it.muscleGroupId }.toSet(),
            ),
            onDismiss = { showEditDialog = false },
            onConfirm = { form ->
                showEditDialog = false
                viewModel.updateCustomExercise(form)
            },
        )
    }

    if (confirmDelete && loadedDetail != null) {
        ConfirmDeleteDialog(
            title = "¿Borrar \"${loadedDetail.name}\"?",
            text = "Solo se puede borrar si no está en ninguna rutina ni en el historial.",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteCustomExercise { deleted ->
                    if (deleted) onBack() else deleteBlocked = true
                }
            },
        )
    }

    if (deleteBlocked) {
        AlertDialog(
            onDismissRequest = { deleteBlocked = false },
            title = { Text("No se puede borrar") },
            text = {
                Text(
                    "Este ejercicio está en alguna rutina o en tu historial de " +
                        "entrenamientos. Quítalo de las rutinas primero; el historial " +
                        "no se reescribe.",
                )
            },
            confirmButton = { TextButton(onClick = { deleteBlocked = false }) { Text("Vale") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loadedDetail?.name ?: "Ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Los propios se editan y se borran; los de wger no se tocan.
                    if (loadedDetail?.isCustom == true) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar ejercicio")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar ejercicio")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ExerciseDetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            ExerciseDetailUiState.NotFound -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Ejercicio no encontrado") }

            is ExerciseDetailUiState.Loaded -> {
                val d = s.detail
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (d.images.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(d.images) { url ->
                                AsyncImage(
                                    model = url,
                                    // El carrusel es el contenido principal de la
                                    // ficha; no puede ser invisible para TalkBack.
                                    contentDescription = "Foto de ${d.name}",
                                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }

                    if (d.muscles.isNotEmpty()) {
                        Column {
                            Text("Músculos trabajados", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.muscles.sortedBy { it.role }.forEach { muscle ->
                                    TagChip(
                                        label = muscle.nameEs,
                                        emphasized = muscle.role == MuscleRole.PRIMARY,
                                    )
                                }
                            }
                        }
                    }

                    if (d.equipment.isNotEmpty()) {
                        Column {
                            Text("Material", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                d.equipment.joinToString(" · ") { it.nameEs },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!d.description.isNullOrBlank()) {
                        Column {
                            Text("Instrucciones", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(d.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Con una sola sesión ya hay marca que enseñar; la gráfica
                    // necesita dos, pero "tu mejor marca" no.
                    records?.let { pr ->
                        Column {
                            Text("Tus marcas", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            val isTimed = d.exerciseType == ExerciseType.TIME
                            Text(
                                buildString {
                                    if (isTimed) {
                                        pr.maxTimeSeconds?.let { append("Mejor tiempo: $it s") }
                                    } else {
                                        pr.maxWeightKg?.let { append("Mejor peso: ${it.oneDecimal()} kg") }
                                        pr.maxReps?.let {
                                            if (isNotEmpty()) append(" · ")
                                            append("Más repeticiones: $it")
                                        }
                                        pr.bestEstimated1RM?.let {
                                            if (isNotEmpty()) append(" · ")
                                            append("1RM estimado: ${it.oneDecimal()} kg")
                                        }
                                    }
                                    if (isEmpty()) append("Aún sin marcas con carga")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "En ${pr.sessions} ${if (pr.sessions == 1) "sesión" else "sesiones"}. " +
                                    "El 1RM es una estimación (Epley), no un máximo probado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (history.size >= 2) {
                        Column {
                            Text("Progreso", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            val isTimed = d.exerciseType == ExerciseType.TIME
                            // El 1RM estimado es la curva honesta: el peso máximo a
                            // secas baja si subes repeticiones bajando el peso.
                            val use1RM = !isTimed && history.all { it.best1RM != null }
                            Text(
                                when {
                                    isTimed -> "Tiempo máximo por sesión"
                                    use1RM -> "1RM estimado por sesión"
                                    else -> "Peso máximo por sesión"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LineChart(
                                values = history.map {
                                    when {
                                        isTimed -> it.maxTimeSeconds.toDouble()
                                        use1RM -> it.best1RM ?: 0.0
                                        else -> it.maxWeight
                                    }
                                },
                                labels = history.map { it.date.formatShortEs() },
                                unit = if (isTimed) " s" else " kg",
                            )
                        }
                    }

                    if (d.videoUrl != null) {
                        Button(onClick = { uriHandler.openUri(d.videoUrl) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("Ver vídeo")
                        }
                    } else {
                        // wger apenas tiene vídeos propios, así que se ofrece la búsqueda.
                        OutlinedButton(
                            onClick = {
                                val query = "como hacer ${d.name} tecnica ejercicio"
                                uriHandler.openUri(
                                    "https://www.youtube.com/results?search_query=${query.urlEncode()}",
                                )
                            },
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("Buscar vídeo en YouTube")
                        }
                    }

                    if (d.description.isNullOrBlank()) {
                        Text(
                            "Este ejercicio no trae instrucciones en el catálogo de wger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
