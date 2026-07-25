package com.pelayo.gimnasio.domain.model

data class ExerciseListItem(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val exerciseType: ExerciseType,
    val isCustom: Boolean,
)

data class MuscleWithRole(
    val id: Long,
    val nameEs: String,
    val isFront: Boolean,
    val muscleGroupId: Long,
    val role: MuscleRole,
)

data class EquipmentItem(
    val id: Long,
    val nameEs: String,
)

data class ExerciseDetail(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val description: String?,
    val videoUrl: String?,
    val metValue: Double,
    val exerciseType: ExerciseType,
    val isCustom: Boolean,
    val images: List<String>,
    val muscles: List<MuscleWithRole>,
    val equipment: List<EquipmentItem>,
)
