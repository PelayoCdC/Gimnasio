package com.pelayo.gimnasio.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import com.pelayo.gimnasio.resources.Res
import com.pelayo.gimnasio.resources.tab_calendar
import com.pelayo.gimnasio.resources.tab_home
import com.pelayo.gimnasio.resources.tab_nutrition
import com.pelayo.gimnasio.resources.tab_profile
import com.pelayo.gimnasio.resources.tab_workout
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable object HomeRoute
@Serializable object WorkoutRoute
@Serializable object CalendarRoute
@Serializable object NutritionRoute
@Serializable object ProfileRoute

@Serializable object ExerciseCatalogRoute
@Serializable data class ExerciseDetailRoute(val exerciseId: Long)

@Serializable object RoutineListRoute
@Serializable object RoutineGeneratorRoute
@Serializable data class RoutineDetailRoute(val routineId: Long)
@Serializable data class ExercisePickerRoute(val dayId: Long, val routineId: Long)

@Serializable data class ActiveWorkoutRoute(val sessionId: Long)
@Serializable data class WorkoutSummaryRoute(val sessionId: Long)

/** La fecha viaja como ISO-8601 porque las rutas solo admiten tipos primitivos. */
@Serializable data class FoodSearchRoute(val dateIso: String, val mealType: String)

@Serializable object SettingsRoute

@Serializable object DietPlanRoute
@Serializable object RecipeListRoute
@Serializable data class RecipeEditRoute(val recipeId: Long)

data class BottomTab(
    val route: Any,
    val label: StringResource,
    val icon: ImageVector,
)

val bottomTabs = listOf(
    BottomTab(HomeRoute, Res.string.tab_home, Icons.Filled.Home),
    BottomTab(WorkoutRoute, Res.string.tab_workout, Icons.Filled.FitnessCenter),
    BottomTab(CalendarRoute, Res.string.tab_calendar, Icons.Filled.CalendarMonth),
    BottomTab(NutritionRoute, Res.string.tab_nutrition, Icons.Filled.Restaurant),
    BottomTab(ProfileRoute, Res.string.tab_profile, Icons.Filled.Person),
)
