package com.pelayo.gimnasio.ui.workout.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.calc.BalanceReport
import com.pelayo.gimnasio.domain.calc.CandidateExercise
import com.pelayo.gimnasio.domain.calc.RoutineAdvisor
import com.pelayo.gimnasio.domain.calc.RoutineExerciseRef
import com.pelayo.gimnasio.domain.calc.RoutineSuggestion
import com.pelayo.gimnasio.domain.model.ExerciseDetail
import com.pelayo.gimnasio.domain.model.ExerciseListItem
import com.pelayo.gimnasio.domain.model.RoutineDetail
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.RoutineSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineListViewModel(
    private val repository: RoutineRepository,
) : ViewModel() {

    val routines: StateFlow<List<RoutineSummary>> = repository.routines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createRoutine(name: String, goal: RoutineGoal, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createRoutine(name.trim().ifBlank { "Nueva rutina" }, null, goal)
            onCreated(id)
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch { repository.deleteRoutine(id) }
    }

    fun duplicateRoutine(id: Long, onDuplicated: (Long) -> Unit) {
        viewModelScope.launch {
            repository.duplicateRoutine(id)?.let(onDuplicated)
        }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActive(id) }
    }
}

class RoutineDetailViewModel(
    private val routineId: Long,
    private val repository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    /** Abre (o retoma) la sesión de entrenamiento de un día y devuelve su id. */
    fun startWorkout(dayId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(workoutRepository.startSession(dayId)) }
    }

    private val _routine = MutableStateFlow<RoutineDetail?>(null)
    val routine: StateFlow<RoutineDetail?> = _routine

    private val _balance = MutableStateFlow<BalanceReport?>(null)
    val balance: StateFlow<BalanceReport?> = _balance

    private val _suggestions = MutableStateFlow<List<RoutineSuggestion>>(emptyList())
    val suggestions: StateFlow<List<RoutineSuggestion>> = _suggestions

    /** El resto de rutinas, para poder copiarles un día. */
    val otherRoutines: StateFlow<List<RoutineSummary>> = repository.routines()
        .map { list -> list.filter { it.id != routineId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** El catálogo solo hace falta para sugerir, y no cambia mientras se edita. */
    private var catalog: List<CandidateExercise> = emptyList()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val detail = repository.routineDetail(routineId)
            val report = repository.balanceReport(routineId)
            _routine.value = detail
            _balance.value = report

            if (catalog.isEmpty()) catalog = exerciseRepository.candidates()
            _suggestions.value = if (detail == null || report == null) {
                emptyList()
            } else {
                RoutineAdvisor.suggest(
                    report = report,
                    routineExercises = detail.days.flatMap { day ->
                        day.exercises.map { item ->
                            RoutineExerciseRef(
                                routineExerciseId = item.id,
                                exerciseId = item.exerciseId,
                                name = item.exerciseName,
                                dayId = day.id,
                                dayName = day.name,
                            )
                        }
                    },
                    catalog = catalog,
                )
            }
        }
    }

    /** Aplica una sugerencia: añade el ejercicio propuesto o sustituye el que sobra. */
    fun applySuggestion(suggestion: RoutineSuggestion) {
        viewModelScope.launch {
            val goal = _routine.value?.goal ?: RoutineGoal.HIPERTROFIA
            val replaces = suggestion.replacesRoutineExerciseId
            if (replaces == null) {
                repository.addExerciseToDay(suggestion.dayId, suggestion.exerciseId, goal)
            } else {
                repository.replaceRoutineExercise(replaces, suggestion.exerciseId, goal)
            }
            reload()
        }
    }

    /** Descarta una sugerencia hasta la próxima recarga, sin tocar la rutina. */
    fun dismissSuggestion(suggestion: RoutineSuggestion) {
        _suggestions.value = _suggestions.value - suggestion
    }

    fun copyDayTo(dayId: Long, targetRoutineId: Long, onCopied: () -> Unit) {
        viewModelScope.launch {
            repository.copyDayToRoutine(dayId, targetRoutineId)
            onCopied()
        }
    }

    fun addDay(name: String, weekday: Int?) {
        viewModelScope.launch {
            repository.addDay(routineId, name.trim().ifBlank { "Día nuevo" }, weekday)
            reload()
        }
    }

    fun deleteDay(dayId: Long) {
        viewModelScope.launch {
            repository.deleteDay(dayId)
            reload()
        }
    }

    /** Renombra un día o le cambia el día de la semana, sin tocar sus ejercicios. */
    fun updateDay(dayId: Long, name: String, weekday: Int?) {
        viewModelScope.launch {
            val day = _routine.value?.days?.firstOrNull { it.id == dayId } ?: return@launch
            repository.updateDay(dayId, name.trim().ifBlank { day.name }, weekday, day.dayIndex)
            reload()
        }
    }

    fun renameRoutine(name: String, goal: RoutineGoal) {
        viewModelScope.launch {
            val current = _routine.value ?: return@launch
            repository.updateRoutine(routineId, name, current.description, goal)
            reload()
        }
    }

    fun setActive() {
        viewModelScope.launch {
            repository.setActive(routineId)
            reload()
        }
    }

    fun removeExercise(routineExerciseId: Long) {
        viewModelScope.launch {
            repository.removeRoutineExercise(routineExerciseId)
            reload()
        }
    }

    fun moveExercise(routineExerciseId: Long, delta: Int) {
        viewModelScope.launch {
            repository.moveRoutineExercise(routineExerciseId, delta)
            reload()
        }
    }

    fun updateExercise(
        id: Long,
        sets: Int,
        repsMin: Int?,
        repsMax: Int?,
        timeSeconds: Int?,
        weight: Double?,
        restSeconds: Int,
        position: Int,
    ) {
        viewModelScope.launch {
            repository.updateRoutineExercise(
                id = id,
                targetSets = sets,
                targetRepsMin = repsMin,
                targetRepsMax = repsMax,
                targetTimeSeconds = timeSeconds,
                targetWeight = weight,
                restSeconds = restSeconds,
                notes = null,
                position = position,
            )
            reload()
        }
    }
}

