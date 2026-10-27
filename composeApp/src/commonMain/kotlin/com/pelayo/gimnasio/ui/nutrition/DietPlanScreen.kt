package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import com.pelayo.gimnasio.ui.components.TagChip
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
import com.pelayo.gimnasio.domain.model.DietPlanMealItem
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.FoodUnit
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import com.pelayo.gimnasio.ui.components.ConfirmDeleteDialog
import com.pelayo.gimnasio.util.weekdayInitialsEs
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
    val day by viewModel.selectedDay.collectAsState()
    val message by viewModel.message.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<MealType?>(null) }
    var editing by remember { mutableStateOf<DietPlanMealItem?>(null) }
    var showCopyDay by remember { mutableStateOf(false) }
    var confirmDeletePlan by remember { mutableStateOf(false) }
    var showEditPlan by remember { mutableStateOf(false) }
    /** Día destino de la copia pendiente de confirmar, porque pisa su menú. */
    var copyTargetToConfirm by remember { mutableStateOf<Int?>(null) }

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
                            "Un plan es el menú de la semana con objetivos de calorías y macros. " +
                                "Puedes volcar el día que toque al diario con un toque.",
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
                item { PlanSummaryCard(plan, day) }

                item {
                    WeekSelector(
                        selectedDay = day,
                        plannedDays = plan.plannedDays,
                        onSelect = viewModel::selectDay,
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!plan.isActive) {
                            OutlinedButton(onClick = { viewModel.setActive(plan.id) }) {
                                Text("Marcar como activo")
                            }
                        }
                        Button(onClick = viewModel::applyToToday) { Text("Aplicar a hoy") }
                        OutlinedButton(onClick = { showEditPlan = true }) { Text("Editar") }
                    }
                }

                MealType.entries.forEach { meal ->
                    item(key = "plan-meal-${meal.name}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(meal.labelEs, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    val kcal = plan.mealsFor(day, meal).sumOf { it.macros.kcal }
                                    Text(
                                        "${kcal.roundToInt()} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                plan.mealsFor(day, meal).forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clickable { editing = item }
                                                .padding(vertical = 8.dp),
                                        ) {
                                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${item.quantityLabel} · ${item.macros.kcal.roundToInt()} kcal",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { editing = item }) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "Editar ${item.name}",
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
                        onClick = { showCopyDay = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar el ${weekdayNamesEs[day - 1].lowercase()} a otro día")
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { confirmDeletePlan = true },
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
            dayOfWeek = day,
            onDismiss = { addingTo = null },
        )
    }

    editing?.let { item ->
        EditQuantityDialog(
            title = item.name,
            isRecipe = item.recipeId != null,
            unit = item.unit,
            initialAmount = (item.servings ?: item.grams) ?: 0.0,
            initialMeal = item.mealType,
            onDismiss = { editing = null },
            onConfirm = { meal, amount ->
                viewModel.updateMeal(item, meal, amount)
                editing = null
            },
        )
    }

    if (showCopyDay) {
        CopyDayDialog(
            fromDay = day,
            onDismiss = { showCopyDay = false },
            onCopy = { target ->
                showCopyDay = false
                // Si el destino ya tiene menú, copiar lo machaca: se confirma antes.
                if (selected?.plannedDays?.contains(target) == true) {
                    copyTargetToConfirm = target
                } else {
                    viewModel.copyDayTo(target)
                }
            },
        )
    }

    copyTargetToConfirm?.let { target ->
        ConfirmDeleteDialog(
            title = "¿Reemplazar el ${weekdayNamesEs[target - 1].lowercase()}?",
            text = "El ${weekdayNamesEs[target - 1].lowercase()} ya tiene menú y se " +
                "sustituirá por el del ${weekdayNamesEs[day - 1].lowercase()}.",
            confirmLabel = "Reemplazar",
            onDismiss = { copyTargetToConfirm = null },
            onConfirm = {
                viewModel.copyDayTo(target)
                copyTargetToConfirm = null
            },
        )
    }

    if (showEditPlan) {
        selected?.let { plan ->
            EditPlanDialog(
                plan = plan,
                onDismiss = { showEditPlan = false },
                onSave = { name, kcal, protein, carbs, fat ->
                    viewModel.updatePlan(plan.id, name, kcal, protein, carbs, fat)
                    showEditPlan = false
                },
            )
        }
    }

    if (confirmDeletePlan) {
        selected?.let { plan ->
            ConfirmDeleteDialog(
                title = "¿Borrar \"${plan.name}\"?",
                text = "Se borra el plan con el menú de toda la semana. " +
                    "Lo ya apuntado en el diario no se toca.",
                onDismiss = { confirmDeletePlan = false },
                onConfirm = {
                    viewModel.deletePlan(plan.id)
                    confirmDeletePlan = false
                },
            )
        }
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

/**
 * Los siete días de la semana. El punto marca los que ya tienen menú, para ver de
 * un vistazo lo que queda por planificar.
 */
@Composable
private fun WeekSelector(
    selectedDay: Int,
    plannedDays: Set<Int>,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weekdayInitialsEs.forEachIndexed { index, initial ->
            val dayOfWeek = index + 1
            FilterChip(
                selected = selectedDay == dayOfWeek,
                onClick = { onSelect(dayOfWeek) },
                label = {
                    Text(if (plannedDays.contains(dayOfWeek)) "$initial ·" else initial)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CopyDayDialog(
    fromDay: Int,
    onDismiss: () -> Unit,
    onCopy: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copiar el ${weekdayNamesEs[fromDay - 1].lowercase()}") },
        text = {
            Column {
                Text(
                    "El día que elijas se queda con este mismo menú y pierde el que tuviera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                weekdayNamesEs.forEachIndexed { index, name ->
                    val dayOfWeek = index + 1
                    if (dayOfWeek != fromDay) {
                        TextButton(
                            onClick = { onCopy(dayOfWeek) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PlanSummaryCard(plan: DietPlanItem, dayOfWeek: Int) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.name, style = MaterialTheme.typography.titleLarge)
            Text(
                weekdayNamesEs[dayOfWeek - 1],
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            val planned = plan.plannedMacros(dayOfWeek)
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
                        TagChip(
                            label = "${it.kcal.roundToInt()} kcal · P ${it.protein.roundToInt()} " +
                                "· C ${it.carbs.roundToInt()} · G ${it.fat.roundToInt()}",
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

/** Nombre y objetivos diarios de un plan ya creado; el menú semanal no se toca. */
@Composable
private fun EditPlanDialog(
    plan: DietPlanItem,
    onDismiss: () -> Unit,
    onSave: (String, Double?, Double?, Double?, Double?) -> Unit,
) {
    var name by remember { mutableStateOf(plan.name) }
    var kcal by remember { mutableStateOf(plan.targetKcal?.roundToInt()?.toString() ?: "") }
    var protein by remember { mutableStateOf(plan.targetProteinG?.roundToInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(plan.targetCarbsG?.roundToInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(plan.targetFatG?.roundToInt()?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                Text("Objetivos diarios (opcional)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter(Char::isDigit) },
                    label = { Text("Calorías") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it.filter(Char::isDigit) },
                        label = { Text("Proteína (g)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it.filter(Char::isDigit) },
                        label = { Text("Carbohid. (g)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it.filter(Char::isDigit) },
                        label = { Text("Grasa (g)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        kcal.toDoubleOrNull(),
                        protein.toDoubleOrNull(),
                        carbs.toDoubleOrNull(),
                        fat.toDoubleOrNull(),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AddToPlanDialog(
    viewModel: DietPlanViewModel,
    mealType: MealType,
    dayOfWeek: Int,
    onDismiss: () -> Unit,
) {
    val foods by viewModel.foods.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val query by viewModel.foodQuery.collectAsState()
    var selectedFood by remember { mutableStateOf<FoodItem?>(null) }
    var selectedRecipeId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("100") }

    val hasSelection = selectedFood != null || selectedRecipeId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Añadir a ${mealType.labelEs.lowercase()} " +
                    "del ${weekdayNamesEs[dayOfWeek - 1].lowercase()}",
            )
        },
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
                                    selectedFood = food
                                    amount = (food.servingSize ?: 100.0).roundToInt().toString()
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(food.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${food.kcalPer100.roundToInt()} kcal/100 ${food.unit.labelEs}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        selectedFood?.name
                            ?: recipes.firstOrNull { it.id == selectedRecipeId }?.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = {
                            Text(
                                if (selectedRecipeId != null) {
                                    "Raciones"
                                } else {
                                    "Cantidad (${(selectedFood?.unit ?: FoodUnit.G).labelEs})"
                                },
                            )
                        },
                        singleLine = true,
                    )
                    // Macros en vivo mientras se escribe, como en el resto de
                    // diálogos de cantidad de la app.
                    amount.toDoubleOrNull()?.let { value ->
                        val macros = selectedFood?.let { food ->
                            Macros.forGrams(
                                value, food.kcalPer100, food.proteinPer100,
                                food.carbsPer100, food.fatPer100,
                            )
                        } ?: recipes.firstOrNull { it.id == selectedRecipeId }?.let { recipe ->
                            val per = recipe.macrosPerServing
                            Macros(per.kcal * value, per.protein * value, per.carbs * value, per.fat * value)
                        }
                        macros?.let {
                            Text(
                                "${it.kcal.roundToInt()} kcal · P ${it.protein.roundToInt()} g · " +
                                    "C ${it.carbs.roundToInt()} g · G ${it.fat.roundToInt()} g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Vuelta a la lista sin tener que cancelar el diálogo entero.
                    TextButton(onClick = {
                        selectedFood = null
                        selectedRecipeId = null
                    }) { Text("Elegir otro") }
                }
            }
        },
        confirmButton = {
            if (hasSelection) {
                TextButton(
                    onClick = {
                        val value = amount.toDoubleOrNull() ?: return@TextButton
                        selectedRecipeId?.let { viewModel.addRecipe(mealType, it, value) }
                        selectedFood?.let { viewModel.addFood(mealType, it.id, value) }
                        onDismiss()
                    },
                    enabled = amount.toDoubleOrNull() != null,
                ) { Text("Añadir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
