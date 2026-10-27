package com.pelayo.gimnasio.ui.workout.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.model.ExerciseListItem
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCatalogScreen(
    onExerciseClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CatalogViewModel = koinViewModel(),
) {
    val count by viewModel.exerciseCount.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val query by viewModel.query.collectAsState()
    val groupFilter by viewModel.muscleGroupFilter.collectAsState()
    val equipmentFilter by viewModel.equipmentFilter.collectAsState()
    val equipment by viewModel.equipment.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CustomExerciseDialog(
            title = "Crear ejercicio",
            onDismiss = { showCreateDialog = false },
            onConfirm = { form ->
                showCreateDialog = false
                viewModel.createCustomExercise(form) { id -> onExerciseClick(id) }
            },
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Crear ejercicio propio")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Ejercicios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Volver a sincronizar sin ir a Ajustes: es la única forma de
                    // traer los alias nuevos una vez descargado el catálogo.
                    if (count > 0L) {
                        IconButton(
                            onClick = viewModel::startSync,
                            enabled = syncState !is SyncUiState.Running,
                        ) {
                            Icon(
                                Icons.Filled.CloudDownload,
                                contentDescription = "Actualizar catálogo desde wger",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            // -1 es "aún no sé cuántos hay": sin esta rama la pantalla enseñaba
            // un "Sin resultados" fantasma mientras llegaba el primer conteo.
            count < 0L -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            count == 0L -> EmptyCatalog(
                syncState = syncState,
                onDownload = viewModel::startSync,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                (syncState as? SyncUiState.Running)?.let { running ->
                    if (running.total > 0) {
                        LinearProgressIndicator(
                            progress = { running.done.toFloat() / running.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar ejercicio…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.muscleGroups) { group ->
                        FilterChip(
                            selected = groupFilter == group.id,
                            onClick = {
                                viewModel.muscleGroupFilter.value =
                                    if (groupFilter == group.id) null else group.id
                            },
                            label = { Text(group.name) },
                        )
                    }
                }
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(equipment) { eq ->
                        FilterChip(
                            selected = equipmentFilter == eq.id,
                            onClick = {
                                viewModel.equipmentFilter.value =
                                    if (equipmentFilter == eq.id) null else eq.id
                            },
                            label = { Text(eq.nameEs) },
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
                            "Prueba con otro nombre o quita filtros.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(exercises, key = { it.id }) { exercise ->
                            ExerciseRow(exercise = exercise, onClick = { onExerciseClick(exercise.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseListItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (exercise.imageUrl != null) {
                AsyncImage(
                    model = exercise.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyCatalog(
    syncState: SyncUiState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.FitnessCenter,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Catálogo de ejercicios vacío", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Descarga el catálogo gratuito de wger (~700 ejercicios con imágenes e instrucciones). Solo hace falta una vez.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        when (syncState) {
            is SyncUiState.Running -> {
                if (syncState.total > 0) {
                    LinearProgressIndicator(
                        progress = { syncState.done.toFloat() / syncState.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${syncState.done} / ${syncState.total}")
                } else {
                    CircularProgressIndicator()
                }
            }

            is SyncUiState.Failed -> {
                Text(syncState.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDownload) { Text("Reintentar") }
            }

            // Terminó pero el catálogo sigue vacío (0 importados): decirlo es
            // mejor que volver a enseñar el mismo botón como si nada.
            is SyncUiState.Finished -> {
                Text("La descarga terminó con ${syncState.imported} ejercicios importados.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDownload) { Text("Reintentar") }
            }

            SyncUiState.Idle -> Button(onClick = onDownload) { Text("Descargar catálogo") }
        }
    }
}
