package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class RecipeListViewModel(
    private val repository: NutritionRepository,
) : ViewModel() {

    private val _recipes = MutableStateFlow<List<RecipeItem>>(emptyList())
    val recipes: StateFlow<List<RecipeItem>> = _recipes

    init {
        reload()
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

    fun logRecipe(date: LocalDate, meal: MealType, recipeId: Long, servings: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logRecipe(date, meal, recipeId, servings)
            onDone()
        }
    }
}

class RecipeEditViewModel(
    private val recipeId: Long,
    private val repository: NutritionRepository,
) : ViewModel() {

    private val _recipe = MutableStateFlow<RecipeItem?>(null)
    val recipe: StateFlow<RecipeItem?> = _recipe

    private val _foods = MutableStateFlow<List<FoodItem>>(emptyList())
    val foods: StateFlow<List<FoodItem>> = _foods

    val foodQuery = MutableStateFlow("")

    init {
        reload()
        searchFoods()
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
