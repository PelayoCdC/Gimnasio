package com.pelayo.gimnasio.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
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

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _summary.value = repository.summary(sessionId) }
    }

    /**
     * Corrige una serie ya registrada. Después recalcula el gasto de la sesión,
     * que si no se quedaría con el valor congelado al terminarla.
     */
    fun updateSet(setId: Long, weightKg: Double?, reps: Int?, timeSeconds: Int?) {
        viewModelScope.launch {
            repository.updateSet(setId, weightKg, reps, timeSeconds)
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
    }
}
