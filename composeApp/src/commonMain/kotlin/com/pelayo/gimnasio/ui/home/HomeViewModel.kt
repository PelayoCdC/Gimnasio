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
import com.pelayo.gimnasio.domain.model.BodyMeasurementEntry
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val routineName: String? = null,
    val nextWorkout: NextWorkout = NextWorkout.NoRoutine,
    val balance: CalorieBalance? = null,
    val latestWeight: BodyMeasurementEntry? = null,
    /** Diferencia con la medición anterior; null si solo hay una. */
    val weightDelta: Double? = null,
    val completedSessions: Long = 0,
    val catalogEmpty: Boolean = true,
    val profileIncomplete: Boolean = true,
    val streak: StreakInfo? = null,
    /** Logros recién alcanzados, para celebrarlos en la portada. */
    val latestAchievement: Achievement? = null,
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

            val streak = StreakCalculator.calculate(
                sessionDates = workoutRepository.completedSessionDates(),
                today = date,
                weeklyTarget = activeRoutine?.days?.size ?: 0,
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
                weightDelta = if (measurements.size >= 2) {
                    measurements[0].weightKg - measurements[1].weightKg
                } else {
                    null
                },
                completedSessions = workoutRepository.completedSessionCount(),
                catalogEmpty = routineRepository.isCatalogEmpty(),
                profileIncomplete = metrics == null,
                streak = streak,
                latestAchievement = achievements.lastOrNull { it.unlocked },
            )
        }
    }

    /** Abre (o retoma) la sesión del día propuesto y devuelve su id. */
    fun startWorkout(dayId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(workoutRepository.startSession(dayId)) }
    }
}
