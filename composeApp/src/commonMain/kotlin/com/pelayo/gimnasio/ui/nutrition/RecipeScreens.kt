package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.RecipeIngredientItem
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.ui.components.ConfirmDeleteDialog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onRecipeClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeListViewModel = koinViewModel(),
) {
    val recipes by viewModel.recipes.collectAsState()
    val pendingDeletion by viewModel.pendingDeletion.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis recetas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva receta")
            }
        },
    ) { padding ->
        if (recipes.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Aún no tienes recetas", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crea una con sus ingredientes y te calculo las calorías y macros de cada ración.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recipes.size) { index ->
                    val recipe = recipes[index]
                    RecipeCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe.id) },
                        onDelete = { viewModel.askToDelete(recipe) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRecipeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, servings ->
                showCreateDialog = false
                viewModel.createRecipe(name, servings, onRecipeClick)
            },
        )
    }

    pendingDeletion?.let { pending ->
        if (pending.usage.canDelete) {
            ConfirmDeleteDialog(
                title = "¿Borrar ${pending.recipe.name}?",
                text = buildString {
                    append("Se borra la receta con sus ")
                    append("${pending.recipe.ingredients.size} ingredientes.")
                    if (pending.usage.dietPlanMeals > 0) {
                        append(" También se quita de tus planes de dieta, donde aparece ")
                        append("${pending.usage.dietPlanMeals} ")
                        append(if (pending.usage.dietPlanMeals == 1) "vez." else "veces.")
                    }
                },
                onDismiss = viewModel::cancelDeletion,
                onConfirm = viewModel::confirmDeletion,
            )
        } else {
            // El diario es el registro de lo que se comió: no se reescribe por
            // borrar una receta, y la base de datos tampoco lo permitiría.
            AlertDialog(
                onDismissRequest = viewModel::cancelDeletion,
                title = { Text("No se puede borrar") },
                text = {
                    Text(
                        "\"${pending.recipe.name}\" está apuntada ${pending.usage.diaryEntries} " +
                            (if (pending.usage.diaryEntries == 1) "vez" else "veces") +
                            " en tu diario. Borrarla cambiaría lo que ya comiste, así que se " +
                            "queda. Puedes editarla o quitarla del diario primero.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::cancelDeletion) { Text("Vale") }
                },
            )
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                val macros = recipe.macrosPerServing
                Text(
                    "${recipe.servings} raciones · ${macros.kcal.roundToInt()} kcal/ración",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long,
    onBack: () -> Unit,
    viewModel: RecipeEditViewModel = koinViewModel { parametersOf(recipeId) },
) {
    val recipe by viewModel.recipe.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    var showAddIngredient by remember { mutableStateOf(false) }
    var ingredientToEdit by remember { mutableStateOf<RecipeIngredientItem?>(null) }
    var ingredientToDelete by remember { mutableStateOf<RecipeIngredientItem?>(null) }
    // El alimento elegido vive aquí porque también lo fijan el escáner y el alta
    // a mano, que se resuelven fuera del diálogo de ingredientes.
    var selectedFood by remember { mutableStateOf<FoodItem?>(null) }
    var showCustomFood by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        recipe?.let { current ->
            RenameRecipeDialog(
                name = current.name,
                servings = current.servings,
                onDismiss = { showRenameDialog = false },
                onSave = { name, servings ->
                    viewModel.updateRecipe(name, servings)
                    showRenameDialog = false
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "Receta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Renombrar y cambiar cuántas raciones rinde: el método del
                    // ViewModel existía desde el principio sin nadie que lo llamara.
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar nombre y raciones")
                    }
                },
            )
        },
    ) { padding ->
        val current = recipe
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Por ración", style = MaterialTheme.typography.labelMedium)
                        val macros = current.macrosPerServing
                        Text(
                            "${macros.kcal.roundToInt()} kcal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "P ${macros.protein.roundToInt()} g · " +
                                "C ${macros.carbs.roundToInt()} g · " +
                                "G ${macros.fat.roundToInt()} g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Rinde ${current.servings} raciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Text("Ingredientes", style = MaterialTheme.typography.titleMedium) }

            if (current.ingredients.isEmpty()) {
                item {
                    Text(
                        "Añade ingredientes para calcular las calorías. Usa los alimentos que ya tengas guardados en Nutrición.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(current.ingredients.size) { index ->
                val ingredient = current.ingredients[index]
                Card(
                    onClick = { ingredientToEdit = ingredient },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ingredient.foodName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${ingredient.grams.roundToInt()} ${ingredient.unit.labelEs} · " +
                                    "${ingredient.macros.kcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { ingredientToEdit = ingredient }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Cambiar la cantidad de ${ingredient.foodName}",
                            )
                        }
                        IconButton(onClick = { ingredientToDelete = ingredient }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar")
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { showAddIngredient = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir ingrediente")
                }
            }
        }
    }

    if (showAddIngredient) {
        AddIngredientDialog(
            viewModel = viewModel,
            selected = selectedFood,
            onSelect = { selectedFood = it },
            onCreateFood = {
                // Un diálogo cada vez: el de ingredientes vuelve al crear el alimento.
                showAddIngredient = false
                showCustomFood = true
            },
            onDismiss = {
                showAddIngredient = false
                selectedFood = null
            },
            onAdd = { foodId, grams ->
                viewModel.addIngredient(foodId, grams)
                showAddIngredient = false
                selectedFood = null
            },
        )
    }

    when (val scan = scanState) {
        is ScanUiState.NotFound -> AlertDialog(
            onDismissRequest = viewModel::dismissScan,
            title = { Text("Producto no encontrado") },
            text = {
                Text(
                    "El código ${scan.barcode} no está en Open Food Facts. Puedes crear el " +
                        "alimento a mano con los datos de la etiqueta.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissScan()
                    // Un diálogo cada vez: el de ingredientes vuelve al crear el alimento.
                    showAddIngredient = false
                    showCustomFood = true
                }) { Text("Crear alimento") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissScan) { Text("Cancelar") } },
        )

        is ScanUiState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissScan,
            title = { Text("No se pudo escanear") },
            text = { Text(scan.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissScan) { Text("Vale") } },
        )

        else -> Unit
    }

    ingredientToEdit?.let { ingredient ->
        IngredientAmountDialog(
            ingredient = ingredient,
            onDismiss = { ingredientToEdit = null },
            onSave = { grams ->
                viewModel.updateIngredient(ingredient.id, grams)
                ingredientToEdit = null
            },
        )
    }

    ingredientToDelete?.let { ingredient ->
        ConfirmDeleteDialog(
            title = "¿Quitar ${ingredient.foodName}?",
            text = "Sale de la receta y las calorías por ración se recalculan.",
            confirmLabel = "Quitar",
            onDismiss = { ingredientToDelete = null },
            onConfirm = {
                viewModel.removeIngredient(ingredient.id)
                ingredientToDelete = null
            },
        )
    }

    if (showCustomFood) {
        CustomFoodDialog(
            onDismiss = { showCustomFood = false },
            onCreate = { name, brand, kcal, protein, carbs, fat, unit ->
                viewModel.createCustomFood(name, brand, kcal, protein, carbs, fat, unit) { food ->
                    selectedFood = food
                    showCustomFood = false
                    showAddIngredient = true
                }
            },
        )
    }
}

/** Cambia cuánto lleva la receta de un ingrediente, con los macros recalculados en vivo. */
@Composable
private fun IngredientAmountDialog(
    ingredient: RecipeIngredientItem,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var amount by remember { mutableStateOf(ingredient.grams.roundToInt().toString()) }
    val parsed = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ingredient.foodName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad (${ingredient.unit.labelEs})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (parsed != null && ingredient.grams > 0) {
                    // Los macros guardados son de la cantidad actual: se reescalan.
                    val factor = parsed / ingredient.grams
                    Text(
                        "${(ingredient.macros.kcal * factor).roundToInt()} kcal · " +
                            "P ${(ingredient.macros.protein * factor).roundToInt()} g · " +
                            "C ${(ingredient.macros.carbs * factor).roundToInt()} g · " +
                            "G ${(ingredient.macros.fat * factor).roundToInt()} g",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null && parsed > 0,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AddIngredientDialog(
    viewModel: RecipeEditViewModel,
    selected: FoodItem?,
    onSelect: (FoodItem) -> Unit,
    onCreateFood: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (Long, Double) -> Unit,
) {
    val foods by viewModel.foods.collectAsState()
    val query by viewModel.foodQuery.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    var grams by remember { mutableStateOf("100") }

    // Lo escaneado pasa directo a la cantidad, sin buscarlo en la lista.
    LaunchedEffect(scanState) {
        (scanState as? ScanUiState.Found)?.let {
            onSelect(it.food)
            viewModel.dismissScan()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selected?.name ?: "Elegir ingrediente") },
        text = {
            if (selected == null) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::onFoodQueryChange,
                            placeholder = { Text("Buscar alimento…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (viewModel.scannerAvailable) {
                            IconButton(
                                onClick = viewModel::scanBarcode,
                                enabled = scanState !is ScanUiState.Scanning,
                            ) {
                                Icon(
                                    Icons.Filled.QrCodeScanner,
                                    contentDescription = "Escanear código de barras",
                                )
                            }
                        }
                    }
                    // El alta va **antes** de la lista: detrás quedaba fuera de la
                    // zona visible del diálogo y no había manera de llegar a ella.
                    TextButton(onClick = onCreateFood) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Crear alimento")
                    }

                    if (foods.isEmpty()) {
                        Text(
                            "Nada con ese nombre. Escanea el código de barras del producto " +
                                "o créalo a mano con los datos de la etiqueta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // heightIn y no height: con dos alimentos no tiene sentido
                        // reservar el hueco de diez.
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(foods.size) { index ->
                                val food = foods[index]
                                // La misma fila que el buscador del diario: con foto
                                // y marca se distingue el yogur que buscas del resto.
                                Box(Modifier.padding(vertical = 4.dp)) {
                                    FoodRow(food = food, onClick = { onSelect(food) })
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad (${selected.unit.labelEs})") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            selected?.let { food ->
                TextButton(
                    onClick = { grams.toDoubleOrNull()?.let { onAdd(food.id, it) } },
                    enabled = grams.toDoubleOrNull() != null,
                ) { Text("Añadir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CreateRecipeDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva receta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Arroz con pollo") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it.filter(Char::isDigit) },
                    label = { Text("Raciones que salen") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, servings.toIntOrNull() ?: 1) },
                enabled = name.isNotBlank(),
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Nombre y raciones de una receta ya creada; los ingredientes van aparte. */
@Composable
private fun RenameRecipeDialog(
    name: String,
    servings: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var newName by remember { mutableStateOf(name) }
    var newServings by remember { mutableStateOf(servings.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar receta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newServings,
                    onValueChange = { newServings = it.filter(Char::isDigit) },
                    label = { Text("Raciones que rinde") },
                    supportingText = { Text("Las calorías por ración se recalculan.") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank() && (newServings.toIntOrNull() ?: 0) > 0,
                onClick = { onSave(newName.trim(), newServings.toInt()) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
