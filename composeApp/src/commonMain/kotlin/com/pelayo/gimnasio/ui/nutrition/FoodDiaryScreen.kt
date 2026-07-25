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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.domain.model.FoodLogItem
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.labelEs
import com.pelayo.gimnasio.util.monthNamesEs
import com.pelayo.gimnasio.util.monthNumber
import com.pelayo.gimnasio.util.today
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDiaryScreen(
    onAddFood: (LocalDate, MealType) -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenDietPlans: () -> Unit,
    viewModel: FoodDiaryViewModel = koinViewModel(),
) {
    val date by viewModel.date.collectAsState()
    val day by viewModel.day.collectAsState()
    val balance by viewModel.balance.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrición") },
                actions = {
                    IconButton(onClick = onOpenDietPlans) {
                        Icon(Icons.Filled.EventNote, contentDescription = "Planes de dieta")
                    }
                    IconButton(onClick = onOpenRecipes) {
                        Icon(Icons.Filled.MenuBook, contentDescription = "Mis recetas")
                    }
                    IconButton(onClick = { viewModel.shiftDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior")
                    }
                    IconButton(
                        onClick = { viewModel.shiftDay(1) },
                        enabled = date < today(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente")
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
                Text(
                    if (date == today()) "Hoy" else formatDate(date),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            item { BalanceCard(balance = balance, total = day.total) }

            MealType.entries.forEach { meal ->
                item(key = "meal-${meal.name}") {
                    MealCard(
                        meal = meal,
                        items = day.entriesByMeal[meal].orEmpty(),
                        total = day.mealTotal(meal),
                        onAdd = { onAddFood(date, meal) },
                        onDelete = viewModel::deleteEntry,
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: CalorieBalance?, total: Macros) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${total.kcal.roundToInt()}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text("kcal consumidas", style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MacroStat("Proteína", total.protein)
                MacroStat("Carbos", total.carbs)
                MacroStat("Grasa", total.fat)
            }

            val expenditure = balance?.expenditureKcal
            if (expenditure == null) {
                Text(
                    "Completa tu perfil y registra tu peso para ver el balance calórico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val diff = balance.balanceKcal ?: 0.0
                LinearProgressIndicator(
                    progress = { (total.kcal / expenditure).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    buildString {
                        append("Gasto estimado ${expenditure.roundToInt()} kcal")
                        if (balance.exerciseKcal > 0) {
                            append(" (incluye ${balance.exerciseKcal.roundToInt()} de entreno)")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (diff >= 0) {
                        "Superávit de ${diff.roundToInt()} kcal"
                    } else {
                        "Déficit de ${abs(diff).roundToInt()} kcal"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (diff >= 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun MacroStat(label: String, grams: Double) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${grams.roundToInt()} g", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun MealCard(
    meal: MealType,
    items: List<FoodLogItem>,
    total: Macros,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.labelEs, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "${total.kcal.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))

            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${item.quantityLabel} · ${item.macros.kcal.roundToInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Quitar")
                    }
                }
            }

            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Añadir")
            }
        }
    }
}

private fun formatDate(date: LocalDate): String =
    "${date.day} de ${monthNamesEs[date.monthNumber - 1]}"
