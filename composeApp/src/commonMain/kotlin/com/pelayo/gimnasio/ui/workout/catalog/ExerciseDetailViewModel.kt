package com.pelayo.gimnasio.ui.workout.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.ExerciseDetail
import com.pelayo.gimnasio.domain.model.ProgressPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState
    data class Loaded(val detail: ExerciseDetail) : ExerciseDetailUiState
    data object NotFound : ExerciseDetailUiState
}

class ExerciseDetailViewModel(
    exerciseId: Long,
    private val repository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val state: StateFlow<ExerciseDetailUiState> = _state

    private val _history = MutableStateFlow<List<ProgressPoint>>(emptyList())
    val history: StateFlow<List<ProgressPoint>> = _history

    init {
        viewModelScope.launch {
            val detail = repository.exerciseDetail(exerciseId)
            _state.value = if (detail != null) {
                ExerciseDetailUiState.Loaded(detail)
            } else {
                ExerciseDetailUiState.NotFound
            }
            _history.value = workoutRepository.progressHistory(exerciseId)
        }
    }
}
