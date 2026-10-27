package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.FoodUnit
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.domain.model.RecipeUsage
import com.pelayo.gimnasio.platform.BarcodeScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** La receta que se quiere borrar, con el aviso de dónde está usada. */
data class RecipeDeletion(val recipe: RecipeItem, val usage: RecipeUsage)

class RecipeListViewModel(
    private val repository: NutritionRepository,
) : ViewModel() {

    private val _recipes = MutableStateFlow<List<RecipeItem>>(emptyList())
    val recipes: StateFlow<List<RecipeItem>> = _recipes

    private val _pendingDeletion = MutableStateFlow<RecipeDeletion?>(null)
    val pendingDeletion: StateFlow<RecipeDeletion?> = _pendingDeletion

    init {
        reload()
    }

    /** Prepara el borrado: antes de preguntar hay que saber dónde está usada. */
    fun askToDelete(recipe: RecipeItem) {
        viewModelScope.launch {
            _pendingDeletion.value = RecipeDeletion(recipe, repository.recipeUsage(recipe.id))
        }
    }

    fun cancelDeletion() {
        _pendingDeletion.value = null
    }

    fun confirmDeletion() {
        val pending = _pendingDeletion.value ?: return
        _pendingDeletion.value = null
        deleteRecipe(pending.recipe.id)
    }

    fun reload() {
        viewModelScope.launch { _recipes.value = repository.recipes() }
    }

    fun createRecipe(name: String, servings: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createRecipe(name.trim().ifBlank { "Nueva receta" }, servings, null)
            reload()
            onCreated(id)
        }
    }

    fun deleteRecipe(id: Long) {
        viewModelScope.launch {
            repository.deleteRecipe(id)
            reload()
        }
    }
}

class RecipeEditViewModel(
    private val recipeId: Long,
    private val repository: NutritionRepository,
    private val barcodeScanner: BarcodeScanner,
) : ViewModel() {

    private val _recipe = MutableStateFlow<RecipeItem?>(null)
    val recipe: StateFlow<RecipeItem?> = _recipe

    private val _foods = MutableStateFlow<List<FoodItem>>(emptyList())
    val foods: StateFlow<List<FoodItem>> = _foods

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState

    val scannerAvailable: Boolean get() = barcodeScanner.isAvailable()

    val foodQuery = MutableStateFlow("")

    init {
        reload()
        searchFoods()
    }

    /**
     * Escanea el código de un ingrediente. Los alimentos de una receta salen de la
     * despensa igual que los del diario, así que aquí también compensa el escáner.
     */
    fun scanBarcode() {
        if (_scanState.value is ScanUiState.Scanning) return
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            val result = scanAndResolveFood(barcodeScanner, repository)
            if (result is ScanUiState.Found) searchFoods()
            _scanState.value = result
        }
    }

    fun dismissScan() {
        _scanState.value = ScanUiState.Idle
    }

    fun createCustomFood(
        name: String,
        brand: String?,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        unit: FoodUnit,
        onCreated: (FoodItem) -> Unit,
    ) {
        viewModelScope.launch {
            val id = repository.createCustomFood(
                name, brand, kcal, protein, carbs, fat, unit = unit,
            )
            searchFoods()
            repository.food(id)?.let(onCreated)
        }
    }

    fun reload() {
        viewModelScope.launch { _recipe.value = repository.recipe(recipeId) }
    }

    fun onFoodQueryChange(value: String) {
        foodQuery.value = value
        searchFoods()
    }

    private fun searchFoods() {
        viewModelScope.launch { _foods.value = repository.searchLocal(foodQuery.value, limit = 30) }
    }

    fun addIngredient(foodId: Long, grams: Double) {
        viewModelScope.launch {
            repository.addIngredient(recipeId, foodId, grams)
            reload()
        }
    }

    fun updateIngredient(id: Long, grams: Double) {
        viewModelScope.launch {
            repository.updateIngredient(id, grams)
            reload()
        }
    }

    fun removeIngredient(id: Long) {
        viewModelScope.launch {
            repository.removeIngredient(id)
            reload()
        }
    }

    fun updateRecipe(name: String, servings: Int) {
        viewModelScope.launch {
            repository.updateRecipe(recipeId, name, servings, _recipe.value?.notes)
            reload()
        }
    }
}
