package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.domain.model.DietPlanItem
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MacroSplit
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Sugerencia de objetivos calculada a partir del TDEE y el peso del usuario. */
data class SuggestedTargets(
    val maintenanceKcal: Double,
    val macros: Macros,
    val goal: RoutineGoal,
)

class DietPlanViewModel(
    private val repository: NutritionRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _plans = MutableStateFlow<List<DietPlanItem>>(emptyList())
    val plans: StateFlow<List<DietPlanItem>> = _plans

    private val _selected = MutableStateFlow<DietPlanItem?>(null)
    val selected: StateFlow<DietPlanItem?> = _selected

    private val _suggestion = MutableStateFlow<SuggestedTargets?>(null)
    val suggestion: StateFlow<SuggestedTargets?> = _suggestion

    private val _foods = MutableStateFlow<List<FoodItem>>(emptyList())
    val foods: StateFlow<List<FoodItem>> = _foods

    private val _recipes = MutableStateFlow<List<RecipeItem>>(emptyList())
    val recipes: StateFlow<List<RecipeItem>> = _recipes

    val foodQuery = MutableStateFlow("")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _plans.value = repository.dietPlans()
            _selected.value = _selected.value?.let { current ->
                _plans.value.firstOrNull { it.id == current.id }
            } ?: _plans.value.firstOrNull { it.isActive } ?: _plans.value.firstOrNull()
            _recipes.value = repository.recipes()
            searchFoods()
            computeSuggestion()
        }
    }

    private suspend fun computeSuggestion() {
        val metrics = profileRepository.currentMetrics() ?: run {
            _suggestion.value = null
            return
        }
        // Objetivo por defecto: mantenimiento con reparto de hipertrofia.
        val goal = RoutineGoal.HIPERTROFIA
        _suggestion.value = SuggestedTargets(
            maintenanceKcal = metrics.tdee,
            macros = MacroSplit.split(metrics.tdee, metrics.weightKg, goal),
            goal = goal,
        )
    }

    /** Recalcula la sugerencia para un objetivo concreto (déficit o superávit). */
    fun suggestionFor(goal: RoutineGoal): SuggestedTargets? {
        val current = _suggestion.value ?: return null
        val kcal = when (goal) {
            RoutineGoal.PERDIDA_GRASA -> current.maintenanceKcal - 400
            RoutineGoal.HIPERTROFIA -> current.maintenanceKcal + 250
            else -> current.maintenanceKcal
        }
        val weight = current.macros.protein / MacroSplit.proteinPerKg(current.goal)
        return SuggestedTargets(current.maintenanceKcal, MacroSplit.split(kcal, weight, goal), goal)
    }

    fun selectPlan(id: Long) {
        viewModelScope.launch { _selected.value = repository.dietPlan(id) }
    }

    fun createPlan(name: String, targets: Macros?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createDietPlan(
                name = name.trim().ifBlank { "Mi dieta" },
                targetKcal = targets?.kcal,
                targetProteinG = targets?.protein,
                targetCarbsG = targets?.carbs,
                targetFatG = targets?.fat,
            )
            reload()
            _selected.value = repository.dietPlan(id)
            onCreated(id)
        }
    }

    fun deletePlan(id: Long) {
        viewModelScope.launch {
            repository.deleteDietPlan(id)
            if (_selected.value?.id == id) _selected.value = null
            reload()
        }
    }

    fun setActive(id: Long) {
        viewModelScope.launch {
            repository.setActiveDietPlan(id)
            reload()
        }
    }

    fun onFoodQueryChange(value: String) {
        foodQuery.value = value
        searchFoods()
    }

    private fun searchFoods() {
        viewModelScope.launch { _foods.value = repository.searchLocal(foodQuery.value, limit = 30) }
    }

    fun addFood(mealType: MealType, foodId: Long, grams: Double) {
        val planId = _selected.value?.id ?: return
        viewModelScope.launch {
            repository.addFoodToDietPlan(planId, mealType, foodId, grams)
            _selected.value = repository.dietPlan(planId)
        }
    }

    fun addRecipe(mealType: MealType, recipeId: Long, servings: Double) {
        val planId = _selected.value?.id ?: return
        viewModelScope.launch {
            repository.addRecipeToDietPlan(planId, mealType, recipeId, servings)
            _selected.value = repository.dietPlan(planId)
        }
    }

    fun removeMeal(id: Long) {
        val planId = _selected.value?.id ?: return
        viewModelScope.launch {
            repository.removeDietPlanMeal(id)
            _selected.value = repository.dietPlan(planId)
        }
    }

    /** Vuelca el menú del plan en el diario de hoy. */
    fun applyToToday() {
        val planId = _selected.value?.id ?: return
        viewModelScope.launch {
            val added = repository.applyDietPlanToDay(planId, today())
            _message.value = if (added == 0) {
                "Este plan no tiene alimentos todavía."
            } else {
                "Añadidos $added alimentos al diario de hoy."
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
