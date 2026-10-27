package com.pelayo.gimnasio.ui.workout.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.CatalogSyncService
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.SyncProgress
import com.pelayo.gimnasio.domain.model.EquipmentItem
import com.pelayo.gimnasio.domain.model.ExerciseListItem
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data class Running(val done: Int, val total: Int) : SyncUiState
    data class Finished(val imported: Int) : SyncUiState
    data class Failed(val message: String) : SyncUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CatalogViewModel(
    private val repository: ExerciseRepository,
    private val syncService: CatalogSyncService,
) : ViewModel() {

    val query = MutableStateFlow("")
    val muscleGroupFilter = MutableStateFlow<Long?>(null)
    val equipmentFilter = MutableStateFlow<Long?>(null)

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState

    val exerciseCount: StateFlow<Long> = repository.exerciseCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    val muscleGroups: List<MuscleGroupInfo> = repository.muscleGroups()

    private val _equipment = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val equipment: StateFlow<List<EquipmentItem>> = _equipment

    val exercises: StateFlow<List<ExerciseListItem>> =
        combine(query.debounce(200), muscleGroupFilter, equipmentFilter) { q, m, e -> Triple(q, m, e) }
            .flatMapLatest { (q, m, e) -> repository.searchExercises(q, m, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _equipment.value = repository.equipment() }
    }

    /** Da de alta un ejercicio propio y avisa con su id para poder abrirlo. */
    fun createCustomExercise(form: CustomExerciseForm, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createCustomExercise(
                name = form.name,
                description = form.description,
                exerciseType = form.exerciseType,
                primaryGroupIds = form.primaryGroupIds,
                secondaryGroupIds = form.secondaryGroupIds,
            )
            onCreated(id)
        }
    }

    fun startSync() {
        if (_syncState.value is SyncUiState.Running) return
        viewModelScope.launch {
            syncService.sync().collect { progress ->
                _syncState.value = when (progress) {
                    is SyncProgress.Running -> SyncUiState.Running(progress.done, progress.total)
                    is SyncProgress.Finished -> SyncUiState.Finished(progress.imported)
                    is SyncProgress.Failed -> SyncUiState.Failed(progress.message)
                }
            }
            viewModelScope.launch { _equipment.value = repository.equipment() }
        }
    }
}
