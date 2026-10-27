package com.pelayo.gimnasio.ui.workout.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.MuscleGroups

/** Lo que se rellena al crear o editar un ejercicio propio. */
data class CustomExerciseForm(
    val name: String,
    val description: String?,
    val exerciseType: ExerciseType,
    val primaryGroupIds: Set<Long>,
    val secondaryGroupIds: Set<Long>,
)

/**
 * Alta y edición de ejercicios propios. La app llevaba desde el principio
 * diciendo "puedes crearlos a mano" en la importación de rutinas sin tener
 * ninguna pantalla que lo hiciera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseDialog(
    title: String,
    initial: CustomExerciseForm? = null,
    onDismiss: () -> Unit,
    onConfirm: (CustomExerciseForm) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var type by remember { mutableStateOf(initial?.exerciseType ?: ExerciseType.REPS) }
    var primary by remember { mutableStateOf(initial?.primaryGroupIds ?: emptySet()) }
    var secondary by remember { mutableStateOf(initial?.secondaryGroupIds ?: emptySet()) }

    fun toggle(set: Set<Long>, id: Long): Set<Long> =
        if (id in set) set - id else set + id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Remo en máquina del gimnasio") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notas de técnica (opcional)") },
                    minLines = 2,
                )

                Text("Se registra por", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ExerciseType.REPS,
                        onClick = { type = ExerciseType.REPS },
                        label = { Text("Repeticiones") },
                    )
                    FilterChip(
                        selected = type == ExerciseType.TIME,
                        onClick = { type = ExerciseType.TIME },
                        label = { Text("Tiempo") },
                    )
                }

                Text("Músculos principales", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroups.all.forEach { group ->
                        FilterChip(
                            selected = group.id in primary,
                            onClick = {
                                primary = toggle(primary, group.id)
                                secondary = secondary - group.id
                            },
                            label = { Text(group.name) },
                        )
                    }
                }

                Text("Secundarios (opcional)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroups.all.forEach { group ->
                        FilterChip(
                            selected = group.id in secondary,
                            onClick = {
                                secondary = toggle(secondary, group.id)
                                primary = primary - group.id
                            },
                            label = { Text(group.name) },
                        )
                    }
                }
                Text(
                    "Con al menos un músculo principal el ejercicio cuenta en el " +
                        "balance de la rutina y en el mapa muscular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && primary.isNotEmpty(),
                onClick = {
                    onConfirm(
                        CustomExerciseForm(
                            name = name.trim(),
                            description = description.trim().takeIf { it.isNotBlank() },
                            exerciseType = type,
                            primaryGroupIds = primary,
                            secondaryGroupIds = secondary,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
