package com.pelayo.gimnasio.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.ActiveWorkout
import com.pelayo.gimnasio.domain.model.SetLogItem
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

    // Cronómetros de serie en marcha, por ejercicio (instante de arranque en
    // epoch millis). Viven aquí y no en la composición: al cambiar de app hay
    // dispositivos que recrean la actividad, y el cronómetro debe seguir
    // corriendo. Se guarda el instante, no la cuenta: la pantalla recalcula el
    // tiempo contra el reloj en cada frame.
    private val _stopwatchStarts = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val stopwatchStarts: StateFlow<Map<Long, Long>> = _stopwatchStarts

    fun startStopwatch(exerciseId: Long) {
        _stopwatchStarts.value += (exerciseId to Clock.System.now().toEpochMilliseconds())
    }

    /** Para el cronómetro del ejercicio y devuelve los segundos aguantados. */
    fun stopStopwatch(exerciseId: Long): Int {
        val start = _stopwatchStarts.value[exerciseId] ?: return 0
        _stopwatchStarts.value -= exerciseId
        return ((Clock.System.now().toEpochMilliseconds() - start) / 1000).toInt()
    }

    /** Si es false, el aviso de fin de descanso puede llegar con retraso. */
    val exactAlarmsAllowed: Boolean get() = notifier.canScheduleExactAlarms()

    fun openExactAlarmSettings() = notifier.openExactAlarmSettings()

    init {
        reload()
    }

    /** Carga completa: plan del día, prerrelleno e historial de progresión. */
    fun reload() {
        viewModelScope.launch { _workout.value = repository.activeWorkout(sessionId) }
    }

    /**
     * Refresco barato tras apuntar o borrar una serie: solo vuelve a leer las
     * series de la sesión y las mete en el estado que ya teníamos. Evita
     * recalcular prerrelleno y progresión, que no cambian mientras entrenas.
     */
    private fun refreshLoggedSets() {
        viewModelScope.launch {
            val current = _workout.value ?: return@launch
            val logged = repository.loggedSetsBySession(sessionId)
            _workout.value = current.copy(
                exercises = current.exercises.map { exercise ->
                    exercise.copy(loggedSets = logged[exercise.exerciseId].orEmpty())
                },
            )
        }
    }

    fun logSet(
        exerciseId: Long,
        setNumber: Int,
        weightKg: Double?,
        reps: Int?,
        timeSeconds: Int?,
        restSeconds: Int,
        exerciseName: String,
        isWarmup: Boolean = false,
    ) {
        viewModelScope.launch {
            repository.logSet(sessionId, exerciseId, setNumber, weightKg, reps, timeSeconds, isWarmup)
            refreshLoggedSets()
            // Tras un calentamiento no se lanza el descanso completo de trabajo.
            if (restSeconds > 0 && !isWarmup) startRest(restSeconds, exerciseName)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            repository.deleteSet(setId)
            refreshLoggedSets()
        }
    }

    /** Reinserta una serie borrada, para el "Deshacer" del snackbar. */
    fun restoreSet(set: SetLogItem) {
        viewModelScope.launch {
            repository.logSet(
                sessionId = sessionId,
                exerciseId = set.exerciseId,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                timeSeconds = set.timeSeconds,
                isWarmup = set.isWarmup,
                rpe = set.rpe,
            )
            refreshLoggedSets()
        }
    }

    fun startRest(seconds: Int, exerciseName: String?) {
        timerJob?.cancel()
        _restTimer.value = RestTimerState(seconds, seconds, exerciseName)
        // El aviso lo dispara el sistema; esta corrutina solo mueve la cuenta
        // atrás de la pantalla y refresca la notificación mientras la app viva.
        notifier.startRest(seconds, exerciseName)
        // Contra el reloj, no contando ticks: en segundo plano el proceso se
        // congela y los ticks perdidos dejaban la cuenta atrás mintiendo al
        // volver. Así el primer tick de vuelta la pone donde toca.
        val endMillis = Clock.System.now().toEpochMilliseconds() + seconds * 1000L
        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(250)
                val left = ((endMillis - Clock.System.now().toEpochMilliseconds() + 999) / 1000)
                    .toInt().coerceAtLeast(0)
                if (left != remaining) {
                    remaining = left
                    _restTimer.value = _restTimer.value?.copy(secondsRemaining = remaining)
                    if (remaining > 0) notifier.updateCountdown(remaining)
                }
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
            // Desde el startTime persistido de la sesión, no desde que se abrió la
            // pantalla: retomar un entreno no debe reiniciar su duración. Si la
            // sesión quedó abierta de otro día, el dato ya no mide nada y se cae
            // al tiempo de pantalla.
            val persistedStart = repository.sessionStartTime(sessionId)
                ?.let { runCatching { kotlin.time.Instant.parse(it) }.getOrNull() }
            val start = persistedStart
                ?.takeIf { (Clock.System.now() - it).inWholeHours < 6 }
                ?: startedAt
            val minutes = ((Clock.System.now() - start).inWholeSeconds / 60).toInt().coerceAtLeast(1)
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
