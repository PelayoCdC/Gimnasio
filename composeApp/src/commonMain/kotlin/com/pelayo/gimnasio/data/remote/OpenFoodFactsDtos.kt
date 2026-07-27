package com.pelayo.gimnasio.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class OffProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

/**
 * Respuesta de search.openfoodfacts.org, que devuelve los productos en `hits`.
 * De ahí solo se aprovechan los códigos de barras: ese servicio no incluye los
 * nutrientes ni pidiéndolos.
 */
@Serializable
data class OffSearchResponse(
    val count: Int = 0,
    val hits: List<OffProduct> = emptyList(),
)

/** Respuesta de la búsqueda de la API v2, que sí trae los productos completos. */
@Serializable
data class OffProductsResponse(
    val count: Int = 0,
    val products: List<OffProduct> = emptyList(),
)

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_es") val productNameEs: String? = null,
    // El buscador nuevo devuelve las marcas en una lista y la API v2 en una
    // cadena separada por comas; el serializador acepta las dos formas.
    @Serializable(with = BrandsSerializer::class) val brands: String? = null,
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

/**
 * Lee `brands` venga como cadena ("Hacendado,Mercadona") o como lista
 * (["Hacendado", "Mercadona"]), y siempre devuelve una cadena.
 *
 * Que la lista reventara la deserialización es lo que dejó la búsqueda inservible
 * enseñando un error de conexión que no tenía nada que ver.
 */
private object BrandsSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("brands", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }

            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value.orEmpty())
    }
}

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
