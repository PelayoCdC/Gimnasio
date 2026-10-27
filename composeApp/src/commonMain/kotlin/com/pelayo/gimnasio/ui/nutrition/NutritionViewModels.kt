package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.CalorieBalance
import com.pelayo.gimnasio.domain.model.DayNutrition
import com.pelayo.gimnasio.domain.model.FoodCounts
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.FoodLogItem
import com.pelayo.gimnasio.domain.model.FoodUnit
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.platform.BarcodeScanner
import com.pelayo.gimnasio.platform.ScanResult
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class FoodDiaryViewModel(
    private val repository: NutritionRepository,
    private val profileRepository: ProfileRepository,
    private val workoutRepository: WorkoutRepository,
    /** Fecha de apertura; null es hoy. Desde el calendario llega el día elegido. */
    initialDate: LocalDate? = null,
) : ViewModel() {

    private val _date = MutableStateFlow(initialDate ?: today())
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

    /** Reinserta una entrada borrada, para el "Deshacer" del snackbar. */
    fun restoreEntry(item: FoodLogItem) {
        viewModelScope.launch {
            repository.restoreLogEntry(_date.value, item)
            reload()
        }
    }

    /**
     * Copia la comida del día anterior. [onResult] recibe cuántas entradas
     * nuevas se copiaron, para poder decir "ayer no apuntaste nada" si son cero.
     */
    fun repeatYesterday(mealType: MealType, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val copied = repository.repeatMealFromPreviousDay(_date.value, mealType)
            if (copied > 0) reload()
            onResult(copied)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteLogEntry(id)
            reload()
        }
    }

    /**
     * Corrige lo apuntado: la cantidad y, si te equivocaste de comida, también a
     * cuál pertenece. [amount] son gramos para un alimento y raciones para una receta.
     */
    fun updateEntry(item: FoodLogItem, mealType: MealType, amount: Double) {
        viewModelScope.launch {
            repository.updateLogEntry(
                id = item.id,
                mealType = mealType,
                grams = if (item.isRecipe) null else amount,
                servings = if (item.isRecipe) amount else null,
            )
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

/**
 * Escanea un código y resuelve el producto: primero en local, luego en Open Food
 * Facts. Lo comparten el catálogo, el añadir a una comida y el añadir ingredientes
 * a una receta, que hacen lo mismo y solo difieren en qué hacen con el alimento.
 */
suspend fun scanAndResolveFood(
    scanner: BarcodeScanner,
    repository: NutritionRepository,
): ScanUiState = when (val scan = scanner.scan()) {
    is ScanResult.Cancelled -> ScanUiState.Idle

    is ScanResult.NotAvailable -> ScanUiState.Failed("Este dispositivo no puede escanear códigos.")

    is ScanResult.Failed -> ScanUiState.Failed(scan.message)

    is ScanResult.Success -> repository.findByBarcode(scan.barcode).fold(
        onSuccess = { food ->
            if (food == null) ScanUiState.NotFound(scan.barcode) else ScanUiState.Found(food)
        },
        onFailure = {
            ScanUiState.Failed(it.message ?: "No se pudo consultar Open Food Facts")
        },
    )
}

/**
 * Busca alimentos y da de alta los que falten, que es lo mismo tanto si vas a
 * apuntar una comida (`FoodSearchScreen`) como si solo estás manteniendo el
 * catálogo (`FoodCatalogScreen`). Las dos pantallas la comparten; cada una usa la
 * parte que necesita.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodSearchViewModel(
    private val repository: NutritionRepository,
    private val barcodeScanner: BarcodeScanner,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _results = MutableStateFlow<List<FoodItem>>(emptyList())
    val results: StateFlow<List<FoodItem>> = _results

    /** Las recetas propias se pueden apuntar igual que un alimento, por raciones. */
    private val _recipes = MutableStateFlow<List<RecipeItem>>(emptyList())
    val recipes: StateFlow<List<RecipeItem>> = _recipes

    private val _counts = MutableStateFlow(FoodCounts(0, 0))
    val counts: StateFlow<FoodCounts> = _counts

    private val _onlineState = MutableStateFlow<OnlineSearchState>(OnlineSearchState.Idle)
    val onlineState: StateFlow<OnlineSearchState> = _onlineState

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState

    /**
     * Segundos que quedan del corte de Open Food Facts, 0 si no hay. Se cuenta
     * atrás en la propia pantalla para que se vea que la espera avanza; si no, la
     * tentación es seguir dándole al botón.
     */
    private val _rateLimitSecondsLeft = MutableStateFlow(0)
    val rateLimitSecondsLeft: StateFlow<Int> = _rateLimitSecondsLeft

    val scannerAvailable: Boolean get() = barcodeScanner.isAvailable()

    /** Tope de la lista: con más alimentos hay que buscar por nombre. */
    val searchLimit = 100

    init {
        refresh()
        loadRecipes()
        watchRateLimit()
    }

    private fun watchRateLimit() {
        viewModelScope.launch {
            repository.rateLimitedUntil.collectLatest {
                while (true) {
                    val left = repository.rateLimitSecondsLeft()
                    _rateLimitSecondsLeft.value = left
                    if (left == 0) break
                    delay(1000)
                }
            }
        }
    }

    /** Relee la lista y el recuento del catálogo. */
    fun refresh() {
        searchLocal()
        viewModelScope.launch { _counts.value = repository.foodCounts() }
    }

    private fun loadRecipes() {
        viewModelScope.launch { _recipes.value = repository.recipes() }
    }

    /** Escanea un código y busca el producto: primero en local, luego en Open Food Facts. */
    fun scanBarcode() {
        if (_scanState.value is ScanUiState.Scanning) return
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            val result = scanAndResolveFood(barcodeScanner, repository)
            // Si el escaneo lo ha traído de Open Food Facts, ya está en el catálogo.
            if (result is ScanUiState.Found) refresh()
            _scanState.value = result
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
        viewModelScope.launch { _results.value = repository.searchLocal(query.value, searchLimit) }
    }

    /** Búsqueda online explícita: consulta Open Food Facts y cachea el resultado. */
    fun searchOnline() {
        val text = query.value.trim()
        if (text.length < 2 || _rateLimitSecondsLeft.value > 0) return
        _onlineState.value = OnlineSearchState.Loading
        viewModelScope.launch {
            repository.searchOnline(text)
                .onSuccess { found ->
                    _onlineState.value = OnlineSearchState.Done(found.size)
                    refresh()
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
        unit: FoodUnit,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val id = repository.createCustomFood(
                name, brand, kcal, protein, carbs, fat, unit = unit,
            )
            refresh()
            onCreated(id)
        }
    }

    /** Corrige la unidad de un alimento desde su ficha. */
    fun changeUnit(foodId: Long, unit: FoodUnit) {
        viewModelScope.launch {
            repository.updateFoodUnit(foodId, unit)
            refresh()
        }
    }

    /** Corrige un alimento creado a mano: antes un error en las kcal era para siempre. */
    fun updateCustomFood(
        id: Long,
        name: String,
        brand: String?,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        unit: FoodUnit,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.updateCustomFood(id, name, brand, kcal, protein, carbs, fat, unit = unit)
            refresh()
            onDone()
        }
    }

    /** false si está en el diario o en una receta y no se puede borrar. */
    fun deleteFood(id: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = repository.deleteFood(id)
            if (deleted) refresh()
            onResult(deleted)
        }
    }

    fun logFood(date: LocalDate, mealType: MealType, foodId: Long, grams: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logFood(date, mealType, foodId, grams)
            onDone()
        }
    }

    fun logRecipe(
        date: LocalDate,
        mealType: MealType,
        recipeId: Long,
        servings: Double,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.logRecipe(date, mealType, recipeId, servings)
            onDone()
        }
    }
}
