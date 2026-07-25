package com.pelayo.gimnasio.di

import com.pelayo.gimnasio.data.backup.BackupService
import com.pelayo.gimnasio.data.db.DatabaseSeeder
import com.pelayo.gimnasio.data.remote.HttpClientFactory
import com.pelayo.gimnasio.data.remote.OpenFoodFactsApi
import com.pelayo.gimnasio.data.remote.WgerApi
import com.pelayo.gimnasio.data.repository.NutritionRepository
import com.pelayo.gimnasio.data.repository.CatalogSyncService
import com.pelayo.gimnasio.data.repository.ExerciseRepository
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.WorkoutRepository
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.platform.DatabaseDriverFactory
import com.pelayo.gimnasio.ui.calendar.CalendarViewModel
import com.pelayo.gimnasio.ui.home.HomeViewModel
import com.pelayo.gimnasio.ui.nutrition.DietPlanViewModel
import com.pelayo.gimnasio.ui.nutrition.FoodDiaryViewModel
import com.pelayo.gimnasio.ui.nutrition.FoodSearchViewModel
import com.pelayo.gimnasio.ui.nutrition.RecipeEditViewModel
import com.pelayo.gimnasio.ui.nutrition.RecipeListViewModel
import com.pelayo.gimnasio.ui.profile.ProfileViewModel
import com.pelayo.gimnasio.ui.profile.SettingsViewModel
import com.pelayo.gimnasio.ui.workout.active.ActiveWorkoutViewModel
import com.pelayo.gimnasio.ui.workout.active.WorkoutSummaryViewModel
import com.pelayo.gimnasio.ui.workout.catalog.CatalogViewModel
import com.pelayo.gimnasio.ui.workout.catalog.ExerciseDetailViewModel
import com.pelayo.gimnasio.ui.workout.routine.ExercisePickerViewModel
import com.pelayo.gimnasio.ui.workout.routine.RoutineDetailViewModel
import com.pelayo.gimnasio.ui.workout.routine.RoutineGeneratorViewModel
import com.pelayo.gimnasio.ui.workout.routine.RoutineListViewModel
import com.russhwolf.settings.Settings
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule = module {
    single {
        GimnasioDb(get<DatabaseDriverFactory>().createDriver()).also { DatabaseSeeder.seed(it) }
    }
    single { Settings() }
    single { HttpClientFactory.create() }
    single { WgerApi(get()) }
    single { ExerciseRepository(get()) }
    single { RoutineRepository(get()) }
    single { WorkoutRepository(get()) }
    single { ProfileRepository(get()) }
    single { OpenFoodFactsApi(get()) }
    single { NutritionRepository(get(), get()) }
    single { CatalogSyncService(get(), get(), get()) }
    single { BackupService(get()) }

    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { CatalogViewModel(get(), get()) }
    viewModel { (exerciseId: Long) -> ExerciseDetailViewModel(exerciseId, get(), get()) }
    viewModel { RoutineListViewModel(get()) }
    viewModel { RoutineGeneratorViewModel(get(), get()) }
    viewModel { (routineId: Long) -> RoutineDetailViewModel(routineId, get(), get()) }
    viewModel { (dayId: Long, routineId: Long) ->
        ExercisePickerViewModel(dayId, routineId, get(), get())
    }
    viewModel { (sessionId: Long) -> ActiveWorkoutViewModel(sessionId, get(), get(), get()) }
    viewModel { (sessionId: Long) -> WorkoutSummaryViewModel(sessionId, get()) }
    viewModel { CalendarViewModel(get()) }
    viewModel { ProfileViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { FoodDiaryViewModel(get(), get(), get()) }
    viewModel { FoodSearchViewModel(get(), get()) }
    viewModel { DietPlanViewModel(get(), get()) }
    viewModel { RecipeListViewModel(get()) }
    viewModel { (recipeId: Long) -> RecipeEditViewModel(recipeId, get()) }
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, appModule)
    }
}
