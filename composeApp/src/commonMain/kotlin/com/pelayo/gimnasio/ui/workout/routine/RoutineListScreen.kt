package com.pelayo.gimnasio.ui.workout.routine

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.RoutineSummary
import com.pelayo.gimnasio.domain.model.labelEs
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    onRoutineClick: (Long) -> Unit,
    onGenerateRoutine: () -> Unit,
    onBack: () -> Unit,
    viewModel: RoutineListViewModel = koinViewModel(),
) {
    val routines by viewModel.routines.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<RoutineSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis rutinas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva rutina")
            }
        },
    ) { padding ->
        if (routines.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Aún no tienes rutinas", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crea una rutina, añade días y ejercicios, y te avisaré si algún grupo muscular se queda fuera.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGenerateRoutine) { Text("Generar una automáticamente") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedButton(onClick = onGenerateRoutine, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generar rutina automáticamente")
                    }
                }
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onClick = { onRoutineClick(routine.id) },
                        onSetActive = { viewModel.setActive(routine.id) },
                        onDuplicate = { viewModel.duplicateRoutine(routine.id, onRoutineClick) },
                        onDelete = { routineToDelete = routine },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoutineDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, goal ->
                showCreateDialog = false
                viewModel.createRoutine(name, goal) { id -> onRoutineClick(id) }
            },
        )
    }

    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Borrar rutina") },
            text = { Text("¿Seguro que quieres borrar \"${routine.name}\"? Se perderán sus días y ejercicios.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRoutine(routine.id)
                    routineToDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineSummary,
    onClick: () -> Unit,
    onSetActive: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${routine.dayCount} días · ${routine.exerciseCount} ejercicios · ${routine.goal.labelEs}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Borrar")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (routine.isActive) {
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
                TextButton(onClick = onSetActive) { Text("Marcar como activa") }
            }
        }
    }
}

@Composable
private fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onCreate: (String, RoutineGoal) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(RoutineGoal.HIPERTROFIA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva rutina") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Full body 3 días") },
                    singleLine = true,
                )
                Text("Objetivo", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoutineGoal.entries.forEach { option ->
                        FilterChip(
                            selected = goal == option,
                            onClick = { goal = option },
                            label = { Text(option.labelEs) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, goal) }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
