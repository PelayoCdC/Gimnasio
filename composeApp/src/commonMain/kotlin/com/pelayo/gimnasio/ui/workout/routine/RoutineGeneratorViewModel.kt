package com.pelayo.gimnasio.ui.workout.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.domain.calc.BalanceReport
import com.pelayo.gimnasio.domain.calc.CandidateExercise
import com.pelayo.gimnasio.domain.calc.GeneratedRoutine
import com.pelayo.gimnasio.domain.calc.MuscleBalanceAnalyzer
import com.pelayo.gimnasio.domain.calc.MuscleVolumeEntry
import com.pelayo.gimnasio.domain.calc.RoutineGenerator
import com.pelayo.gimnasio.domain.model.EquipmentItem
import com.pelayo.gimnasio.domain.model.ExerciseComplexity
import com.pelayo.gimnasio.domain.model.ExperienceLevel
import com.pelayo.gimnasio.domain.model.MuscleRole
import com.pelayo.gimnasio.domain.model.RoutineGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneratedPreview(
    val routine: GeneratedRoutine,
    val balance: BalanceReport,
    val exerciseNames: Map<Long, String>,
    val exerciseComplexity: Map<Long, ExerciseComplexity> = emptyMap(),
)

class RoutineGeneratorViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    private var catalog: List<CandidateExercise> = emptyList()

    val daysPerWeek = MutableStateFlow(3)
    val goal = MutableStateFlow(RoutineGoal.HIPERTROFIA)
    val level = MutableStateFlow(ExperienceLevel.INTERMEDIO)
    val minutesPerSession = MutableStateFlow(RoutineGenerator.DEFAULT_MINUTES_PER_SESSION)

    /** null = las que diga el objetivo y el nivel; un número, lo que elija el usuario. */
    val setsPerExercise = MutableStateFlow<Int?>(null)
    val selectedEquipment = MutableStateFlow<Set<Long>>(emptySet())

    private val _equipment = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val equipment: StateFlow<List<EquipmentItem>> = _equipment

    private val _preview = MutableStateFlow<GeneratedPreview?>(null)
    val preview: StateFlow<GeneratedPreview?> = _preview

    private val _catalogEmpty = MutableStateFlow(false)
    val catalogEmpty: StateFlow<Boolean> = _catalogEmpty

    init {
        viewModelScope.launch {
            catalog = exerciseRepository.candidates()
            _catalogEmpty.value = catalog.isEmpty()
            // Máquinas y poleas no existen como equipamiento en wger; se ofrecen
            // como chips sintéticos para que "entreno en casa" pueda excluirlas.
            _equipment.value = listOf(
                EquipmentItem(RoutineGenerator.MACHINE_EQUIPMENT_ID, "Máquinas"),
                EquipmentItem(RoutineGenerator.PULLEY_EQUIPMENT_ID, "Poleas"),
            ) + exerciseRepository.equipment()
        }
    }

    fun setDays(days: Int) {
        daysPerWeek.value = days
        if (_preview.value != null) generate()
    }

    fun setGoal(value: RoutineGoal) {
        goal.value = value
        if (_preview.value != null) generate()
    }

    fun setLevel(value: ExperienceLevel) {
        level.value = value
        if (_preview.value != null) generate()
    }

    fun setMinutesPerSession(minutes: Int) {
        minutesPerSession.value = minutes
        if (_preview.value != null) generate()
    }

    /** Se vuelve a pulsar la misma opción y se deja decidir al objetivo otra vez. */
    fun setSetsPerExercise(sets: Int?) {
        setsPerExercise.value = sets
        if (_preview.value != null) generate()
    }

    fun toggleEquipment(id: Long) {
        selectedEquipment.value = selectedEquipment.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        if (_preview.value != null) generate()
    }

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating

    fun generate() {
        viewModelScope.launch {
            _generating.value = true
            try {
                if (catalog.isEmpty()) {
                    catalog = exerciseRepository.candidates()
                    _catalogEmpty.value = catalog.isEmpty()
                    if (catalog.isEmpty()) return@launch
                }

                // Fuera del hilo principal: con el catálogo entero, generar y
                // reequilibrar es trabajo de verdad y congelaba la pantalla.
                _preview.value = withContext(Dispatchers.Default) {
                    val routine = RoutineGenerator.generate(
                        daysPerWeek = daysPerWeek.value,
                        goal = goal.value,
                        catalog = catalog,
                        availableEquipmentIds = selectedEquipment.value.takeIf { it.isNotEmpty() },
                        level = level.value,
                        minutesPerSession = minutesPerSession.value,
                        setsPerExercise = setsPerExercise.value,
                    )
                    GeneratedPreview(
                        routine = routine,
                        // Con el nivel: el mismo umbral que usó el generador para
                        // reequilibrar, para que el aviso y la propuesta cuadren.
                        balance = MuscleBalanceAnalyzer.analyze(
                            routine.toVolumeEntries(catalog),
                            routine.level,
                        ),
                        exerciseNames = catalog.associate { it.id to it.name },
                        exerciseComplexity = catalog.filter { it.id in routine.exerciseIds }
                            .associate { it.id to it.complexity },
                    )
                }
            } finally {
                _generating.value = false
            }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val routine = _preview.value?.routine ?: return
        viewModelScope.launch { onSaved(routineRepository.saveGenerated(routine)) }
    }
}

/** Ids de todos los ejercicios de la propuesta, sin repetir. */
private val GeneratedRoutine.exerciseIds: Set<Long>
    get() = days.flatMapTo(mutableSetOf()) { it.exerciseIds }

/** Traduce la propuesta a entradas de volumen para poder analizar su balance antes de guardarla. */
private fun GeneratedRoutine.toVolumeEntries(catalog: List<CandidateExercise>): List<MuscleVolumeEntry> {
    val byId = catalog.associateBy { it.id }
    val sets = prescription.sets
    return days.flatMap { day ->
        day.exerciseIds.flatMap { exerciseId ->
            val exercise = byId[exerciseId] ?: return@flatMap emptyList()
            exercise.primaryGroupIds.map { MuscleVolumeEntry(sets, MuscleRole.PRIMARY, it) } +
                exercise.secondaryGroupIds.map { MuscleVolumeEntry(sets, MuscleRole.SECONDARY, it) }
        }
    }
}
