package com.pelayo.gimnasio.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.SessionRecord
import com.pelayo.gimnasio.domain.model.SetLogItem
import com.pelayo.gimnasio.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkoutSummaryViewModel(
    private val sessionId: Long,
    private val repository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary

    /** Récords batidos en esta sesión, para celebrarlos en la cabecera. */
    private val _records = MutableStateFlow<List<SessionRecord>>(emptyList())
    val records: StateFlow<List<SessionRecord>> = _records

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _summary.value = repository.summary(sessionId)
            _records.value = repository.newRecordsInSession(sessionId)
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
            recalculateAndReload()
        }
    }

    /**
     * Corrige una serie ya registrada. Después recalcula el gasto de la sesión,
     * que si no se quedaría con el valor congelado al terminarla.
     */
    fun updateSet(setId: Long, weightKg: Double?, reps: Int?, timeSeconds: Int?, rpe: Double? = null) {
        viewModelScope.launch {
            // isWarmup no se pasa: el repositorio conserva el flag que tuviera.
            repository.updateSet(setId, weightKg, reps, timeSeconds, rpe = rpe)
            recalculateAndReload()
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            repository.deleteSet(setId)
            recalculateAndReload()
        }
    }

    fun saveNotes(notes: String) {
        viewModelScope.launch {
            repository.updateSessionNotes(sessionId, notes)
            reload()
        }
    }

    fun deleteSession() {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            _deleted.value = true
        }
    }

    private suspend fun recalculateAndReload() {
        repository.recalculateSession(sessionId, profileRepository.latestMeasurement()?.weightKg)
        _summary.value = repository.summary(sessionId)
        _records.value = repository.newRecordsInSession(sessionId)
    }
}
