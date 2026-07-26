package com.pelayo.gimnasio.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pelayo.gimnasio.domain.calc.Achievement
import com.pelayo.gimnasio.domain.calc.NextWorkout
import com.pelayo.gimnasio.domain.calc.StreakInfo
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.domain.model.formatKg
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartWorkout: (sessionId: Long) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Gimnasio") }) }) { padding ->
        if (state.loading) {
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
            // Primeros pasos: hasta que no haya catálogo y perfil, nada más tiene sentido.
            if (state.catalogEmpty) {
                item {
                    SetupCard(
                        title = "Descarga el catálogo de ejercicios",
                        text = "Es lo primero: sin él no puedes crear rutinas ni registrar entrenos.",
                        action = "Ir al catálogo",
                        onClick = onOpenCatalog,
                    )
                }
            }
            if (state.profileIncomplete) {
                item {
                    SetupCard(
                        title = "Completa tu perfil",
                        text = "Con tu altura, fecha de nacimiento y peso puedo calcular tu gasto calórico.",
                        action = "Ir al perfil",
                        onClick = onOpenProfile,
                    )
                }
            }

            // Va antes que la propuesta del día: si dejaste algo a medias, eso
            // es lo primero que hay que resolver.
            state.unfinished?.let { session ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Tienes un entrenamiento sin terminar",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "${session.dayName} · ${session.date}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onStartWorkout(session.sessionId) }) {
                                    Text("Retomar")
                                }
                                OutlinedButton(onClick = viewModel::discardUnfinished) {
                                    Text("Descartar")
                                }
                            }
                        }
                    }
                }
            }

            item {
                NextWorkoutCard(
                    routineName = state.routineName,
                    next = state.nextWorkout,
                    onStart = { dayId -> viewModel.startWorkout(dayId, onStartWorkout) },
                    onOpenRoutines = onOpenRoutines,
                )
            }

            state.streak?.takeIf { it.totalSessions > 0 }?.let { streak ->
                item { StreakCard(streak = streak, achievement = state.latestAchievement) }
            }

            state.balance?.let { balance ->
                item { TodayBalanceCard(balance = balance, onClick = onOpenNutrition) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.MonitorWeight,
                        label = "Peso actual",
                        value = state.latestWeight?.let { "${it.weightKg.formatKg()} kg" } ?: "—",
                        hint = state.weightDelta?.let { delta ->
                            when {
                                abs(delta) < 0.05 -> "Igual que la última vez"
                                delta > 0 -> "+${delta.formatKg()} kg desde la anterior"
                                else -> "${delta.formatKg()} kg desde la anterior"
                            }
                        },
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.FitnessCenter,
                        label = "Entrenos",
                        value = state.completedSessions.toString(),
                        hint = "Sesiones completadas",
                    )
                }
            }
        }
    }
}

@Composable
private fun NextWorkoutCard(
    routineName: String?,
    next: NextWorkout,
    onStart: (Long) -> Unit,
    onOpenRoutines: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (next) {
                is NextWorkout.NoRoutine -> {
                    Text("Sin rutina activa", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Crea una rutina o deja que te genere una equilibrada según tus días y tu material.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenRoutines) { Text("Crear rutina") }
                }

                is NextWorkout.RestDay -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Hoy toca descansar", style = MaterialTheme.typography.titleMedium)
                    }
                    next.nextDay?.let { day ->
                        Text(
                            buildString {
                                append("Siguiente: ${day.name}")
                                day.weekday?.let { append(" · ${weekdayNamesEs[it - 1].lowercase()}") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { onStart(day.id) }) {
                            Text("Entrenar igualmente")
                        }
                    }
                }

                is NextWorkout.Scheduled -> WorkoutProposal(
                    routineName = routineName,
                    dayName = next.day.name,
                    subtitle = "Hoy te toca",
                    onStart = { onStart(next.day.id) },
                )

                is NextWorkout.NextInRotation -> WorkoutProposal(
                    routineName = routineName,
                    dayName = next.day.name,
                    subtitle = "Siguiente en tu rutina",
                    onStart = { onStart(next.day.id) },
                )
            }
        }
    }
}

@Composable
private fun WorkoutProposal(
    routineName: String?,
    dayName: String,
    subtitle: String,
    onStart: () -> Unit,
) {
    Text(
        subtitle,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(dayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    routineName?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Empezar entrenamiento")
    }
}

@Composable
private fun StreakCard(streak: StreakInfo, achievement: Achievement?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        streak.weekStreak >= 2 -> "${streak.weekStreak} semanas seguidas"
                        streak.weekStreak == 1 -> "Llevas 1 semana cumpliendo"
                        else -> "Tu semana"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (streak.weeklyTarget > 0) {
                LinearProgressIndicator(
                    progress = {
                        (streak.sessionsThisWeek.toFloat() / streak.weeklyTarget).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (streak.weekCompleted) {
                        "Semana completada: ${streak.sessionsThisWeek} de ${streak.weeklyTarget}"
                    } else {
                        "${streak.sessionsThisWeek} de ${streak.weeklyTarget} entrenos esta semana"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            streak.daysSinceLastWorkout?.let { days ->
                if (days >= 4) {
                    Text(
                        when (days) {
                            in 4..7 -> "Hace $days días que no entrenas."
                            else -> "Hace $days días del último entreno. Retomarlo suave es buena idea."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            achievement?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${it.title} · ${it.description}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayBalanceCard(balance: CalorieBalance, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Hoy has comido", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${balance.intakeKcal.roundToInt()} kcal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            val expenditure = balance.expenditureKcal
            if (expenditure == null) {
                Text(
                    "Completa tu perfil para ver el balance del día.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    progress = { (balance.intakeKcal / expenditure).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                val diff = balance.balanceKcal ?: 0.0
                Text(
                    if (diff >= 0) {
                        "Superávit de ${diff.roundToInt()} kcal sobre un gasto de ${expenditure.roundToInt()}"
                    } else {
                        "Déficit de ${abs(diff).roundToInt()} kcal sobre un gasto de ${expenditure.roundToInt()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    hint: String?,
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, text: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}
