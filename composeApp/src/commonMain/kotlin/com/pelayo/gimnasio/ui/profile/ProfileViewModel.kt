package com.pelayo.gimnasio.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelayo.gimnasio.data.repository.ProfileRepository
import com.pelayo.gimnasio.domain.model.ActivityLevel
import com.pelayo.gimnasio.domain.model.BodyMeasurementEntry
import com.pelayo.gimnasio.domain.model.BodyMetrics
import com.pelayo.gimnasio.domain.model.Sex
import com.pelayo.gimnasio.domain.model.UserProfileData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ProfileViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfileData?>(null)
    val profile: StateFlow<UserProfileData?> = _profile

    private val _metrics = MutableStateFlow<BodyMetrics?>(null)
    val metrics: StateFlow<BodyMetrics?> = _metrics

    private val _measurements = MutableStateFlow<List<BodyMeasurementEntry>>(emptyList())
    val measurements: StateFlow<List<BodyMeasurementEntry>> = _measurements

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _profile.value = repository.profile()
            _measurements.value = repository.measurements()
            _metrics.value = repository.currentMetrics()
        }
    }

    fun saveProfile(
        name: String?,
        sex: Sex,
        birthDate: LocalDate?,
        heightCm: Double?,
        activityLevel: ActivityLevel,
    ) {
        viewModelScope.launch {
            repository.saveProfile(
                UserProfileData(
                    name = name?.takeIf { it.isNotBlank() },
                    sex = sex,
                    birthDate = birthDate,
                    heightCm = heightCm,
                    activityLevel = activityLevel,
                    goal = _profile.value?.goal,
                    targetKcalOverride = _profile.value?.targetKcalOverride,
                ),
            )
            reload()
        }
    }

    fun addMeasurement(
        weightKg: Double,
        neckCm: Double?,
        waistCm: Double?,
        hipCm: Double?,
    ) {
        viewModelScope.launch {
            repository.addMeasurement(weightKg, neckCm, waistCm, hipCm)
            reload()
        }
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            repository.deleteMeasurement(id)
            reload()
        }
    }
}
