package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.domain.model.FoodUnit
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.labelEs

/**
 * Edita una cantidad ya apuntada, en el diario o en un plan de dieta. Deja cambiar
 * también de comida, que es el otro fallo típico: apuntar la cena en la comida.
 *
 * La cantidad son gramos para un alimento y raciones para una receta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditQuantityDialog(
    title: String,
    isRecipe: Boolean,
    unit: FoodUnit?,
    initialAmount: Double,
    initialMeal: MealType,
    onDismiss: () -> Unit,
    onConfirm: (MealType, Double) -> Unit,
) {
    var amount by remember { mutableStateOf(formatAmount(initialAmount)) }
    var meal by remember { mutableStateOf(initialMeal) }
    val parsed = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = {
                        Text(
                            if (isRecipe) {
                                "Raciones"
                            } else {
                                "Cantidad (${(unit ?: FoodUnit.G).labelEs})"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("Comida", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { option ->
                        FilterChip(
                            selected = meal == option,
                            onClick = { meal = option },
                            label = { Text(option.labelEs) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(meal, it) } },
                enabled = parsed != null && parsed > 0,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Sin decimales cuando no hacen falta, que es lo normal en gramos. */
private fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
