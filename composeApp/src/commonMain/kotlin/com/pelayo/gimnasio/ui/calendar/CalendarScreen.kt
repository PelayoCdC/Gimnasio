package com.pelayo.gimnasio.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.util.formatDayMonthEs
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pelayo.gimnasio.domain.model.CalendarSession
import com.pelayo.gimnasio.util.monthNamesEs
import com.pelayo.gimnasio.util.monthNumber
import com.pelayo.gimnasio.util.weekdayInitialsEs
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onSessionClick: (Long) -> Unit,
    onResumeSession: (Long) -> Unit,
    onOpenDiary: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val grid by viewModel.grid.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val calories by viewModel.calories.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendario") },
                actions = {
                    IconButton(onClick = viewModel::showPreviousMonth) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Mes anterior")
                    }
                    // Vuelta a hoy sin deshacer la navegación mes a mes.
                    IconButton(onClick = viewModel::showToday) {
                        Icon(Icons.Filled.Today, contentDescription = "Ir a hoy")
                    }
                    IconButton(onClick = viewModel::showNextMonth) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Mes siguiente")
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
                    grid.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(Modifier.fillMaxWidth()) {
                    weekdayInitialsEs.forEach { initial ->
                        Text(
                            initial,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                // 6 semanas × 7 días con celdas cuadradas; altura fija para que
                // la rejilla conviva con el scroll de la lista exterior.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    userScrollEnabled = false,
                ) {
                    items(grid.cells.size) { index ->
                        val date = grid.cells[index]
                        DayCell(
                            date = date,
                            isToday = date == viewModel.todayDate,
                            isSelected = date != null && date == selectedDate,
                            sessions = date?.let { sessions[it] }.orEmpty(),
                            hasDiet = date?.let { calories[it] }?.let { it > 0 } == true,
                            onClick = { date?.let(viewModel::selectDate) },
                        )
                    }
                }
            }

            item { CalendarLegend() }

            // Lo comido: del día elegido, o del mes entero si no hay ninguno.
            item {
                val kcal = selectedDate?.let { calories[it] }
                val monthTotal = calories.values.sum()
                val daysWithFood = calories.count { it.value > 0 }
                DietSummaryCard(
                    text = when {
                        selectedDate != null && kcal != null && kcal > 0 ->
                            "${kcal.roundToInt()} kcal apuntadas ese día. Toca para ver el diario."

                        selectedDate != null -> "Ese día no apuntaste nada de comer."

                        daysWithFood > 0 ->
                            "$daysWithFood días con comida apuntada este mes, " +
                                "${(monthTotal / daysWithFood).roundToInt()} kcal de media."

                        else -> "Este mes no has apuntado nada de comer."
                    },
                    // La tarjeta que anuncia calorías de un día concreto abre ese
                    // día en el diario, que ya sabía moverse a cualquier fecha.
                    onClick = selectedDate?.let { date -> { onOpenDiary(date) } },
                )
            }

            val shown = selectedDate?.let { date -> sessions[date].orEmpty() }
                ?: sessions.values.flatten().sortedByDescending { it.date }

            if (shown.isEmpty()) {
                item {
                    Text(
                        if (selectedDate != null) {
                            "Sin entrenamientos ese día."
                        } else {
                            "No hay entrenamientos registrados este mes."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Text(
                        selectedDate?.let { formatLongDate(it) } ?: "Entrenamientos del mes",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                shown.forEach { session ->
                    item(key = "session-${session.sessionId}") {
                        SessionRow(
                            session = session,
                            onClick = {
                                // Una sesión sin terminar se retoma; el resumen es
                                // para las cerradas (y no tiene botón de retomar).
                                if (session.isCompleted) {
                                    onSessionClick(session.sessionId)
                                } else {
                                    onResumeSession(session.sessionId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    sessions: List<CalendarSession>,
    hasDiet: Boolean,
    onClick: () -> Unit,
) {
    // Los puntos son solo color: el lector de pantalla necesita el resumen en texto.
    val cellDescription = date?.let {
        buildString {
            append(it.formatDayMonthEs())
            val completed = sessions.count { s -> s.isCompleted }
            val pending = sessions.size - completed
            if (completed > 0) append(", $completed entrenamientos")
            if (pending > 0) append(", $pending sin terminar")
            if (hasDiet) append(", comida apuntada")
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                },
            )
            .then(
                if (isToday) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = date != null, onClick = onClick)
            .then(
                cellDescription?.let { text ->
                    Modifier.semantics { contentDescription = text }
                } ?: Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                sessions.take(3).forEach { session ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (session.isCompleted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                    )
                }
                // El punto de la comida va en otro color para distinguirlo de un
                // entrenamiento de un vistazo.
                if (hasDiet) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }
        }
    }
}

/** Qué significa cada punto de la rejilla, que eran tres colores sin explicar. */
@Composable
private fun CalendarLegend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(MaterialTheme.colorScheme.primary, "Entreno")
        LegendDot(MaterialTheme.colorScheme.outline, "Sin terminar")
        LegendDot(MaterialTheme.colorScheme.tertiary, "Comida")
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DietSummaryCard(text: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SessionRow(session: CalendarSession, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.dayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(formatLongDate(session.date))
                        session.durationMinutes?.let { append(" · $it min") }
                        session.kcalBurned?.let { append(" · ${it.toInt()} kcal") }
                        if (!session.isCompleted) append(" · sin terminar")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatLongDate(date: LocalDate): String =
    "${date.day} de ${monthNamesEs[date.monthNumber - 1]} de ${date.year}"
