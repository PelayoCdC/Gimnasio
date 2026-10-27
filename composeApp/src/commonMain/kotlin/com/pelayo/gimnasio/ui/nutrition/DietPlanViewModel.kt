package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.domain.model.DietPlanItem
import com.pelayo.gimnasio.domain.model.DietPlanMealItem
import com.pelayo.gimnasio.domain.model.FoodItem
import com.pelayo.gimnasio.domain.model.Macros
import com.pelayo.gimnasio.domain.model.MacroSplit
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.domain.model.RecipeItem
import com.pelayo.gimnasio.domain.model.RoutineGoal
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.isoDayNumber

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

    /** Día de la semana que se está editando (ISO, lunes = 1). Se abre por el de hoy. */
    private val _selectedDay = MutableStateFlow(today().dayOfWeek.isoDayNumber)
    val selectedDay: StateFlow<Int> = _selectedDay

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
        val metrics = profileRepository.currentMetrics()
        // Sin TDEE (falta la fecha de nacimiento) no hay nada que sugerir.
        val tdee = metrics?.tdee ?: run {
            _suggestion.value = null
            return
        }
        // Objetivo por defecto: mantenimiento con reparto de hipertrofia.
        val goal = RoutineGoal.HIPERTROFIA
        _suggestion.value = SuggestedTargets(
            maintenanceKcal = tdee,
            macros = MacroSplit.split(tdee, metrics.weightKg, goal),
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

    /** Nombre y objetivos de un plan ya creado; el menú no se toca. */
    fun updatePlan(
        id: Long,
        name: String,
        targetKcal: Double?,
        targetProteinG: Double?,
        targetCarbsG: Double?,
        targetFatG: Double?,
    ) {
        viewModelScope.launch {
            repository.updateDietPlan(id, name, targetKcal, targetProteinG, targetCarbsG, targetFatG)
            reload()
        }
    }

    fun selectDay(dayOfWeek: Int) {
        _selectedDay.value = dayOfWeek
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
            repository.addFoodToDietPlan(planId, _selectedDay.value, mealType, foodId, grams)
            _selected.value = repository.dietPlan(planId)
        }
    }

    fun addRecipe(mealType: MealType, recipeId: Long, servings: Double) {
        val planId = _selected.value?.id ?: return
        viewModelScope.launch {
            repository.addRecipeToDietPlan(planId, _selectedDay.value, mealType, recipeId, servings)
            _selected.value = repository.dietPlan(planId)
        }
    }

    /** [amount] son gramos para un alimento y raciones para una receta. */
    fun updateMeal(meal: DietPlanMealItem, mealType: MealType, amount: Double) {
        val planId = _selected.value?.id ?: return
        val isRecipe = meal.recipeId != null
        viewModelScope.launch {
            repository.updateDietPlanMeal(
                id = meal.id,
                dayOfWeek = meal.dayOfWeek,
                mealType = mealType,
                grams = if (isRecipe) null else amount,
                servings = if (isRecipe) amount else null,
            )
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

    /** Copia el menú del día que se está viendo a otro, reemplazando lo que hubiera. */
    fun copyDayTo(targetDay: Int) {
        val planId = _selected.value?.id ?: return
        val fromDay = _selectedDay.value
        viewModelScope.launch {
            val copied = repository.copyDietPlanDay(planId, fromDay, targetDay)
            _selected.value = repository.dietPlan(planId)
            _message.value = if (copied == 0) {
                "El ${weekdayNamesEs[fromDay - 1].lowercase()} no tiene nada que copiar."
            } else {
                "Copiado el menú del ${weekdayNamesEs[fromDay - 1].lowercase()} al " +
                    "${weekdayNamesEs[targetDay - 1].lowercase()}."
            }
        }
    }

    /** Vuelca en el diario de hoy el menú que el plan tiene para hoy. */
    fun applyToToday() {
        val planId = _selected.value?.id ?: return
        viewModelScope.launch {
            val today = today()
            val added = repository.applyDietPlanToDay(planId, today)
            val dayName = weekdayNamesEs[today.dayOfWeek.isoDayNumber - 1].lowercase()
            _message.value = if (added == 0) {
                "El plan no tiene nada para el $dayName."
            } else {
                "Añadidos $added alimentos al diario de hoy."
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
