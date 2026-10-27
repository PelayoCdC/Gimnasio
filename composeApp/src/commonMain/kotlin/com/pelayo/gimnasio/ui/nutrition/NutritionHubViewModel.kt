package com.pelayo.gimnasio.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Un día del gráfico de calorías. Los días sin nada apuntado valen 0. */
data class DailyCalories(val date: LocalDate, val kcal: Double)

class NutritionHubViewModel(
    private val repository: NutritionRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /** Dos semanas: entran en la pantalla y son suficientes para ver la tendencia. */
    private val days = 14

    private val _history = MutableStateFlow<List<DailyCalories>>(emptyList())
    val history: StateFlow<List<DailyCalories>> = _history

    /** Gasto diario estimado, para saber si se come por encima o por debajo. */
    private val _tdee = MutableStateFlow<Double?>(null)
    val tdee: StateFlow<Double?> = _tdee

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val end = today()
            val start = end.minus(DatePeriod(days = days - 1))
            val byDate = repository.dailyCalories(start, end)
            // Los huecos se rellenan con cero: un día sin apuntar nada es parte de
            // la historia, y saltárselo dibujaría una línea que engaña.
            _history.value = (0 until days).map { offset ->
                val date = start.plus(DatePeriod(days = offset))
                DailyCalories(date, byDate[date] ?: 0.0)
            }
            _tdee.value = profileRepository.currentMetrics()?.tdee
        }
    }

    /**
     * Media de los días con algo apuntado; los vacíos no cuentan. Deriva del
     * historial como estado observable: como getter suelto solo se refrescaba de
     * rebote cuando otra cosa recomponía la pantalla.
     */
    val average: StateFlow<Double> = _history
        .map { history ->
            history.filter { it.kcal > 0 }.map { it.kcal }.average().takeIf { !it.isNaN() } ?: 0.0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
}
