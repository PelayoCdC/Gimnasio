package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pelayo.gimnasio.domain.model.DietPlanItem
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.labelEs
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DietPlanScreen(
    onBack: () -> Unit,
    viewModel: DietPlanViewModel = koinViewModel(),
) {
    val plans by viewModel.plans.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val message by viewModel.message.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<MealType?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planes de dieta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Nuevo plan")
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
            if (plans.isEmpty()) {
                item {
                    Column(Modifier.padding(top = 32.dp)) {
                        Text("Sin planes de dieta", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Un plan es un menú tipo con objetivos de calorías y macros. " +
                                "Puedes volcarlo al diario de un día con un toque.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showCreate = true }) { Text("Crear plan") }
                    }
                }
            } else {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        plans.forEach { plan ->
                            FilterChip(
                                selected = selected?.id == plan.id,
                                onClick = { viewModel.selectPlan(plan.id) },
                                label = { Text(plan.name) },
                                leadingIcon = if (plan.isActive) {
                                    {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.width(18.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            selected?.let { plan ->
                item { PlanSummaryCard(plan) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!plan.isActive) {
                            OutlinedButton(onClick = { viewModel.setActive(plan.id) }) {
                                Text("Marcar como activo")
                            }
                        }
                        Button(onClick = viewModel::applyToToday) { Text("Aplicar a hoy") }
                    }
                }

                MealType.entries.forEach { meal ->
                    item(key = "plan-meal-${meal.name}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(meal.labelEs, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    val kcal = plan.mealsFor(meal).sumOf { it.macros.kcal }
                                    Text(
                                        "${kcal.roundToInt()} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                plan.mealsFor(meal).forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${item.quantityLabel} · ${item.macros.kcal.roundToInt()} kcal",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { viewModel.removeMeal(item.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Quitar")
                                        }
                                    }
                                }
                                TextButton(onClick = { addingTo = meal }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Añadir")
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.deletePlan(plan.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Borrar este plan") }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlanDialog(
            viewModel = viewModel,
            onDismiss = { showCreate = false },
            onCreate = { name, targets ->
                viewModel.createPlan(name, targets) { showCreate = false }
            },
        )
    }

    addingTo?.let { meal ->
        AddToPlanDialog(
            viewModel = viewModel,
            mealType = meal,
            onDismiss = { addingTo = null },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Plan de dieta") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("Vale") } },
        )
    }
}

@Composable
private fun PlanSummaryCard(plan: DietPlanItem) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.name, style = MaterialTheme.typography.titleLarge)

            val planned = plan.plannedMacros
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(
                        "Planificado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${planned.kcal.roundToInt()} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                plan.targetKcal?.let { target ->
                    Column {
                        Text(
                            "Objetivo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${target.roundToInt()} kcal",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            Text(
                "P ${planned.protein.roundToInt()} g · C ${planned.carbs.roundToInt()} g · " +
                    "G ${planned.fat.roundToInt()} g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            plan.targetKcal?.let { target ->
                val diff = planned.kcal - target
                if (kotlin.math.abs(diff) > 50) {
                    Text(
                        if (diff > 0) {
                            "El menú se pasa ${diff.roundToInt()} kcal del objetivo."
                        } else {
                            "Al menú le faltan ${(-diff).roundToInt()} kcal para el objetivo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatePlanDialog(
    viewModel: DietPlanViewModel,
    onDismiss: () -> Unit,
    onCreate: (String, Macros?) -> Unit,
) {
    val suggestion by viewModel.suggestion.collectAsState()
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(RoutineGoal.HIPERTROFIA) }
    val targets = viewModel.suggestionFor(goal)?.macros

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo plan de dieta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Volumen invierno") },
                    singleLine = true,
                )

                if (suggestion == null) {
                    Text(
                        "Completa tu perfil y registra tu peso y te propondré objetivos de calorías y macros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Objetivo", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            RoutineGoal.PERDIDA_GRASA,
                            RoutineGoal.HIPERTROFIA,
                            RoutineGoal.RESISTENCIA,
                        ).forEach { option ->
                            FilterChip(
                                selected = goal == option,
                                onClick = { goal = option },
                                label = { Text(option.labelEs) },
                            )
                        }
                    }
                    targets?.let {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${it.kcal.roundToInt()} kcal · P ${it.protein.roundToInt()} " +
                                        "· C ${it.carbs.roundToInt()} · G ${it.fat.roundToInt()}",
                                )
                            },
                        )
                        Text(
                            "Calculado sobre tu gasto diario estimado.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, targets) },
                enabled = name.isNotBlank(),
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AddToPlanDialog(
    viewModel: DietPlanViewModel,
    mealType: MealType,
    onDismiss: () -> Unit,
) {
    val foods by viewModel.foods.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val query by viewModel.foodQuery.collectAsState()
    var selectedFoodId by remember { mutableStateOf<Long?>(null) }
    var selectedRecipeId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("100") }

    val hasSelection = selectedFoodId != null || selectedRecipeId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a ${mealType.labelEs.lowercase()}") },
        text = {
            if (!hasSelection) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onFoodQueryChange,
                        placeholder = { Text("Buscar alimento…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(300.dp)) {
                        if (recipes.isNotEmpty()) {
                            item {
                                Text(
                                    "Tus recetas",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(recipes.size) { index ->
                                val recipe = recipes[index]
                                Card(
                                    onClick = {
                                        selectedRecipeId = recipe.id
                                        amount = "1"
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(recipe.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${recipe.macrosPerServing.kcal.roundToInt()} kcal/ración",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "Alimentos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(foods.size) { index ->
                            val food = foods[index]
                            Card(
                                onClick = {
                                    selectedFoodId = food.id
                                    amount = (food.servingSizeG ?: 100.0).roundToInt().toString()
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(food.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${food.kcalPer100.roundToInt()} kcal/100 g",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (selectedRecipeId != null) "Raciones" else "Cantidad (g)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            if (hasSelection) {
                TextButton(
                    onClick = {
                        val value = amount.toDoubleOrNull() ?: return@TextButton
                        selectedRecipeId?.let { viewModel.addRecipe(mealType, it, value) }
                        selectedFoodId?.let { viewModel.addFood(mealType, it, value) }
                        onDismiss()
                    },
                    enabled = amount.toDoubleOrNull() != null,
                ) { Text("Añadir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
