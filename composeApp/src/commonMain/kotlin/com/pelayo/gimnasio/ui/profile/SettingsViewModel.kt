package com.pelayo.gimnasio.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.backup.BackupService
import com.pelayo.gimnasio.data.backup.ImportSummary
import com.pelayo.gimnasio.data.repository.CatalogSyncService
import com.pelayo.gimnasio.data.repository.RoutineRepository
import com.pelayo.gimnasio.data.repository.SyncProgress
import com.pelayo.gimnasio.domain.model.weekdayNamesEs
import com.pelayo.gimnasio.platform.FileTransfer
import com.pelayo.gimnasio.platform.ReminderScheduler
import com.pelayo.gimnasio.platform.WorkoutReminder
import com.pelayo.gimnasio.util.today
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val KEY_WORKOUT_REMINDERS = "recordatoriosEntreno"
private const val KEY_WEIGH_IN_REMINDER = "recordatorioPesaje"
private const val KEY_REMINDER_HOUR = "horaRecordatorio"

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class Message(val text: String, val isError: Boolean = false) : BackupUiState
    data class Imported(val summary: ImportSummary) : BackupUiState
}

sealed interface CatalogSyncUiState {
    data object Idle : CatalogSyncUiState
    data class Running(val done: Int, val total: Int) : CatalogSyncUiState
    data class Finished(val imported: Int) : CatalogSyncUiState
    data class Failed(val message: String) : CatalogSyncUiState
}

data class RemindersState(
    val supported: Boolean = false,
    val workoutRemindersOn: Boolean = false,
    val weighInReminderOn: Boolean = false,
    val hour: Int = 18,
    /** Días de la rutina activa que tienen día de la semana asignado. */
    val scheduledDays: List<String> = emptyList(),
)

class SettingsViewModel(
    private val backupService: BackupService,
    private val fileTransfer: FileTransfer,
    private val catalogSyncService: CatalogSyncService,
    private val routineRepository: RoutineRepository,
    private val reminderScheduler: ReminderScheduler,
    private val settings: Settings,
) : ViewModel() {

    private val _reminders = MutableStateFlow(RemindersState())
    val reminders: StateFlow<RemindersState> = _reminders

    init {
        loadReminders()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            val routine = routineRepository.activeRoutineDetail()
            _reminders.value = RemindersState(
                supported = reminderScheduler.isSupported(),
                workoutRemindersOn = settings.getBoolean(KEY_WORKOUT_REMINDERS, false),
                weighInReminderOn = settings.getBoolean(KEY_WEIGH_IN_REMINDER, false),
                hour = settings.getInt(KEY_REMINDER_HOUR, 18),
                scheduledDays = routine?.days.orEmpty()
                    .filter { it.weekday != null }
                    .map { "${it.name} (${weekdayNamesEs[it.weekday!! - 1].lowercase()})" },
            )
        }
    }

    fun setWorkoutReminders(enabled: Boolean) {
        settings.putBoolean(KEY_WORKOUT_REMINDERS, enabled)
        viewModelScope.launch {
            if (enabled) {
                val routine = routineRepository.activeRoutineDetail()
                val hour = _reminders.value.hour
                reminderScheduler.scheduleWorkoutReminders(
                    routine?.days.orEmpty().mapNotNull { day ->
                        day.weekday?.let {
                            WorkoutReminder(weekday = it, hour = hour, minute = 0, dayName = day.name)
                        }
                    },
                )
            } else {
                reminderScheduler.cancelWorkoutReminders()
            }
            loadReminders()
        }
    }

    fun setWeighInReminder(enabled: Boolean) {
        settings.putBoolean(KEY_WEIGH_IN_REMINDER, enabled)
        if (enabled) {
            // Domingo por la mañana: el momento clásico para pesarse.
            reminderScheduler.scheduleWeighInReminder(weekday = 7, hour = 9, minute = 0)
        } else {
            reminderScheduler.cancelWeighInReminder()
        }
        loadReminders()
    }

    fun setReminderHour(hour: Int) {
        settings.putInt(KEY_REMINDER_HOUR, hour.coerceIn(0, 23))
        loadReminders()
        // Reprograma con la hora nueva si estaban activos.
        if (settings.getBoolean(KEY_WORKOUT_REMINDERS, false)) setWorkoutReminders(true)
    }

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState

    private val _syncState = MutableStateFlow<CatalogSyncUiState>(CatalogSyncUiState.Idle)
    val syncState: StateFlow<CatalogSyncUiState> = _syncState

    val lastCatalogSync: String? get() = catalogSyncService.lastSyncedAt

    fun exportBackup() {
        runFileTask("copia-gimnasio-${today()}.json", "application/json") {
            backupService.exportToJson()
        }
    }

    fun exportSetsCsv() {
        runFileTask("entrenamientos-${today()}.csv", "text/csv") {
            backupService.exportSetsCsv()
        }
    }

    fun exportMeasurementsCsv() {
        runFileTask("medidas-${today()}.csv", "text/csv") {
            backupService.exportMeasurementsCsv()
        }
    }

    private fun runFileTask(fileName: String, mimeType: String, produce: suspend () -> String) {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val result = runCatching {
                fileTransfer.saveText(fileName, mimeType, produce())
            }
            _backupState.value = when {
                result.isFailure -> BackupUiState.Message(
                    result.exceptionOrNull()?.message ?: "No se pudo generar el fichero",
                    isError = true,
                )
                result.getOrDefault(false) -> BackupUiState.Message("Guardado como $fileName")
                // Cancelar el diálogo no es un error, así que no se muestra en rojo.
                else -> BackupUiState.Idle
            }
        }
    }

    fun importBackup() {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            val content = fileTransfer.readText("application/json")
            if (content == null) {
                _backupState.value = BackupUiState.Idle
                return@launch
            }
            backupService.importFromJson(content)
                .onSuccess { _backupState.value = BackupUiState.Imported(it) }
                .onFailure {
                    _backupState.value = BackupUiState.Message(
                        it.message ?: "El fichero no es una copia válida",
                        isError = true,
                    )
                }
        }
    }

    fun dismissMessage() {
        _backupState.value = BackupUiState.Idle
    }

    fun syncCatalog() {
        if (_syncState.value is CatalogSyncUiState.Running) return
        viewModelScope.launch {
            catalogSyncService.sync().collect { progress ->
                _syncState.value = when (progress) {
                    is SyncProgress.Running -> CatalogSyncUiState.Running(progress.done, progress.total)
                    is SyncProgress.Finished -> CatalogSyncUiState.Finished(progress.imported)
                    is SyncProgress.Failed -> CatalogSyncUiState.Failed(progress.message)
                }
            }
        }
    }
}
