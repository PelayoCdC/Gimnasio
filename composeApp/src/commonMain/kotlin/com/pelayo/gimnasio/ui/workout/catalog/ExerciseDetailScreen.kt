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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
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
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? ExerciseDetailUiState.Loaded)?.detail?.name ?: "Ejercicio")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                                    contentDescription = null,
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
                                    if (muscle.role == MuscleRole.PRIMARY) {
                                        AssistChip(onClick = {}, label = { Text(muscle.nameEs) })
                                    } else {
                                        SuggestionChip(onClick = {}, label = { Text(muscle.nameEs) })
                                    }
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

                    if (history.size >= 2) {
                        Column {
                            Text("Progreso", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            val isTimed = d.exerciseType == ExerciseType.TIME
                            Text(
                                if (isTimed) "Tiempo máximo por sesión" else "Peso máximo por sesión",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LineChart(
                                values = history.map {
                                    if (isTimed) it.maxTimeSeconds.toDouble() else it.maxWeight
                                },
                                labels = history.map { it.date.toString() },
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
