package com.pelayo.gimnasio.ui.workout.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.ExerciseDetail
import com.pelayo.gimnasio.domain.model.PersonalRecords
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
    private val exerciseId: Long,
    private val repository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val state: StateFlow<ExerciseDetailUiState> = _state

    private val _history = MutableStateFlow<List<ProgressPoint>>(emptyList())
    val history: StateFlow<List<ProgressPoint>> = _history

    /** Mejores marcas; se enseñan aunque solo haya una sesión. */
    private val _records = MutableStateFlow<PersonalRecords?>(null)
    val records: StateFlow<PersonalRecords?> = _records

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val detail = repository.exerciseDetail(exerciseId)
            _state.value = if (detail != null) {
                ExerciseDetailUiState.Loaded(detail)
            } else {
                ExerciseDetailUiState.NotFound
            }
            _history.value = workoutRepository.progressHistory(exerciseId)
            _records.value = workoutRepository.personalRecords(exerciseId)
        }
    }

    /** Solo para ejercicios propios; los de wger no se editan. */
    fun updateCustomExercise(form: CustomExerciseForm) {
        viewModelScope.launch {
            repository.updateCustomExercise(
                id = exerciseId,
                name = form.name,
                description = form.description,
                exerciseType = form.exerciseType,
                primaryGroupIds = form.primaryGroupIds,
                secondaryGroupIds = form.secondaryGroupIds,
            )
            reload()
        }
    }

    /** false si está en una rutina o en el historial y no se puede borrar. */
    fun deleteCustomExercise(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.deleteCustomExercise(exerciseId))
        }
    }
}