/** Selector de ejercicios para añadir a un día de rutina. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ExercisePickerViewModel(
    private val dayId: Long,
    private val routineId: Long,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val muscleGroupFilter = MutableStateFlow<Long?>(null)

    val muscleGroups = exerciseRepository.muscleGroups()

    val exercises: StateFlow<List<ExerciseListItem>> =
        combine(query.debounce(200), muscleGroupFilter) { q, group -> q to group }
            .flatMapLatest { (q, group) -> exerciseRepository.searchExercises(q, group, null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMuscleGroup(id: Long?) {
        muscleGroupFilter.value = if (muscleGroupFilter.value == id) null else id
    }

    /** Ficha del ejercicio que se está mirando antes de decidir si se añade. */
    private val _preview = MutableStateFlow<ExerciseDetail?>(null)
    val preview: StateFlow<ExerciseDetail?> = _preview

    private val _loadingPreview = MutableStateFlow(false)
    val loadingPreview: StateFlow<Boolean> = _loadingPreview

    fun showPreview(exerciseId: Long) {
        viewModelScope.launch {
            _loadingPreview.value = true
            _preview.value = exerciseRepository.exerciseDetail(exerciseId)
            _loadingPreview.value = false
        }
    }

    fun hidePreview() {
        _preview.value = null
    }

    fun addExercise(exerciseId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val routine = routineRepository.routineDetail(routineId)
            routineRepository.addExerciseToDay(
                dayId = dayId,
                exerciseId = exerciseId,
                goal = routine?.goal ?: RoutineGoal.HIPERTROFIA,
            )
            onDone()
        }
    }
}
