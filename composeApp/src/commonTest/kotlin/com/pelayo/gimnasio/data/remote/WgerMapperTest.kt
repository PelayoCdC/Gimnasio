package com.pelayo.gimnasio.data.remote

import com.pelayo.gimnasio.data.remote.HttpClientFactory.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WgerMapperTest {

    // Fixture recortado con la forma real de /api/v2/exerciseinfo/ (campos extra incluidos
    // a propósito: deben ignorarse con ignoreUnknownKeys).
    private val fixture = """
    {
      "count": 1,
      "next": null,
      "previous": null,
      "results": [
        {
          "id": 345,
          "uuid": "c788d643-150a-4ac7-97ef-84643c6419bf",
          "created": "2023-08-06T10:17:17.349574+02:00",
          "category": { "id": 11, "name": "Chest" },
          "muscles": [ { "id": 4, "name": "Pectoralis major", "name_en": "Chest", "is_front": true } ],
          "muscles_secondary": [
            { "id": 5, "name": "Triceps brachii", "name_en": "Triceps", "is_front": false },
            { "id": 2, "name": "Anterior deltoid", "name_en": "Shoulders", "is_front": true }
          ],
          "equipment": [ { "id": 1, "name": "Barbell" }, { "id": 8, "name": "Bench" } ],
          "translations": [
            {
              "id": 1060,
              "name": "Bench Press",
              "description": "<p>Lie on the bench.</p><p>Lower the bar slowly.</p>",
              "language": 2
            },
            {
              "id": 2087,
              "name": "Press de banca",
              "description": "<p>Túmbate en el banco.</p><ul><li>Baja la barra</li><li>Empuja</li></ul>",
              "language": 4
            }
          ],
          "images": [
            { "id": 20, "image": "https://wger.de/media/exercise-images/345/main.png", "is_main": true },
            { "id": 21, "image": "https://wger.de/media/exercise-images/345/alt.png", "is_main": false }
          ],
          "videos": [ { "id": 5, "video": "https://wger.de/media/exercise-video/345.mp4" } ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun parsea_pagina_y_mapea_a_espanol() {
        val page = json.decodeFromString<WgerPage<WgerExerciseInfoDto>>(fixture)
        assertEquals(1, page.count)
        val dto = page.results.single()

        val mapped = WgerMapper.map(dto, spanishLangId = 4, englishLangId = 2)!!

        assertEquals(345L, mapped.wgerId)
        assertEquals("Press de banca", mapped.nameEs)
        assertEquals("Bench Press", mapped.nameEn)
        val description = mapped.descriptionEs!!
        assertTrue(description.contains("Túmbate en el banco."))
        assertTrue(description.contains("• Baja la barra"))
        assertTrue(!description.contains("<"))
        assertEquals(11L, mapped.categoryId)
        assertEquals(3.5, mapped.metValue) // Chest → fuerza general
        assertEquals("REPS", mapped.exerciseType)
        assertEquals(listOf(4L), mapped.primaryMuscleIds)
        assertEquals(listOf(5L, 2L), mapped.secondaryMuscleIds)
        assertEquals(listOf(1L, 8L), mapped.equipmentIds)
        assertEquals(2, mapped.imageUrls.size)
        assertTrue(mapped.imageUrls.first().second) // primera es is_main
        assertEquals("https://wger.de/media/exercise-video/345.mp4", mapped.videoUrl)
    }

    @Test
    fun sin_traduccion_espanola_usa_ingles() {
        val page = json.decodeFromString<WgerPage<WgerExerciseInfoDto>>(fixture)
        val dto = page.results.single()
        val mapped = WgerMapper.map(dto, spanishLangId = 99, englishLangId = 2)!!
        assertEquals("Bench Press", mapped.nameEs)
    }

    @Test
    fun sin_ninguna_traduccion_devuelve_null() {
        val page = json.decodeFromString<WgerPage<WgerExerciseInfoDto>>(fixture)
        val dto = page.results.single()
        assertNull(WgerMapper.map(dto, spanishLangId = 98, englishLangId = 99))
    }

    @Test
    fun cardio_se_mapea_a_tiempo_y_met_alto() {
        val cardio = """
        {
          "id": 400,
          "category": { "id": 15, "name": "Cardio" },
          "translations": [ { "id": 1, "name": "Correr", "description": "", "language": 4 } ]
        }
        """.trimIndent()
        val dto = json.decodeFromString<WgerExerciseInfoDto>(cardio)
        val mapped = WgerMapper.map(dto, 4, 2)!!
        assertEquals("TIME", mapped.exerciseType)
        assertEquals(7.0, mapped.metValue)
        assertNull(mapped.descriptionEs)
    }
}
