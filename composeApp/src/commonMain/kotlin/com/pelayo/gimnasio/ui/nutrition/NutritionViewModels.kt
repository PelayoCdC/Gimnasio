package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.domain.model.DayNutrition
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.platform.BarcodeScanner
import com.pelayo.gimnasio.platform.ScanResult
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class FoodDiaryViewModel(
    private val repository: NutritionRepository,
    private val profileRepository: ProfileRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _date = MutableStateFlow(today())
    val date: StateFlow<LocalDate> = _date

    private val _day = MutableStateFlow(DayNutrition(today(), emptyMap(), Macros()))
    val day: StateFlow<DayNutrition> = _day

    private val _balance = MutableStateFlow<CalorieBalance?>(null)
    val balance: StateFlow<CalorieBalance?> = _balance

    init {
        reload()
    }

    fun shiftDay(days: Int) {
        _date.value = _date.value.plus(DatePeriod(days = days))
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val date = _date.value
            _day.value = repository.dayNutrition(date)

            val tdee = profileRepository.currentMetrics()?.tdee
            val exerciseKcal = workoutRepository.sessionsByDate(date, date)[date]
                .orEmpty()
                .sumOf { it.kcalBurned ?: 0.0 }
            _balance.value = repository.calorieBalance(date, tdee, exerciseKcal)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteLogEntry(id)
            reload()
        }
    }
}

sealed interface OnlineSearchState {
    data object Idle : OnlineSearchState
    data object Loading : OnlineSearchState
    data class Failed(val message: String) : OnlineSearchState
    data class Done(val count: Int) : OnlineSearchState
}

/** Resultado del escaneo listo para la UI. */
sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data class Found(val food: FoodItem) : ScanUiState
    data class NotFound(val barcode: String) : ScanUiState
    data class Failed(val message: String) : ScanUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodSearchViewModel(
    private val repository: NutritionRepository,
    private val barcodeScanner: BarcodeScanner,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _results = MutableStateFlow<List<FoodItem>>(emptyList())
    val results: StateFlow<List<FoodItem>> = _results

    private val _onlineState = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Idle)
    val onlineState: StateFlow<OnlineSearchState> = _onlineState

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState

    val scannerAvailable: Boolean get() = barcodeScanner.isAvailable()

    init {
        searchLocal()
    }

    /** Escanea un código y busca el producto: primero en local, luego en Open Food Facts. */
    fun scanBarcode() {
        if (_scanState.value is ScanUiState.Scanning) return
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            when (val scan = barcodeScanner.scan()) {
                is ScanResult.Cancelled -> _scanState.value = ScanUiState.Idle
                is ScanResult.NotAvailable -> _scanState.value = ScanUiState.Failed(
                    "Este dispositivo no puede escanear códigos.",
                )
                is ScanResult.Failed -> _scanState.value = ScanUiState.Failed(scan.message)
                is ScanResult.Success -> {
                    repository.findByBarcode(scan.barcode)
                        .onSuccess { food ->
                            _scanState.value = if (food == null) {
                                ScanUiState.NotFound(scan.barcode)
                            } else {
                                searchLocal()
                                ScanUiState.Found(food)
                            }
                        }
                        .onFailure {
                            _scanState.value = ScanUiState.Failed(
                                it.message ?: "No se pudo consultar Open Food Facts",
                            )
                        }
                }
            }
        }
    }

    fun dismissScan() {
        _scanState.value = ScanUiState.Idle
    }

    fun onQueryChange(value: String) {
        query.value = value
        _onlineState.value = OnlineSearchState.Idle
        searchLocal()
    }

    private fun searchLocal() {
        viewModelScope.launch { _results.value = repository.searchLocal(query.value) }
    }

    /** Búsqueda online explícita: consulta Open Food Facts y cachea el resultado. */
    fun searchOnline() {
        val text = query.value.trim()
        if (text.length < 2) return
        _onlineState.value = OnlineSearchState.Loading
        viewModelScope.launch {
            repository.searchOnline(text)
                .onSuccess { found ->
                    _onlineState.value = OnlineSearchState.Done(found.size)
                    searchLocal()
                }
                .onFailure {
                    _onlineState.value = OnlineSearchState.Failed(
                        it.message ?: "No se pudo conectar con Open Food Facts",
                    )
                }
        }
    }

    fun createCustomFood(
        name: String,
        brand: String?,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val id = repository.createCustomFood(name, brand, kcal, protein, carbs, fat)
            searchLocal()
            onCreated(id)
        }
    }

    fun logFood(date: LocalDate, mealType: MealType, foodId: Long, grams: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logFood(date, mealType, foodId, grams)
            onDone()
        }
    }
}
