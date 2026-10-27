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
import com.pelayo.gimnasio.domain.model.EquipmentItem
import com.pelayo.gimnasio.domain.model.ExerciseDetail
import com.pelayo.gimnasio.domain.model.ExerciseListItem
import com.pelayo.gimnasio.domain.model.RoutineDetail
import com.pelayo.gimnasio.domain.model.RoutineExerciseItem
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.platform.FileTransfer
import com.pelayo.gimnasio.domain.model.RoutineImportResult
import com.pelayo.gimnasio.domain.model.RoutineSummary
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

/** Qué contar al usuario después de importar una rutina de un fichero. */
sealed interface RoutineImportUiState {
    data object Idle : RoutineImportUiState
    data object Working : RoutineImportUiState
    data class Done(val result: RoutineImportResult) : RoutineImportUiState
    data class Failed(val message: String) : RoutineImportUiState
}

class RoutineListViewModel(
    private val repository: RoutineRepository,
    private val fileTransfer: FileTransfer,
) : ViewModel() {

    val routines: StateFlow<List<RoutineSummary>> = repository.routines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<RoutineImportUiState>(RoutineImportUiState.Idle)
    val importState: StateFlow<RoutineImportUiState> = _importState

    /**
     * Lee una rutina de un fichero de texto. Se pide el tipo genérico de texto y no
     * `text/markdown` porque el selector de Android no reconoce el segundo en todos
     * los sitios y dejaría el fichero en gris.
     */
    fun importRoutine() {
        if (_importState.value is RoutineImportUiState.Working) return
        _importState.value = RoutineImportUiState.Working
        viewModelScope.launch {
            val content = fileTransfer.readText("text/*")
            if (content == null) {
                _importState.value = RoutineImportUiState.Idle
                return@launch
            }
            val result = runCatching { repository.importRoutineMarkdown(content) }
            _importState.value = when {
                result.isFailure -> RoutineImportUiState.Failed(
                    result.exceptionOrNull()?.message ?: "No se pudo leer el fichero",
                )
                result.getOrNull() == null -> RoutineImportUiState.Failed(
                    "Ese fichero no parece una rutina: hace falta un título con # y los días con ##.",
                )
                else -> RoutineImportUiState.Done(result.getOrThrow()!!)
            }
        }
    }

    fun dismissImportMessage() {
        _importState.value = RoutineImportUiState.Idle
    }

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

/** Ejercicio que se ofrece como recambio de otro. */
data class ExerciseAlternative(
    val exerciseId: Long,
    val name: String,
    val styleLabel: String,
    val isCompound: Boolean,
)

/** Más de veinte alternativas ya no se leen: se elige del catálogo entero. */
private const val MAX_ALTERNATIVES = 20

class RoutineDetailViewModel(
    private val routineId: Long,
    private val repository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val fileTransfer: FileTransfer,
) : ViewModel() {

    /**
     * Guarda la rutina como Markdown. Sale legible a propósito: la gracia es poder
     * mandársela a alguien o pegársela a un agente para que la revise.
     */
    fun exportRoutine(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val markdown = repository.exportRoutineMarkdown(routineId)
            if (markdown == null) {
                onResult("No se pudo preparar la rutina")
                return@launch
            }
            val name = _routine.value?.name.orEmpty().ifBlank { "rutina" }
            val fileName = "${name.replace(Regex("""[^\p{L}\d]+"""), "-").trim('-').lowercase()}.md"
            val saved = runCatching { fileTransfer.saveText(fileName, "text/markdown", markdown) }
            onResult(
                when {
                    saved.isFailure -> "No se pudo guardar el fichero"
                    saved.getOrDefault(false) -> "Guardada como $fileName"
                    else -> "" // cancelar no es un error y no se anuncia
                },
            )
        }
    }

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

    /** El catálogo solo hace falta para sugerir y para cambiar ejercicios, y no cambia mientras se edita. */
    private var catalog: List<CandidateExercise> = emptyList()

    /** Alternativas del ejercicio que se está cambiando; vacío mientras no se pide ninguno. */
    private val _alternatives = MutableStateFlow<List<ExerciseAlternative>>(emptyList())
    val alternatives: StateFlow<List<ExerciseAlternative>> = _alternatives

    /** true mientras se buscan alternativas: el diálogo no debe decir "no hay" antes de tiempo. */
    private val _alternativesLoading = MutableStateFlow(false)
    val alternativesLoading: StateFlow<Boolean> = _alternativesLoading

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
                // En Default: recorre el catálogo entero con los clasificadores.
                withContext(Dispatchers.Default) {
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

    /**
     * Guarda el nombre y el objetivo, y de paso las series si el usuario las ha
     * cambiado. [sets] a null significa "deja cada ejercicio como está".
     */
    fun renameRoutine(name: String, goal: RoutineGoal, sets: Int? = null) {
        viewModelScope.launch {
            val current = _routine.value ?: return@launch
            repository.updateRoutine(routineId, name, current.description, goal)
            sets?.let { repository.setSetsForRoutine(routineId, it) }
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

    /**
     * Ejercicios por los que se puede cambiar uno de la rutina: los que trabajan su
     * mismo grupo, empezando por los que se le parecen (compuesto por compuesto,
     * aislamiento por aislamiento) y con el material más cómodo primero.
     */
    fun loadAlternatives(item: RoutineExerciseItem) {
        viewModelScope.launch {
            _alternativesLoading.value = true
            try {
                loadAlternativesNow(item)
            } finally {
                _alternativesLoading.value = false
            }
        }
    }

    private suspend fun loadAlternativesNow(item: RoutineExerciseItem) {
        if (catalog.isEmpty()) catalog = exerciseRepository.candidates()
        val current = catalog.firstOrNull { it.id == item.exerciseId }
        val groups = current?.primaryGroupIds.orEmpty()
        val inRoutine = _routine.value?.days
            ?.flatMap { day -> day.exercises.map { it.exerciseId } }
            .orEmpty()
            .toSet()

        _alternatives.value = if (groups.isEmpty()) {
            emptyList()
        } else {
            // En Default: ordenar cientos de candidatos pasando los clasificadores
            // de palabras clave no es trabajo para el hilo de la UI.
            withContext(Dispatchers.Default) {
                val candidates = catalog.filter {
                    it.id !in inRoutine &&
                        it.isGymExercise &&
                        it.primaryGroupIds.any { group -> group in groups }
                }
                val styleById = candidates.associate { it.id to it.style }
                val complexityById = candidates.associate { it.id to it.complexity }
                candidates
                    .sortedWith(
                        compareByDescending<CandidateExercise> { it.isCompound == current?.isCompound }
                            .thenBy { styleById.getValue(it.id).priority }
                            .thenBy { complexityById.getValue(it.id) }
                            .thenBy { it.name },
                    )
                    .take(MAX_ALTERNATIVES)
                    .map {
                        ExerciseAlternative(
                            exerciseId = it.id,
                            name = it.name,
                            styleLabel = styleById.getValue(it.id).labelEs,
                            isCompound = it.isCompound,
                        )
                    }
            }
        }
    }

    fun clearAlternatives() {
        _alternatives.value = emptyList()
    }

    /** Aplica el orden que dejó el arrastre, de una vez. */
    fun reorderExercises(dayId: Long, orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderRoutineExercises(dayId, orderedIds)
            reload()
        }
    }

    /** Cambia un ejercicio de la rutina por otro, conservando series y descanso. */
    fun replaceExercise(routineExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch {
            val goal = _routine.value?.goal ?: RoutineGoal.HIPERTROFIA
            repository.replaceRoutineExercise(routineExerciseId, newExerciseId, goal)
            _alternatives.value = emptyList()
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
    val equipmentFilter = MutableStateFlow<Long?>(null)

    val muscleGroups = exerciseRepository.muscleGroups()

    private val _equipment = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val equipment: StateFlow<List<EquipmentItem>> = _equipment

    init {
        viewModelScope.launch { _equipment.value = exerciseRepository.equipment() }
    }

    val exercises: StateFlow<List<ExerciseListItem>> =
        combine(query.debounce(200), muscleGroupFilter, equipmentFilter) { q, group, eq ->
            Triple(q, group, eq)
        }
            .flatMapLatest { (q, group, eq) -> exerciseRepository.searchExercises(q, group, eq) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMuscleGroup(id: Long?) {
        muscleGroupFilter.value = if (muscleGroupFilter.value == id) null else id
    }

    fun setEquipment(id: Long?) {
        equipmentFilter.value = if (equipmentFilter.value == id) null else id
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

    /** Cuántos se han añadido en esta visita, para decirlo en la pantalla. */
    private val _addedCount = MutableStateFlow(0)
    val addedCount: StateFlow<Int> = _addedCount

    /**
     * Añade sin cerrar la pantalla: montar un día son varios ejercicios y cada
     * uno costaba un viaje de ida y vuelta. [onAdded] avisa con el nombre para
     * el snackbar.
     */
    fun addExercise(exerciseId: Long, onAdded: (String) -> Unit = {}) {
        viewModelScope.launch {
            val routine = routineRepository.routineDetail(routineId)
            routineRepository.addExerciseToDay(
                dayId = dayId,
                exerciseId = exerciseId,
                goal = routine?.goal ?: RoutineGoal.HIPERTROFIA,
            )
            _addedCount.value++
            val name = exercises.value.firstOrNull { it.id == exerciseId }?.name
                ?: _preview.value?.name.orEmpty()
            onAdded(name)
        }
    }
}
