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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.domain.model.ActivityLevel
import com.pelayo.gimnasio.domain.model.BodyMeasurementEntry
import com.pelayo.gimnasio.domain.model.BodyMetrics
import com.pelayo.gimnasio.domain.model.Sex
import com.pelayo.gimnasio.domain.model.UserProfileData
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.ui.components.LineChart
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val profile by viewModel.profile.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val measurements by viewModel.measurements.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var showMeasurementDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                actions = {
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar perfil")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
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
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            profile?.name ?: "Sin nombre",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        val p = profile
                        Text(
                            if (p?.heightCm != null && p.birthDate != null) {
                                "${p.sex.labelEs} · ${p.heightCm.roundToInt()} cm · ${p.activityLevel.labelEs}"
                            } else {
                                "Completa tus datos para ver tus métricas"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (p?.heightCm == null || p.birthDate == null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { showProfileDialog = true }) {
                                Text("Completar perfil")
                            }
                        }
                    }
                }
            }

            metrics?.let { m ->
                item { MetricsCard(m) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Peso y medidas", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showMeasurementDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Registrar")
                    }
                }
            }

            if (measurements.size >= 2) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Evolución del peso", style = MaterialTheme.typography.titleSmall)
                            LineChart(
                                // measurements viene de más reciente a más antiguo.
                                values = measurements.reversed().map { it.weightKg },
                                labels = measurements.reversed().map { it.date.toString() },
                                unit = " kg",
                            )
                        }
                    }
                }
            }

            if (measurements.isEmpty()) {
                item {
                    Text(
                        "Aún no has registrado tu peso. Necesito al menos un registro para calcular tus métricas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                measurements.forEach { entry ->
                    item(key = "m-${entry.id}") {
                        MeasurementRow(entry, onDelete = { viewModel.deleteMeasurement(entry.id) })
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileDialog(
            current = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { name, sex, birthDate, height, activity ->
                viewModel.saveProfile(name, sex, birthDate, height, activity)
                showProfileDialog = false
            },
        )
    }

    if (showMeasurementDialog) {
        MeasurementDialog(
            sex = profile?.sex ?: Sex.M,
            onDismiss = { showMeasurementDialog = false },
            onSave = { weight, neck, waist, hip ->
                viewModel.addMeasurement(weight, neck, waist, hip)
                showMeasurementDialog = false
            },
        )
    }
}

@Composable
private fun MetricsCard(metrics: BodyMetrics) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tus métricas", style = MaterialTheme.typography.titleMedium)

            MetricRow(
                label = "IMC",
                value = metrics.bmi.oneDecimal(),
                hint = metrics.bmiCategory.labelEs,
            )
            metrics.bodyFatPercent?.let { fat ->
                MetricRow(
                    label = "Grasa corporal",
                    value = "${fat.oneDecimal()} %",
                    hint = metrics.bodyFatMethod?.labelEs ?: "",
                )
            }
            metrics.leanMassKg?.let { lean ->
                MetricRow(label = "Masa magra", value = "${lean.oneDecimal()} kg", hint = "Peso sin grasa")
            }
            MetricRow(
                label = "Gasto basal (BMR)",
                value = "${metrics.bmr.roundToInt()} kcal",
                hint = "Lo que quemas en reposo (Mifflin-St Jeor)",
            )
            MetricRow(
                label = "Gasto diario (TDEE)",
                value = "${metrics.tdee.roundToInt()} kcal",
                hint = "Basal × factor de actividad",
            )

            Text(
                "Son estimaciones orientativas, no un diagnóstico médico.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MeasurementRow(entry: BodyMeasurementEntry, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${entry.weightKg.oneDecimal()} kg", style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(entry.date.toString())
                        entry.bodyFatPercent?.let { append(" · ${it.oneDecimal()} % grasa") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar")
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    current: UserProfileData?,
    onDismiss: () -> Unit,
    onSave: (String?, Sex, LocalDate?, Double?, ActivityLevel) -> Unit,
) {
    var name by remember { mutableStateOf(current?.name ?: "") }
    var sex by remember { mutableStateOf(current?.sex ?: Sex.M) }
    var birthDate by remember { mutableStateOf(current?.birthDate?.toString() ?: "") }
    var height by remember { mutableStateOf(current?.heightCm?.oneDecimal() ?: "") }
    var activity by remember { mutableStateOf(current?.activityLevel ?: ActivityLevel.LIGERO) }

    val parsedDate = runCatching { LocalDate.parse(birthDate) }.getOrNull()
    val dateError = birthDate.isNotBlank() && parsedDate == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tus datos") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sex.entries.forEach { option ->
                            FilterChip(
                                selected = sex == option,
                                onClick = { sex = option },
                                label = { Text(option.labelEs) },
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("Fecha de nacimiento") },
                        placeholder = { Text("AAAA-MM-DD") },
                        isError = dateError,
                        supportingText = if (dateError) {
                            { Text("Formato AAAA-MM-DD") }
                        } else {
                            null
                        },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Altura (cm)") },
                        singleLine = true,
                    )
                }
                item { Text("Nivel de actividad", style = MaterialTheme.typography.labelLarge) }
                items(ActivityLevel.entries.size) { index ->
                    val option = ActivityLevel.entries[index]
                    FilterChip(
                        selected = activity == option,
                        onClick = { activity = option },
                        label = { Text(option.labelEs) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, sex, parsedDate, height.toDoubleOrNull(), activity) },
                enabled = !dateError,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MeasurementDialog(
    sex: Sex,
    onDismiss: () -> Unit,
    onSave: (Double, Double?, Double?, Double?) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                )
                Text(
                    "Medidas opcionales: con cuello y cintura calculo la grasa por el método de la Marina, bastante más fiable que la estimación por IMC.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = neck,
                    onValueChange = { neck = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cuello (cm)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = waist,
                    onValueChange = { waist = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cintura (cm)") },
                    singleLine = true,
                )
                if (sex == Sex.F) {
                    OutlinedTextField(
                        value = hip,
                        onValueChange = { hip = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Cadera (cm)") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    weight.toDoubleOrNull()?.let {
                        onSave(it, neck.toDoubleOrNull(), waist.toDoubleOrNull(), hip.toDoubleOrNull())
                    }
                },
                enabled = weight.toDoubleOrNull() != null,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun Double.oneDecimal(): String = ((this * 10).roundToInt() / 10.0).toString()
