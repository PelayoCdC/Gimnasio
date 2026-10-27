package com.pelayo.gimnasio.data.remote

import com.pelayo.gimnasio.data.db.WgerMappings

/**
 * Convierte los DTOs de wger a [MappedExercise], eligiendo la traducción en español
 * con fallback a inglés y limpiando el HTML de las descripciones.
 */
object WgerMapper {

    private val htmlTagRegex = Regex("<[^>]*>")

    fun cleanHtml(html: String): String = html
        .replace("</p>", "\n")
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace("</li>", "\n")
        .replace("<li>", "• ")
        .replace(htmlTagRegex, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /**
     * Devuelve null si el ejercicio no tiene traducción utilizable (sin nombre).
     */
    fun map(dto: WgerExerciseInfoDto, spanishLangId: Int?, englishLangId: Int?): MappedExercise? {
        val es = dto.translations.firstOrNull { it.language == spanishLangId && it.name.isNotBlank() }
        val en = dto.translations.firstOrNull { it.language == englishLangId && it.name.isNotBlank() }
        val chosen = es ?: en ?: return null

        val categoryId = dto.category?.id?.toLong()
        return MappedExercise(
            wgerId = dto.id.toLong(),
            nameEs = chosen.name.trim(),
            nameEn = en?.name?.trim(),
            descriptionEs = chosen.description.takeIf { it.isNotBlank() }?.let { cleanHtml(it) },
            categoryId = categoryId,
            videoUrl = dto.videos.firstOrNull { it.video.isNotBlank() }?.video,
            metValue = WgerMappings.metForCategory(categoryId),
            exerciseType = WgerMappings.exerciseTypeFor(chosen.name, categoryId),
            primaryMuscleIds = dto.muscles.map { it.id.toLong() }.filter { it in WgerMappings.muscles },
            secondaryMuscleIds = dto.musclesSecondary.map { it.id.toLong() }
                .filter { it in WgerMappings.muscles }
                .minus(dto.muscles.map { it.id.toLong() }.toSet()),
            equipmentIds = dto.equipment.map { it.id.toLong() },
            imageUrls = dto.images.filter { it.image.isNotBlank() }.map { it.image to it.isMain },
            // Los de las dos traducciones: "press banca" y "bench press" son cosas
            // distintas que el usuario puede teclear.
            aliases = listOfNotNull(es, en)
                .flatMap { translation -> translation.aliases.map { it.alias.trim() } }
                .filter { it.isNotBlank() }
                .distinct(),
        )
    }
}
