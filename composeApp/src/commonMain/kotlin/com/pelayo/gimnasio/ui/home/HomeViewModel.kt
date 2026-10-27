package com.pelayo.gimnasio.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.calc.Achievement
import com.pelayo.gimnasio.domain.calc.NextWorkout
import com.pelayo.gimnasio.domain.calc.NextWorkoutPicker
import com.pelayo.gimnasio.domain.calc.ScheduledDay
import com.pelayo.gimnasio.domain.calc.StreakCalculator
import com.pelayo.gimnasio.domain.calc.StreakInfo
import com.pelayo.gimnasio.domain.calc.WeightTrend
import com.pelayo.gimnasio.domain.calc.WeightTrendCalculator
import com.pelayo.gimnasio.domain.model.BodyMeasurementEntry
import com.pelayo.gimnasio.domain.model.CalendarSession
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class HomeState(
    val loading: Boolean = true,
    val routineName: String? = null,
    val nextWorkout: NextWorkout = NextWorkout.NoRoutine,
    val balance: CalorieBalance? = null,
    val latestWeight: BodyMeasurementEntry? = null,
    /** Tendencia (media móvil de 7 días); null sin pesajes esta semana. */
    val weightTrend: WeightTrend? = null,
    /** Peso objetivo del perfil y fecha estimada de llegada al ritmo actual. */
    val targetWeightKg: Double? = null,
    val goalProjection: LocalDate? = null,
    val completedSessions: Long = 0,
    val catalogEmpty: Boolean = true,
    val profileIncomplete: Boolean = true,
    val streak: StreakInfo? = null,
    /** Logros recién alcanzados, para celebrarlos en la portada. */
    val latestAchievement: Achievement? = null,
    /** Entrenamientos empezados y sin cerrar; si no se ofrecen, se quedan atascados. */
    val unfinished: List<CalendarSession> = emptyList(),
)

class HomeViewModel(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val nutritionRepository: NutritionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val date = today()

            val activeRoutine = routineRepository.activeRoutineDetail()
            val nextWorkout = if (activeRoutine == null) {
                NextWorkout.NoRoutine
            } else {
                NextWorkoutPicker.pick(
                    days = activeRoutine.days.map {
                        ScheduledDay(it.id, it.dayIndex, it.name, it.weekday)
                    },
                    today = date,
                    lastCompletedDayId = workoutRepository.lastCompletedRoutineDayId(),
                )
            }

            val metrics = profileRepository.currentMetrics()
            val measurements = profileRepository.measurements()
            val profile = profileRepository.profile()
            val trend = WeightTrendCalculator.calculate(
                entries = measurements.map { it.date to it.weightKg },
                today = date,
            )

            val completedCount = workoutRepository.completedSessionCount()
            val streak = StreakCalculator.calculate(
                sessionDates = workoutRepository.completedSessionDates(),
                today = date,
                weeklyTarget = activeRoutine?.days?.size ?: 0,
                // El total real, no las fechas distintas: dos sesiones el mismo día
                // hacían que los logros fueran por detrás del contador de la portada.
                totalSessions = completedCount.toInt(),
            )
            val achievements = StreakCalculator.achievements(streak)

            _state.value = HomeState(
                loading = false,
                routineName = activeRoutine?.name,
                nextWorkout = nextWorkout,
                balance = nutritionRepository.calorieBalance(
                    date = date,
                    tdeeKcal = metrics?.tdee,
                    exerciseKcal = workoutRepository.kcalBurnedOn(date),
                ),
                latestWeight = measurements.firstOrNull(),
                // Media móvil, no "las dos últimas mediciones": el peso diario
                // oscila por agua, y la anterior podía ser de hace meses.
                weightTrend = trend,
                targetWeightKg = profile?.targetWeightKg,
                goalProjection = trend?.let {
                    WeightTrendCalculator.projectedDate(
                        currentKg = it.averageKg,
                        targetKg = profile?.targetWeightKg,
                        weeklyRateKg = it.weeklyRateKg,
                        today = date,
                    )
                },
                completedSessions = completedCount,
                catalogEmpty = routineRepository.isCatalogEmpty(),
                profileIncomplete = metrics == null,
                streak = streak,
                latestAchievement = achievements.lastOrNull { it.unlocked },
                // Todas, no solo la primera: dos sesiones huérfanas eran una visible
                // y otra atascada sin ninguna vía de retomarse o descartarse.
                unfinished = workoutRepository.unfinishedSessions(),
            )
        }
    }

    /** Abre (o retoma) la sesión del día propuesto y devuelve su id. */
    fun startWorkout(dayId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(workoutRepository.startSession(dayId)) }
    }

    fun discardUnfinished(sessionId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSession(sessionId)
            reload()
        }
    }
}
