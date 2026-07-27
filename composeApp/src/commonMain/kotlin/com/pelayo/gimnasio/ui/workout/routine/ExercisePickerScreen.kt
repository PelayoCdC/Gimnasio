package com.pelayo.gimnasio.ui.workout.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.model.ExerciseDetail
import com.pelayo.gimnasio.domain.model.MuscleRole
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    dayId: Long,
    routineId: Long,
    onDone: () -> Unit,
    viewModel: ExercisePickerViewModel = koinViewModel { parametersOf(dayId, routineId) },
) {
    val exercises by viewModel.exercises.collectAsState()
    val query by viewModel.query.collectAsState()
    val groupFilter by viewModel.muscleGroupFilter.collectAsState()
    val preview by viewModel.preview.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Añadir ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar ejercicio…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.muscleGroups) { group ->
                    FilterChip(
                        selected = groupFilter == group.id,
                        onClick = { viewModel.setMuscleGroup(group.id) },
                        label = { Text(group.name) },
                    )
                }
            }
            if (exercises.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Sin resultados", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Si el catálogo está vacío, descárgalo desde Entrenamiento → Catálogo de ejercicios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(exercises, key = { it.id }) { exercise ->
                        // La tarjeta abre la ficha; añadir es el botón de la derecha,
                        // para poder mirar un ejercicio sin meterlo en la rutina.
                        Card(
                            onClick = { viewModel.showPreview(exercise.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (exercise.imageUrl != null) {
                                    AsyncImage(
                                        model = exercise.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.FitnessCenter,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).padding(10.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    exercise.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.addExercise(exercise.id, onDone) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "Añadir ${exercise.name}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    preview?.let { detail ->
        ExercisePreviewSheet(
            detail = detail,
            onDismiss = viewModel::hidePreview,
            onAdd = {
                viewModel.hidePreview()
                viewModel.addExercise(detail.id, onDone)
            },
        )
    }
}

/**
 * Ficha del ejercicio dentro del propio buscador: fotos, músculos, material e
 * instrucciones, con el botón de añadir al final. Evita el viaje de añadirlo
 * para verlo y quitarlo si no era el que se buscaba.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePreviewSheet(
    detail: ExerciseDetail,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(detail.name, style = MaterialTheme.typography.titleLarge)

            if (detail.images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(detail.images) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }

            if (detail.muscles.isNotEmpty()) {
                Column {
                    Text("Músculos trabajados", style = MaterialTheme.typography.titleSmall)
                    Text(
                        detail.muscles.sortedBy { it.role }.joinToString(" · ") { muscle ->
                            if (muscle.role == MuscleRole.PRIMARY) {
                                muscle.nameEs
                            } else {
                                "${muscle.nameEs} (secundario)"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (detail.equipment.isNotEmpty()) {
                Column {
                    Text("Material", style = MaterialTheme.typography.titleSmall)
                    Text(
                        detail.equipment.joinToString(" · ") { it.nameEs },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column {
                Text("Instrucciones", style = MaterialTheme.typography.titleSmall)
                Text(
                    detail.description?.takeIf { it.isNotBlank() }
                        ?: "Este ejercicio no trae instrucciones en el catálogo de wger.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir a la rutina")
            }
        }
    }
}
