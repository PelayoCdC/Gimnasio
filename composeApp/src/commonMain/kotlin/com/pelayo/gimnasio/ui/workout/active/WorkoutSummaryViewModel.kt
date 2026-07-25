package com.pelayo.gimnasio.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.WorkoutSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkoutSummaryViewModel(
    private val sessionId: Long,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary

    init {
        viewModelScope.launch { _summary.value = repository.summary(sessionId) }
    }
}
