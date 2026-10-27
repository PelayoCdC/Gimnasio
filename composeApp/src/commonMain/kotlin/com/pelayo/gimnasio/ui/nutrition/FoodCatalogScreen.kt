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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.ui.components.ConfirmDeleteDialog
import com.pelayo.gimnasio.domain.model.FoodUnit
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * El catálogo local de alimentos: lo que se ha ido descargando de Open Food Facts
 * y lo creado a mano. Sirve para ver qué hay y para dar de alta alimentos sin
 * tener que apuntarlos en una comida, que era la única manera de llegar aquí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodCatalogScreen(
    onBack: () -> Unit,
    viewModel: FoodSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val onlineState by viewModel.onlineState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val rateLimitLeft by viewModel.rateLimitSecondsLeft.collectAsState()
    var detail by remember { mutableStateOf<FoodItem?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var foodToEdit by remember { mutableStateOf<FoodItem?>(null) }
    var foodToDelete by remember { mutableStateOf<FoodItem?>(null) }
    var deleteBlocked by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo de alimentos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
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
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                if (counts.total == 0) {
                    "Sin alimentos guardados todavía."
                } else {
                    "${counts.total} alimentos guardados · ${counts.custom} creados por ti"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FoodSearchControls(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                placeholder = "Buscar en el catálogo…",
                onlineState = onlineState,
                rateLimitLeft = rateLimitLeft,
                onSearchOnline = viewModel::searchOnline,
                onCreateFood = { showCustomDialog = true },
                onlineDoneMessage = { "$it productos añadidos al catálogo." },
            )

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
                    Text(
                        if (counts.total == 0) "El catálogo está vacío" else "Nada con ese nombre",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Búscalo online, escanea su código de barras o créalo con los datos " +
                            "de la etiqueta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results.size) { index ->
                        val food = results[index]
                        FoodRow(food = food, onClick = { detail = food })
                    }
                    // La lista está topada: con muchos alimentos hay que buscar.
                    if (results.size >= viewModel.searchLimit) {
                        item {
                            Text(
                                "Se muestran los primeros ${viewModel.searchLimit}. " +
                                    "Busca por nombre para encontrar el resto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { food ->
        // La lista se relee al cambiar la unidad, así que la ficha se saca de ella
        // para que el chip elegido se quede marcado.
        val current = results.firstOrNull { it.id == food.id } ?: food
        FoodDetailDialog(
            food = current,
            onDismiss = { detail = null },
            onChangeUnit = { viewModel.changeUnit(current.id, it) },
            // Solo los propios: los de Open Food Facts se pisan al rebuscarlos.
            onEdit = if (current.isCustom) {
                {
                    detail = null
                    foodToEdit = current
                }
            } else {
                null
            },
            onDelete = if (current.isCustom) {
                {
                    detail = null
                    foodToDelete = current
                }
            } else {
                null
            },
        )
    }

    foodToEdit?.let { food ->
        CustomFoodDialog(
            initial = food,
            onDismiss = { foodToEdit = null },
            onCreate = { name, brand, kcal, protein, carbs, fat, unit ->
                viewModel.updateCustomFood(food.id, name, brand, kcal, protein, carbs, fat, unit) {
                    foodToEdit = null
                }
            },
        )
    }

    foodToDelete?.let { food ->
        ConfirmDeleteDialog(
            title = "¿Borrar \"${food.name}\"?",
            text = "Solo se puede borrar si no está en el diario ni en una receta. " +
                "De los planes de dieta se quita solo.",
            onDismiss = { foodToDelete = null },
            onConfirm = {
                viewModel.deleteFood(food.id) { deleted -> if (!deleted) deleteBlocked = true }
                foodToDelete = null
            },
        )
    }

    if (deleteBlocked) {
        AlertDialog(
            onDismissRequest = { deleteBlocked = false },
            title = { Text("No se puede borrar") },
            text = {
                Text(
                    "Este alimento está apuntado en el diario o en una receta. El diario " +
                        "es historial y no se reescribe; quítalo de las recetas primero.",
                )
            },
            confirmButton = { TextButton(onClick = { deleteBlocked = false }) { Text("Vale") } },
        )
    }

    when (val scan = scanState) {
        // Escanear en el catálogo solo da de alta el producto y enseña su ficha.
        is ScanUiState.Found -> FoodDetailDialog(
            food = scan.food,
            onDismiss = viewModel::dismissScan,
        )

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
 * La ficha del alimento: lo que hay guardado de él, sin apuntarlo en ninguna
 * comida. Con [onChangeUnit] se puede corregir si va en gramos o en mililitros.
 */
@Composable
internal fun FoodDetailDialog(
    food: FoodItem,
    onDismiss: () -> Unit,
    onChangeUnit: ((FoodUnit) -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (food.imageUrl != null) {
                    AsyncImage(
                        model = food.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
                food.brand?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                Text("Por cada 100 ${food.unit.labelEs}", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${food.kcalPer100.roundToInt()} kcal · " +
                        "P ${food.proteinPer100.roundToInt()} g · " +
                        "C ${food.carbsPer100.roundToInt()} g · " +
                        "G ${food.fatPer100.roundToInt()} g",
                    style = MaterialTheme.typography.bodyMedium,
                )
                food.fiberPer100?.let {
                    Text(
                        "Fibra ${it.roundToInt()} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                food.servingSize?.let {
                    Text(
                        "Ración de ${it.roundToInt()} ${food.unit.labelEs}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Open Food Facts no siempre dice si el producto es líquido, y lo
                // cacheado antes de que existiera la columna se quedó en gramos.
                onChangeUnit?.let { change ->
                    Text("Se mide en", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FoodUnit.entries.forEach { option ->
                            FilterChip(
                                selected = food.unit == option,
                                onClick = { change(option) },
                                label = { Text(option.labelEs) },
                            )
                        }
                    }
                }

                Text(
                    when {
                        food.isCustom -> "Creado por ti"
                        food.barcode != null -> "Open Food Facts · código ${food.barcode}"
                        else -> "Open Food Facts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (onEdit != null || onDelete != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onEdit?.let { TextButton(onClick = it) { Text("Editar") } }
                        onDelete?.let { TextButton(onClick = it) { Text("Borrar") } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
