package com.pelayo.gimnasio.data.remote

import com.pelayo.gimnasio.domain.model.FoodUnit

/** Normaliza los productos de Open Food Facts a nuestro modelo de alimento. */
object OffMapper {

    private const val KJ_PER_KCAL = 4.184

    /** Devuelve null si el producto no tiene nombre o no aporta energía utilizable. */
    fun map(product: OffProduct): MappedFood? {
        val name = product.productNameEs?.takeIf { it.isNotBlank() }
            ?: product.productName?.takeIf { it.isNotBlank() }
            ?: return null

        val kcal = product.nutriments.kcalPer100
            ?: product.nutriments.energyPer100?.let { it / KJ_PER_KCAL }
            ?: return null

        return MappedFood(
            barcode = product.code?.takeIf { it.isNotBlank() },
            name = name.trim(),
            brand = product.brands?.takeIf { it.isNotBlank() }?.substringBefore(",")?.trim(),
            kcalPer100 = kcal,
            proteinPer100 = product.nutriments.proteinPer100 ?: 0.0,
            carbsPer100 = product.nutriments.carbsPer100 ?: 0.0,
            fatPer100 = product.nutriments.fatPer100 ?: 0.0,
            fiberPer100 = product.nutriments.fiberPer100,
            servingSize = product.servingSize,
            imageUrl = product.imageUrl?.takeIf { it.isNotBlank() },
            unit = if (product.isLiquid) FoodUnit.ML else FoodUnit.G,
        )
    }

    fun mapAll(products: List<OffProduct>): List<MappedFood> = products.mapNotNull { map(it) }
}
