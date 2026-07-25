package com.pelayo.gimnasio.data.remote

import com.pelayo.gimnasio.data.remote.HttpClientFactory.json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OffMapperTest {

    // Respuesta recortada de /api/v2/product/, con campos extra a propósito.
    private val nutellaJson = """
    {
      "code": "3017624010701",
      "status": 1,
      "status_verbose": "product found",
      "product": {
        "code": "3017624010701",
        "product_name": "Nutella",
        "product_name_es": "Crema de cacao Nutella",
        "brands": "Ferrero, Nutella",
        "image_front_small_url": "https://images.openfoodfacts.org/nutella.jpg",
        "serving_quantity": "15",
        "nutriments": {
          "energy-kcal_100g": 539,
          "proteins_100g": 6.3,
          "carbohydrates_100g": 57.5,
          "fat_100g": 30.9,
          "energy_100g": 2252
        }
      }
    }
    """.trimIndent()

    @Test
    fun mapea_producto_prefiriendo_el_nombre_en_espanol() {
        val response = json.decodeFromString<OffProductResponse>(nutellaJson)
        assertEquals(1, response.status)

        val food = assertNotNull(OffMapper.map(response.product!!))
        assertEquals("3017624010701", food.barcode)
        assertEquals("Crema de cacao Nutella", food.name)
        // De "Ferrero, Nutella" nos quedamos con la primera marca.
        assertEquals("Ferrero", food.brand)
        assertEquals(539.0, food.kcalPer100)
        assertEquals(6.3, food.proteinPer100)
        assertEquals(57.5, food.carbsPer100)
        assertEquals(30.9, food.fatPer100)
        assertNull(food.fiberPer100)
        assertEquals(15.0, food.servingSizeG)
    }

    @Test
    fun serving_quantity_numerico_tambien_se_parsea() {
        // OFF devuelve este campo unas veces entrecomillado y otras como número.
        val numeric = nutellaJson.replace("\"serving_quantity\": \"15\"", "\"serving_quantity\": 15.5")
        val response = json.decodeFromString<OffProductResponse>(numeric)
        assertEquals(15.5, OffMapper.map(response.product!!)!!.servingSizeG)
    }

    @Test
    fun sin_serving_quantity_queda_a_null() {
        val without = nutellaJson.replace("\"serving_quantity\": \"15\",", "")
        val response = json.decodeFromString<OffProductResponse>(without)
        assertNull(OffMapper.map(response.product!!)!!.servingSizeG)
    }

    @Test
    fun sin_kcal_directas_convierte_desde_kilojulios() {
        val onlyKj = """
        {
          "status": 1,
          "product": {
            "code": "123",
            "product_name": "Producto sin kcal",
            "nutriments": { "energy_100g": 2000, "proteins_100g": 5 }
          }
        }
        """.trimIndent()
        val product = json.decodeFromString<OffProductResponse>(onlyKj).product!!
        val food = assertNotNull(OffMapper.map(product))
        // 2000 kJ / 4.184 ≈ 478 kcal
        assertTrue(abs(478.0 - food.kcalPer100) < 1.0, "obtenido ${food.kcalPer100}")
        assertEquals(0.0, food.carbsPer100, "los macros ausentes se asumen a cero")
    }

    @Test
    fun producto_sin_energia_se_descarta() {
        val noEnergy = """
        {"status":1,"product":{"code":"1","product_name":"Agua","nutriments":{}}}
        """.trimIndent()
        assertNull(OffMapper.map(json.decodeFromString<OffProductResponse>(noEnergy).product!!))
    }

    @Test
    fun producto_sin_nombre_se_descarta() {
        val noName = """
        {"status":1,"product":{"code":"1","nutriments":{"energy-kcal_100g":100}}}
        """.trimIndent()
        assertNull(OffMapper.map(json.decodeFromString<OffProductResponse>(noName).product!!))
    }

    @Test
    fun codigo_de_barras_no_encontrado_devuelve_status_cero() {
        val notFound = """{"code":"0000","status":0,"status_verbose":"product not found"}"""
        val response = json.decodeFromString<OffProductResponse>(notFound)
        assertEquals(0, response.status)
        assertNull(response.product)
    }

    @Test
    fun la_busqueda_descarta_los_productos_inservibles() {
        // search.openfoodfacts.org devuelve los productos en "hits", no en "products".
        val search = """
        {
          "count": 3,
          "page": 1,
          "page_size": 25,
          "timed_out": false,
          "hits": [
            {"code":"1","product_name":"Bueno","nutriments":{"energy-kcal_100g":100}},
            {"code":"2","nutriments":{"energy-kcal_100g":100}},
            {"code":"3","product_name":"Sin energía","nutriments":{}}
          ]
        }
        """.trimIndent()
        val response = json.decodeFromString<OffSearchResponse>(search)
        assertEquals(3, response.count)
        assertEquals(listOf("Bueno"), OffMapper.mapAll(response.hits).map { it.name })
    }

    @Test
    fun parsea_la_respuesta_real_del_buscador_nuevo() {
        // Recorte de una respuesta real de search.openfoodfacts.org.
        val real = """
        {
          "hits": [
            {
              "code": "7861029402199",
              "product_name": "Avena Vita",
              "brands": "Avena Vita",
              "image_front_small_url": "https://images.openfoodfacts.org/avena.jpg",
              "nutriments": {
                "sugars_100g": 28.333333333333336,
                "proteins_100g": 3.3333333333333335,
                "energy-kcal_100g": 166.66666666666669,
                "carbohydrates_100g": 40
              },
              "nutriscore_grade": "d",
              "brands_tags": ["avena-vita"]
            }
          ],
          "count": 548,
          "page": 1,
          "is_count_exact": false,
          "took": 12
        }
        """.trimIndent()
        val response = json.decodeFromString<OffSearchResponse>(real)
        assertEquals(548, response.count)

        val food = assertNotNull(OffMapper.mapAll(response.hits).singleOrNull())
        assertEquals("Avena Vita", food.name)
        assertEquals("7861029402199", food.barcode)
        assertTrue(abs(166.67 - food.kcalPer100) < 0.01)
        assertEquals(40.0, food.carbsPer100)
        // No viene grasa: se asume cero en vez de descartar el producto.
        assertEquals(0.0, food.fatPer100)
    }
}
