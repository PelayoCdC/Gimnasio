package com.pelayo.gimnasio.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class OffProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

/**
 * Respuesta de search.openfoodfacts.org. Los productos vienen en `hits`, no en
 * `products` como en la API antigua, pero cada uno tiene los mismos campos.
 */
@Serializable
data class OffSearchResponse(
    val count: Int = 0,
    val hits: List<OffProduct> = emptyList(),
)

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_es") val productNameEs: String? = null,
    val brands: String? = null,
    @SerialName("image_front_small_url") val imageUrl: String? = null,
    // OFF devuelve este campo unas veces como número y otras como cadena.
    @SerialName("serving_quantity") val servingQuantity: JsonPrimitive? = null,
    val nutriments: OffNutriments = OffNutriments(),
) {
    val servingSizeGrams: Double?
        get() = servingQuantity?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
}

@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_100g") val kcalPer100: Double? = null,
    @SerialName("proteins_100g") val proteinPer100: Double? = null,
    @SerialName("carbohydrates_100g") val carbsPer100: Double? = null,
    @SerialName("fat_100g") val fatPer100: Double? = null,
    @SerialName("fiber_100g") val fiberPer100: Double? = null,
    // Algunos productos solo traen kJ; sirve de respaldo.
    @SerialName("energy_100g") val energyPer100: Double? = null,
)

/** Alimento ya normalizado, listo para persistir en la tabla Food. */
data class MappedFood(
    val barcode: String?,
    val name: String,
    val brand: String?,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val fiberPer100: Double?,
    val servingSizeG: Double?,
    val imageUrl: String?,
)
