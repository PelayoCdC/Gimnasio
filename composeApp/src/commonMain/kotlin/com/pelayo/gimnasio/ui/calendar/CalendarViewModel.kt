package com.pelayo.gimnasio.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.domain.model.CalendarSession
import com.pelayo.gimnasio.util.MonthGrid
import com.pelayo.gimnasio.util.buildMonthGrid
import com.pelayo.gimnasio.util.monthNumber
import com.pelayo.gimnasio.util.monthRange
import com.pelayo.gimnasio.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class CalendarViewModel(
    private val repository: WorkoutRepository,
    private val nutritionRepository: NutritionRepository,
) : ViewModel() {

    private val currentDay = today()

    private val _grid = MutableStateFlow(buildMonthGrid(currentDay.year, currentDay.monthNumber))
    val grid: StateFlow<MonthGrid> = _grid

    private val _sessions = MutableStateFlow<Map<LocalDate, List<CalendarSession>>>(emptyMap())
    val sessions: StateFlow<Map<LocalDate, List<CalendarSession>>> = _sessions

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    /** Calorías apuntadas cada día del mes: en el calendario también se come. */
    private val _calories = MutableStateFlow<Map<LocalDate, Double>>(emptyMap())
    val calories: StateFlow<Map<LocalDate, Double>> = _calories

    val todayDate: LocalDate get() = currentDay

    init {
        loadMonth()
    }

    fun showPreviousMonth() = shiftMonth(-1)

    fun showNextMonth() = shiftMonth(1)

    /** Vuelve al mes actual con hoy seleccionado, tras navegar meses con las flechas. */
    fun showToday() {
        _grid.value = buildMonthGrid(currentDay.year, currentDay.monthNumber)
        _selectedDate.value = currentDay
        loadMonth()
    }

    private fun shiftMonth(delta: Int) {
        val current = _grid.value
        val total = current.year * 12 + (current.month - 1) + delta
        _grid.value = buildMonthGrid(total / 12, total % 12 + 1)
        _selectedDate.value = null
        loadMonth()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    fun reload() = loadMonth()

    private fun loadMonth() {
        val grid = _grid.value
        viewModelScope.launch {
            val (from, to) = monthRange(grid.year, grid.month)
            _sessions.value = repository.sessionsByDate(from, to)
            _calories.value = nutritionRepository.dailyCalories(from, to)
        }
    }
}
