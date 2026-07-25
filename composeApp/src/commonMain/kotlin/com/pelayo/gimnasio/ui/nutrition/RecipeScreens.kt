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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.RecipeItem
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
                        onDelete = { viewModel.deleteRecipe(recipe.id) },
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
    var showAddIngredient by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name ?: "Receta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ingredient.foodName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${ingredient.grams.roundToInt()} g · ${ingredient.macros.kcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.removeIngredient(ingredient.id) }) {
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
            onDismiss = { showAddIngredient = false },
            onAdd = { foodId, grams ->
                viewModel.addIngredient(foodId, grams)
                showAddIngredient = false
            },
        )
    }
}

@Composable
private fun AddIngredientDialog(
    viewModel: RecipeEditViewModel,
    onDismiss: () -> Unit,
    onAdd: (Long, Double) -> Unit,
) {
    val foods by viewModel.foods.collectAsState()
    val query by viewModel.foodQuery.collectAsState()
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var grams by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selected?.name ?: "Elegir ingrediente") },
        text = {
            if (selected == null) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onFoodQueryChange,
                        placeholder = { Text("Buscar alimento…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (foods.isEmpty()) {
                        Text(
                            "No tienes alimentos guardados. Añádelos desde el diario de nutrición (búsqueda online o creando el tuyo).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(Modifier.height(280.dp)) {
                            items(foods.size) { index ->
                                val food = foods[index]
                                Card(
                                    onClick = { selected = food },
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
                }
            } else {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad (g)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            if (selected != null) {
                TextButton(
                    onClick = { grams.toDoubleOrNull()?.let { onAdd(selected!!.id, it) } },
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
