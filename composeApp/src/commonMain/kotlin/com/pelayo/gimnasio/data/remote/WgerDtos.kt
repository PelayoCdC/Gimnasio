package com.pelayo.gimnasio.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WgerPage<T>(
    val count: Int = 0,
    val next: String? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class WgerLanguage(
    val id: Int,
    @SerialName("short_name") val shortName: String = "",
)

@Serializable
data class WgerMuscleDto(
    val id: Int,
    val name: String = "",
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("is_front") val isFront: Boolean = true,
)

@Serializable
data class WgerEquipmentDto(
    val id: Int,
    val name: String = "",
)

@Serializable
data class WgerCategoryDto(
    val id: Int,
    val name: String = "",
)

@Serializable
data class WgerTranslationDto(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val language: Int,
)

@Serializable
data class WgerImageDto(
    val id: Int = 0,
    val image: String = "",
    @SerialName("is_main") val isMain: Boolean = false,
)

@Serializable
data class WgerVideoDto(
    val id: Int = 0,
    val video: String = "",
)

@Serializable
data class WgerExerciseInfoDto(
    val id: Int,
    val category: WgerCategoryDto? = null,
    val muscles: List<WgerMuscleDto> = emptyList(),
    @SerialName("muscles_secondary") val musclesSecondary: List<WgerMuscleDto> = emptyList(),
    val equipment: List<WgerEquipmentDto> = emptyList(),
    val translations: List<WgerTranslationDto> = emptyList(),
    val images: List<WgerImageDto> = emptyList(),
    val videos: List<WgerVideoDto> = emptyList(),
)

/** Ejercicio ya resuelto a nuestro idioma y limpio de HTML, listo para persistir. */
data class MappedExercise(
    val wgerId: Long,
    val nameEs: String,
    val nameEn: String?,
    val descriptionEs: String?,
    val categoryId: Long?,
    val videoUrl: String?,
    val metValue: Double,
    val exerciseType: String,
    val primaryMuscleIds: List<Long>,
    val secondaryMuscleIds: List<Long>,
    val equipmentIds: List<Long>,
    val imageUrls: List<Pair<String, Boolean>>, // url → isMain
)
