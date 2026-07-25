package com.pelayo.gimnasio.di

import com.pelayo.gimnasio.platform.DatabaseDriverFactory
import com.pelayo.gimnasio.platform.FileTransfer
import com.pelayo.gimnasio.platform.RestTimerNotifier
import com.pelayo.gimnasio.ui.workout.active.ActiveWorkoutViewModel
import com.pelayo.gimnasio.ui.workout.active.WorkoutSummaryViewModel
import com.pelayo.gimnasio.ui.workout.catalog.ExerciseDetailViewModel
import com.pelayo.gimnasio.ui.workout.routine.ExercisePickerViewModel
import com.pelayo.gimnasio.ui.workout.routine.RoutineDetailViewModel
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Comprueba que todas las dependencias declaradas en el grafo de Koin se pueden
 * resolver. Sin esto, un binding que falte solo revienta al abrir la pantalla.
 */
class AppModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun el_grafo_de_koin_es_resoluble() {
        appModule.verify(
            extraTypes = listOf(
                // Los aporta el módulo de cada plataforma.
                DatabaseDriverFactory::class,
                RestTimerNotifier::class,
                FileTransfer::class,
                com.pelayo.gimnasio.platform.BarcodeScanner::class,
                com.pelayo.gimnasio.platform.ReminderScheduler::class,
                // El motor de Ktor lo resuelve la propia librería al construir el HttpClient.
                HttpClientEngine::class,
            ),
            injections = injectedParameters(
                definition<ExerciseDetailViewModel>(Long::class),
                definition<RoutineDetailViewModel>(Long::class),
                definition<ExercisePickerViewModel>(Long::class, Long::class),
                definition<ActiveWorkoutViewModel>(Long::class),
                definition<WorkoutSummaryViewModel>(Long::class),
                definition<com.pelayo.gimnasio.ui.nutrition.RecipeEditViewModel>(Long::class),
            ),
        )
    }
}
