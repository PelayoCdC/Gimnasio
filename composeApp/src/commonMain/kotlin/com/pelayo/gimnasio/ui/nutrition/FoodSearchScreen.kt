package com.pelayo.gimnasio.ui.nutrition

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.FoodUnit
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.domain.model.labelEs
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.pelayo.gimnasio.util.oneDecimal
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    date: LocalDate,
    mealType: MealType,
    onDone: () -> Unit,
    viewModel: FoodSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val onlineState by viewModel.onlineState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val rateLimitLeft by viewModel.rateLimitSecondsLeft.collectAsState()
    var selectedFood by remember { mutableStateOf<FoodItem?>(null) }
    var selectedRecipe by remember { mutableStateOf<RecipeItem?>(null) }
    var showRecipes by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // La pantalla se queda abierta tras apuntar: una comida suelen ser varios
    // alimentos y cada uno costaba volver a entrar desde el diario.
    fun confirmLogged(name: String) {
        scope.launch { snackbarHost.showSnackbar("$name apuntado") }
    }

    // Un escaneo con éxito lleva directo al diálogo de cantidad.
    LaunchedEffect(scanState) {
        (scanState as? ScanUiState.Found)?.let {
            selectedFood = it.food
            viewModel.dismissScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Añadir a ${mealType.labelEs.lowercase()}") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (viewModel.scannerAvailable) {
                        IconButton(
                            onClick = viewModel::scanBarcode,
                            enabled = scanState !is ScanUiState.Scanning,
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear código de barras")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !showRecipes,
                    onClick = { showRecipes = false },
                    label = { Text("Alimentos") },
                )
                FilterChip(
                    selected = showRecipes,
                    onClick = { showRecipes = true },
                    label = { Text("Mis recetas") },
                )
            }

            if (showRecipes) {
                RecipePicker(
                    recipes = recipes,
                    onSelect = { selectedRecipe = it },
                )
                return@Column
            }

            FoodSearchControls(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                placeholder = "Buscar alimento…",
                onlineState = onlineState,
                rateLimitLeft = rateLimitLeft,
                onSearchOnline = viewModel::searchOnline,
                onCreateFood = { showCustomDialog = true },
                onlineDoneMessage = { "$it productos descargados de Open Food Facts." },
            )

            if (results.isEmpty()) {
                // Distinguir "no tienes nada guardado" de "esa búsqueda no casa":
                // con el catálogo lleno, el primer mensaje era mentira.
                val counts by viewModel.counts.collectAsState()
                val catalogEmpty = counts.total == 0
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (catalogEmpty) "Sin alimentos guardados" else "Nada con ese nombre",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (catalogEmpty) {
                            "Búscalos online o crea el tuyo con los datos de la etiqueta."
                        } else {
                            "Prueba con otro nombre, búscalo online o créalo a mano."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results.size) { index ->
                        val food = results[index]
                        FoodRow(food = food, onClick = { selectedFood = food })
                    }
                }
            }
        }
    }

    selectedFood?.let { food ->
        QuantityDialog(
            food = food,
            onDismiss = { selectedFood = null },
            onConfirm = { grams ->
                viewModel.logFood(date, mealType, food.id, grams) {
                    selectedFood = null
                    confirmLogged(food.name)
                }
            },
        )
    }

    selectedRecipe?.let { recipe ->
        ServingsDialog(
            recipe = recipe,
            onDismiss = { selectedRecipe = null },
            onConfirm = { servings ->
                viewModel.logRecipe(date, mealType, recipe.id, servings) {
                    selectedRecipe = null
                    confirmLogged(recipe.name)
                }
            },
        )
    }

    when (val scan = scanState) {
        is ScanUiState.NotFound -> AlertDialog(
            onDismissRequest = viewModel::dismissScan,
            title = { Text("Producto no encontrado") },
            text = {
                Text(
                    "El código ${scan.barcode} no está en Open Food Facts. Puedes crear el alimento a mano " +
                        "con los datos de la etiqueta.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissScan()
                    showCustomDialog = true
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

    if (showCustomDialog) {
        CustomFoodDialog(
            onDismiss = { showCustomDialog = false },
            onCreate = { name, brand, kcal, protein, carbs, fat, unit ->
                viewModel.createCustomFood(name, brand, kcal, protein, carbs, fat, unit) {
                    showCustomDialog = false
                }
            },
        )
    }
}

/**
 * Aviso de que Open Food Facts ha cortado. Sale con cuenta atrás porque el corte
 * es por IP y por tiempo: insistir no lo arregla, solo lo alarga.
 */
@Composable
internal fun RateLimitWarning(secondsLeft: Int) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.HourglassBottom, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Open Food Facts te ha cortado por ahora",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Limita las búsquedas de los usuarios anónimos. Vuelve a intentarlo en " +
                        "${secondsLeft}s; seguir probando solo alarga la espera. Puedes crear " +
                        "el alimento a mano mientras tanto.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Las recetas propias se apuntan por raciones, con los macros ya calculados. */
@Composable
private fun RecipePicker(recipes: List<RecipeItem>, onSelect: (RecipeItem) -> Unit) {
    if (recipes.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Aún no tienes recetas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Créalas desde Nutrición → Mis recetas y podrás apuntarlas aquí por raciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(recipes.size) { index ->
            val recipe = recipes[index]
            Card(onClick = { onSelect(recipe) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${recipe.macrosPerServing.kcal.roundToInt()} kcal/ración · " +
                            "rinde ${recipe.servings}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServingsDialog(
    recipe: RecipeItem,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var servings by remember { mutableStateOf("1") }
    val parsed = servings.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Raciones") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (parsed != null) {
                    val macros = recipe.macrosPerServing
                    Text(
                        "${(macros.kcal * parsed).roundToInt()} kcal · " +
                            "P ${(macros.protein * parsed).roundToInt()} g · " +
                            "C ${(macros.carbs * parsed).roundToInt()} g · " +
                            "G ${(macros.fat * parsed).roundToInt()} g",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed > 0,
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun FoodRow(food: FoodItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (food.imageUrl != null) {
                AsyncImage(
                    model = food.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        food.brand?.let { append("$it · ") }
                        append("${food.kcalPer100.roundToInt()} kcal/100 ${food.unit.labelEs}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuantityDialog(
    food: FoodItem,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var grams by remember {
        mutableStateOf((food.servingSize ?: 100.0).roundToInt().toString())
    }
    val parsed = grams.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cantidad (${food.unit.labelEs})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (parsed != null) {
                    val factor = parsed / 100.0
                    Text(
                        "${(food.kcalPer100 * factor).roundToInt()} kcal · " +
                            "P ${(food.proteinPer100 * factor).roundToInt()} g · " +
                            "C ${(food.carbsPer100 * factor).roundToInt()} g · " +
                            "G ${(food.fatPer100 * factor).roundToInt()} g",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed > 0,
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun CustomFoodDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, Double, Double, Double, Double, FoodUnit) -> Unit,
    /** Alimento a editar: prerrellena y cambia el título. Null para crear. */
    initial: FoodItem? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var kcal by remember { mutableStateOf(initial?.kcalPer100?.oneDecimal().orEmpty()) }
    var protein by remember { mutableStateOf(initial?.proteinPer100?.oneDecimal().orEmpty()) }
    var carbs by remember { mutableStateOf(initial?.carbsPer100?.oneDecimal().orEmpty()) }
    var fat by remember { mutableStateOf(initial?.fatPer100?.oneDecimal().orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit ?: FoodUnit.G) }

    val valid = name.isNotBlank() && kcal.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo alimento" else "Editar alimento") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Valores por cada 100 ${unit.labelEs}, como vienen en la etiqueta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    // Los líquidos vienen por 100 ml en la etiqueta, no por 100 g.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FoodUnit.entries.forEach { option ->
                            FilterChip(
                                selected = unit == option,
                                onClick = { unit = option },
                                label = {
                                    Text(if (option == FoodUnit.G) "Sólido (g)" else "Líquido (ml)")
                                },
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca (opcional)") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("kcal / 100 ${unit.labelEs}") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Proteína (g)") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Carbohidratos (g)") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Grasas (g)") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name.trim(),
                        brand.takeIf { it.isNotBlank() }?.trim(),
                        kcal.toDoubleOrNull() ?: 0.0,
                        protein.toDoubleOrNull() ?: 0.0,
                        carbs.toDoubleOrNull() ?: 0.0,
                        fat.toDoubleOrNull() ?: 0.0,
                        unit,
                    )
                },
                enabled = valid,
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
