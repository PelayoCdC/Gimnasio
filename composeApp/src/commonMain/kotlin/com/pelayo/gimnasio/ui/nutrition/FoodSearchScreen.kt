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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.labelEs
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
    val onlineState by viewModel.onlineState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    var selectedFood by remember { mutableStateOf<FoodItem?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }

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
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar alimento…") },
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::searchOnline,
                    enabled = query.trim().length >= 2 && onlineState !is OnlineSearchState.Loading,
                ) {
                    if (onlineState is OnlineSearchState.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar online")
                }
                OutlinedButton(onClick = { showCustomDialog = true }) { Text("Crear alimento") }
            }

            when (val state = onlineState) {
                is OnlineSearchState.Failed -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                is OnlineSearchState.Done -> Text(
                    if (state.count == 0) {
                        "Open Food Facts no encontró nada con ese nombre."
                    } else {
                        "${state.count} productos descargados de Open Food Facts."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                else -> Unit
            }

            if (results.isEmpty()) {
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
                    Text("Sin alimentos guardados", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Búscalos online o crea el tuyo con los datos de la etiqueta.",
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
                    onDone()
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
            onCreate = { name, brand, kcal, protein, carbs, fat ->
                viewModel.createCustomFood(name, brand, kcal, protein, carbs, fat) {
                    showCustomDialog = false
                }
            },
        )
    }
}

@Composable
private fun FoodRow(food: FoodItem, onClick: () -> Unit) {
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
                        append("${food.kcalPer100.roundToInt()} kcal/100 g")
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
        mutableStateOf((food.servingSizeG ?: 100.0).roundToInt().toString())
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
                    label = { Text("Cantidad (g)") },
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
private fun CustomFoodDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, Double, Double, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    val valid = name.isNotBlank() && kcal.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo alimento") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "Valores por cada 100 g, como vienen en la etiqueta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        label = { Text("kcal / 100 g") },
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
                    )
                },
                enabled = valid,
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
