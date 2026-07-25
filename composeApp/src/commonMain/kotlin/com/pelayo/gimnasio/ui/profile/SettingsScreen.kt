package com.pelayo.gimnasio.ui.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val backupState by viewModel.backupState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    var confirmImport by remember { mutableStateOf(false) }
    val working = backupState is BackupUiState.Working

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Copia de seguridad", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tus datos solo están en este móvil. Si lo pierdes o desinstalas la app, se pierden. " +
                                "Guarda una copia de vez en cuando en Drive o donde prefieras.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = viewModel::exportBackup,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar copia de seguridad")
                        }
                        OutlinedButton(
                            onClick = { confirmImport = true },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restaurar desde una copia")
                        }
                        if (working) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Trabajando…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Exportar a hoja de cálculo", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ficheros CSV que puedes abrir con Excel o Google Sheets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = viewModel::exportSetsCsv,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Historial de series") }
                        OutlinedButton(
                            onClick = viewModel::exportMeasurementsCsv,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Peso y medidas") }
                    }
                }
            }

            if (reminders.supported) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Recordatorios", style = MaterialTheme.typography.titleMedium)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Días de entreno", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (reminders.scheduledDays.isEmpty()) {
                                            "Asigna días de la semana a tu rutina para poder avisarte."
                                        } else {
                                            reminders.scheduledDays.joinToString(", ")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = reminders.workoutRemindersOn,
                                    onCheckedChange = viewModel::setWorkoutReminders,
                                    enabled = reminders.scheduledDays.isNotEmpty(),
                                )
                            }

                            if (reminders.workoutRemindersOn) {
                                Text(
                                    "Hora del aviso: ${reminders.hour}:00",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = reminders.hour.toFloat(),
                                    onValueChange = { viewModel.setReminderHour(it.toInt()) },
                                    valueRange = 6f..22f,
                                    steps = 15,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Pesarse cada semana", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Domingos a las 9:00",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = reminders.weighInReminderOn,
                                    onCheckedChange = viewModel::setWeighInReminder,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Catálogo de ejercicios", style = MaterialTheme.typography.titleMedium)
                        Text(
                            viewModel.lastCatalogSync?.let { "Última actualización: ${it.take(10)}" }
                                ?: "Todavía no lo has descargado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when (val state = syncState) {
                            is CatalogSyncUiState.Running -> {
                                if (state.total > 0) {
                                    LinearProgressIndicator(
                                        progress = { state.done.toFloat() / state.total },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text("${state.done} / ${state.total}", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                            is CatalogSyncUiState.Finished -> Text(
                                "${state.imported} ejercicios actualizados.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            is CatalogSyncUiState.Failed -> Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            CatalogSyncUiState.Idle -> Unit
                        }
                        OutlinedButton(
                            onClick = viewModel::syncCatalog,
                            enabled = syncState !is CatalogSyncUiState.Running,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Actualizar desde wger")
                        }
                    }
                }
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Restaurar copia") },
            text = {
                Text(
                    "Se sustituirán tus rutinas, entrenamientos, medidas y comidas por los de la copia. " +
                        "Lo que tengas ahora se perderá. El catálogo de ejercicios se conserva.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    viewModel.importBackup()
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("Cancelar") }
            },
        )
    }

    when (val state = backupState) {
        is BackupUiState.Message -> AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(if (state.isError) "No se pudo completar" else "Listo") },
            text = { Text(state.text) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("Vale") } },
        )

        is BackupUiState.Imported -> AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Copia restaurada") },
            text = {
                Column {
                    val s = state.summary
                    Text("${s.routines} rutinas, ${s.sessions} entrenamientos, ${s.measurements} medidas.")
                    Text("${s.recipes} recetas, ${s.dietPlans} planes de dieta y ${s.foodLogEntries} registros de comida.")
                    if (s.skippedExercises > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Se omitieron ${s.skippedExercises} series o ejercicios porque no están en tu " +
                                "catálogo actual. Actualiza el catálogo desde wger y vuelve a restaurar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("Vale") } },
        )

        else -> Unit
    }
}
