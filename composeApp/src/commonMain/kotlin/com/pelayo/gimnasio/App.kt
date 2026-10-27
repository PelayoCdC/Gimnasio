package com.pelayo.gimnasio

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.pelayo.gimnasio.ui.calendar.CalendarScreen
import com.pelayo.gimnasio.ui.home.HomeScreen
import com.pelayo.gimnasio.ui.navigation.ActiveWorkoutRoute
import com.pelayo.gimnasio.ui.navigation.CalendarRoute
import com.pelayo.gimnasio.ui.navigation.ExerciseCatalogRoute
import com.pelayo.gimnasio.ui.navigation.ExerciseDetailRoute
import com.pelayo.gimnasio.ui.navigation.HomeRoute
import com.pelayo.gimnasio.ui.navigation.NutritionRoute
import com.pelayo.gimnasio.ui.navigation.ExercisePickerRoute
import com.pelayo.gimnasio.ui.navigation.ProfileRoute
import com.pelayo.gimnasio.ui.navigation.RoutineDetailRoute
import com.pelayo.gimnasio.ui.navigation.RoutineGeneratorRoute
import com.pelayo.gimnasio.ui.navigation.RoutineListRoute
import com.pelayo.gimnasio.ui.navigation.WorkoutRoute
import com.pelayo.gimnasio.ui.navigation.WorkoutSummaryRoute
import com.pelayo.gimnasio.ui.navigation.bottomTabs
import com.pelayo.gimnasio.domain.model.MealType
import com.pelayo.gimnasio.ui.navigation.FoodCatalogRoute
import com.pelayo.gimnasio.ui.navigation.FoodDiaryRoute
import com.pelayo.gimnasio.ui.navigation.FoodSearchRoute
import com.pelayo.gimnasio.ui.navigation.DietPlanRoute
import com.pelayo.gimnasio.ui.navigation.RecipeEditRoute
import com.pelayo.gimnasio.ui.navigation.RecipeListRoute
import com.pelayo.gimnasio.ui.nutrition.DietPlanScreen
import com.pelayo.gimnasio.ui.nutrition.FoodCatalogScreen
import com.pelayo.gimnasio.ui.nutrition.FoodDiaryScreen
import com.pelayo.gimnasio.ui.nutrition.NutritionScreen
import com.pelayo.gimnasio.ui.nutrition.FoodSearchScreen
import com.pelayo.gimnasio.ui.nutrition.RecipeEditScreen
import com.pelayo.gimnasio.ui.nutrition.RecipeListScreen
import com.pelayo.gimnasio.ui.navigation.SettingsRoute
import com.pelayo.gimnasio.ui.profile.ProfileScreen
import com.pelayo.gimnasio.ui.profile.SettingsScreen
import kotlinx.datetime.LocalDate
import com.pelayo.gimnasio.ui.theme.GimnasioTheme
import com.pelayo.gimnasio.ui.theme.ThemeSettings
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.pelayo.gimnasio.ui.workout.WorkoutScreen
import com.pelayo.gimnasio.ui.workout.active.ActiveWorkoutScreen
import com.pelayo.gimnasio.ui.workout.active.WorkoutSummaryScreen
import com.pelayo.gimnasio.ui.workout.catalog.ExerciseCatalogScreen
import com.pelayo.gimnasio.ui.workout.catalog.ExerciseDetailScreen
import com.pelayo.gimnasio.ui.workout.routine.ExercisePickerScreen
import com.pelayo.gimnasio.ui.workout.routine.RoutineDetailScreen
import com.pelayo.gimnasio.ui.workout.routine.RoutineGeneratorScreen
import com.pelayo.gimnasio.ui.workout.routine.RoutineListScreen
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Cambia de pestaña conservando el estado de cada una y sin apilar destinos
 * repetidos, tanto desde la barra inferior como desde los accesos de Inicio.
 */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun App() {
    val themeSettings = koinInject<ThemeSettings>()
    val themeMode by themeSettings.mode.collectAsState()
    val dynamicColor by themeSettings.dynamicColor.collectAsState()

    GimnasioTheme(mode = themeMode, dynamicColor = dynamicColor) {
        val httpClient = koinInject<HttpClient>()
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient)) }
                .build()
        }
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        // Durante el entreno la barra de pestañas estorba y es peligrosa: un toque
        // te saca de la sesión sin avisar, y su hueco lo ocupa el temporizador.
        val hideBottomBar = currentDestination?.hasRoute(ActiveWorkoutRoute::class) == true

        Scaffold(
            bottomBar = {
                if (!hideBottomBar) {
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(tab.route::class)
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(innerPadding),
                // Deslizamiento sutil en vez del corte seco por defecto; el pop
                // invierte la dirección para que "volver" se sienta como volver.
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()
                },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()
                },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onStartWorkout = { sessionId ->
                            navController.navigate(ActiveWorkoutRoute(sessionId))
                        },
                        onOpenRoutines = { navController.navigate(RoutineListRoute) },
                        onOpenCatalog = { navController.navigate(ExerciseCatalogRoute) },
                        onOpenNutrition = { navController.navigateToTab(NutritionRoute) },
                        onOpenProfile = { navController.navigateToTab(ProfileRoute) },
                    )
                }
                composable<WorkoutRoute> {
                    WorkoutScreen(
                        onOpenCatalog = { navController.navigate(ExerciseCatalogRoute) },
                        onOpenRoutines = { navController.navigate(RoutineListRoute) },
                    )
                }
                composable<CalendarRoute> {
                    CalendarScreen(
                        onSessionClick = { id -> navController.navigate(WorkoutSummaryRoute(id)) },
                        onResumeSession = { id -> navController.navigate(ActiveWorkoutRoute(id)) },
                        onOpenDiary = { date ->
                            navController.navigate(FoodDiaryRoute(date.toString()))
                        },
                    )
                }
                composable<NutritionRoute> {
                    NutritionScreen(
                        onOpenDiary = { navController.navigate(FoodDiaryRoute()) },
                        onOpenCatalog = { navController.navigate(FoodCatalogRoute) },
                        onOpenRecipes = { navController.navigate(RecipeListRoute) },
                        onOpenDietPlans = { navController.navigate(DietPlanRoute) },
                    )
                }
                composable<FoodDiaryRoute> { entry ->
                    val route = entry.toRoute<FoodDiaryRoute>()
                    FoodDiaryScreen(
                        onAddFood = { date, meal ->
                            navController.navigate(FoodSearchRoute(date.toString(), meal.name))
                        },
                        onBack = { navController.popBackStack() },
                        initialDate = route.dateIso?.let { LocalDate.parse(it) },
                    )
                }
                composable<DietPlanRoute> {
                    DietPlanScreen(onBack = { navController.popBackStack() })
                }
                composable<FoodCatalogRoute> {
                    FoodCatalogScreen(onBack = { navController.popBackStack() })
                }
                composable<RecipeListRoute> {
                    RecipeListScreen(
                        onRecipeClick = { id -> navController.navigate(RecipeEditRoute(id)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<RecipeEditRoute> { entry ->
                    val route = entry.toRoute<RecipeEditRoute>()
                    RecipeEditScreen(
                        recipeId = route.recipeId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<FoodSearchRoute> { entry ->
                    val route = entry.toRoute<FoodSearchRoute>()
                    FoodSearchScreen(
                        date = LocalDate.parse(route.dateIso),
                        mealType = MealType.valueOf(route.mealType),
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<ProfileRoute> {
                    ProfileScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
                }
                composable<SettingsRoute> {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                composable<ExerciseCatalogRoute> {
                    ExerciseCatalogScreen(
                        onExerciseClick = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<ExerciseDetailRoute> { entry ->
                    val route = entry.toRoute<ExerciseDetailRoute>()
                    ExerciseDetailScreen(
                        exerciseId = route.exerciseId,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<RoutineListRoute> {
                    RoutineListScreen(
                        onRoutineClick = { id -> navController.navigate(RoutineDetailRoute(id)) },
                        onGenerateRoutine = { navController.navigate(RoutineGeneratorRoute) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<RoutineGeneratorRoute> {
                    RoutineGeneratorScreen(
                        onSaved = { id ->
                            navController.navigate(RoutineDetailRoute(id)) {
                                popUpTo(RoutineGeneratorRoute) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onOpenCatalog = { navController.navigate(ExerciseCatalogRoute) },
                    )
                }
                composable<RoutineDetailRoute> { entry ->
                    val route = entry.toRoute<RoutineDetailRoute>()
                    RoutineDetailScreen(
                        routineId = route.routineId,
                        onBack = { navController.popBackStack() },
                        onAddExercise = { dayId ->
                            navController.navigate(ExercisePickerRoute(dayId, route.routineId))
                        },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                        onStartWorkout = { sessionId ->
                            navController.navigate(ActiveWorkoutRoute(sessionId))
                        },
                    )
                }
                composable<ActiveWorkoutRoute> { entry ->
                    val route = entry.toRoute<ActiveWorkoutRoute>()
                    ActiveWorkoutScreen(
                        sessionId = route.sessionId,
                        onFinished = { sessionId ->
                            navController.navigate(WorkoutSummaryRoute(sessionId)) {
                                popUpTo(ActiveWorkoutRoute(sessionId)) { inclusive = true }
                            }
                        },
                        onCancel = { navController.popBackStack() },
                        onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                    )
                }
                composable<WorkoutSummaryRoute> { entry ->
                    val route = entry.toRoute<WorkoutSummaryRoute>()
                    WorkoutSummaryScreen(
                        sessionId = route.sessionId,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<ExercisePickerRoute> { entry ->
                    val route = entry.toRoute<ExercisePickerRoute>()
                    ExercisePickerScreen(
                        dayId = route.dayId,
                        routineId = route.routineId,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
