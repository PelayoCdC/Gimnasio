package com.pelayo.gimnasio.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.ActiveWorkout
import com.pelayo.gimnasio.platform.RestTimerNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RestTimerState(
    val totalSeconds: Int,
    val secondsRemaining: Int,
    val exerciseName: String?,
) {
    val progress: Float get() = if (totalSeconds <= 0) 0f else secondsRemaining.toFloat() / totalSeconds
}

@OptIn(ExperimentalTime::class)
class ActiveWorkoutViewModel(
    private val sessionId: Long,
    private val repository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val notifier: RestTimerNotifier,
) : ViewModel() {

    private val _workout = MutableStateFlow<ActiveWorkout?>(null)
    val workout: StateFlow<ActiveWorkout?> = _workout

    private val _restTimer = MutableStateFlow<RestTimerState?>(null)
    val restTimer: StateFlow<RestTimerState?> = _restTimer

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    private var timerJob: Job? = null
    private val startedAt = Clock.System.now()

    /** Si es false, el aviso de fin de descanso puede llegar con retraso. */
    val exactAlarmsAllowed: Boolean get() = notifier.canScheduleExactAlarms()

    fun openExactAlarmSettings() = notifier.openExactAlarmSettings()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _workout.value = repository.activeWorkout(sessionId) }
    }

    fun logSet(
        exerciseId: Long,
        setNumber: Int,
        weightKg: Double?,
        reps: Int?,
        timeSeconds: Int?,
        restSeconds: Int,
        exerciseName: String,
    ) {
        viewModelScope.launch {
            repository.logSet(sessionId, exerciseId, setNumber, weightKg, reps, timeSeconds)
            reload()
            if (restSeconds > 0) startRest(restSeconds, exerciseName)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            repository.deleteSet(setId)
            reload()
        }
    }

    fun startRest(seconds: Int, exerciseName: String?) {
        timerJob?.cancel()
        _restTimer.value = RestTimerState(seconds, seconds, exerciseName)
        // El aviso lo dispara el sistema; esta corrutina solo mueve la cuenta
        // atrás de la pantalla y refresca la notificación mientras la app viva.
        notifier.startRest(seconds, exerciseName)
        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _restTimer.value = _restTimer.value?.copy(secondsRemaining = remaining)
                if (remaining > 0) notifier.updateCountdown(remaining)
            }
            _restTimer.value = null
        }
    }

    fun addRestTime(extraSeconds: Int) {
        val current = _restTimer.value ?: return
        startRest(current.secondsRemaining + extraSeconds, current.exerciseName)
    }

    fun skipRest() {
        timerJob?.cancel()
        timerJob = null
        notifier.cancelRest()
        _restTimer.value = null
    }

    fun finishWorkout() {
        viewModelScope.launch {
            skipRest()
            val minutes = ((Clock.System.now() - startedAt).inWholeSeconds / 60).toInt().coerceAtLeast(1)
            val bodyWeight = profileRepository.latestMeasurement()?.weightKg
            val kcal = repository.estimateSessionKcal(sessionId, bodyWeight)
            repository.completeSession(sessionId, minutes, kcalBurned = kcal, notes = null)
            _finished.value = true
        }
    }

    /** Descarta la sesión si el usuario sale sin registrar nada. */
    fun discardIfEmpty() {
        viewModelScope.launch {
            val current = repository.activeWorkout(sessionId) ?: return@launch
            if (current.exercises.all { it.loggedSets.isEmpty() }) {
                repository.deleteSession(sessionId)
            }
        }
    }

    // A propósito no se cancela el descanso al destruirse la pantalla: si el
    // usuario sale o el sistema mata la app en mitad de un descanso, el aviso
    // programado debe llegar igual. Solo se cancela al saltarlo o al terminar.
}
