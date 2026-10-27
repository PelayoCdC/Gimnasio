package com.pelayo.gimnasio.data.db

import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.calc.ExerciseFormatClassifier
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.util.normalizeForSearch

/**
 * Siembra los catálogos fijos (grupos musculares, músculos de wger y equipamiento
 * conocido). Idempotente: usa INSERT OR REPLACE con ids estables.
 */
object DatabaseSeeder {

    fun seed(db: GimnasioDb) {
        val q = db.catalogQueries
        db.transaction {
            MuscleGroups.all.forEach { group ->
                q.insertMuscleGroup(group.id, group.name, group.category.name)
            }
            WgerMappings.muscles.forEach { (id, seed) ->
                q.insertMuscle(id, seed.nameEs, null, if (seed.isFront) 1 else 0, seed.groupId)
            }
            WgerMappings.equipmentNamesEs.forEach { (id, nameEs) ->
                q.insertEquipment(id, nameEs)
            }
        }
        fillMissingSearchNames(db)
        fillMissingFoodSearchNames(db)
        fixTimeBasedExerciseTypes(db)
    }

    /**
     * Igual que [fillMissingSearchNames] pero para los alimentos, que ganaron su
     * `searchName` (nombre y marca, normalizados) en la versión 4 del esquema.
     */
    fun fillMissingFoodSearchNames(db: GimnasioDb) {
        val q = db.nutritionQueries
        val pending = q.selectFoodsWithoutSearchName().executeAsList()
        if (pending.isEmpty()) return
        db.transaction {
            pending.forEach { row ->
                q.updateFoodSearchName(foodSearchNameFor(row.name, row.brand), row.id)
            }
        }
    }

    /** Contra lo que busca el catálogo de alimentos: nombre y marca normalizados. */
    fun foodSearchNameFor(name: String, brand: String?): String =
        normalizeForSearch(listOfNotNull(name, brand).joinToString(" "))

    /**
     * Corrige el tipo de los ejercicios que se miden en tiempo y llegaron marcados
     * como de repeticiones. wger no distingue los isométricos, así que la plancha
     * pedía kilos y reps; el catálogo ya descargado no se arregla solo, y volver a
     * sincronizarlo entero por esto sería pasarse.
     *
     * Solo toca los del catálogo de wger: si el usuario se creó un ejercicio, el
     * tipo lo eligió él.
     */
    fun fixTimeBasedExerciseTypes(db: GimnasioDb) {
        val q = db.catalogQueries
        val wrong = q.selectExerciseNames().executeAsList().filter { row ->
            row.exerciseType == "REPS" &&
                row.isCustom == 0L &&
                ExerciseFormatClassifier.isTimeBased(row.nameEs, row.categoryId)
        }
        if (wrong.isEmpty()) return
        db.transaction {
            wrong.forEach { q.updateExerciseType("TIME", it.id) }
        }
    }

    /**
     * Rellena `searchName` de los ejercicios que vengan de una versión anterior.
     * La migración no puede hacerlo: quitar tildes en SQL no es posible, así que
     * la columna nace vacía y se completa aquí, una sola vez.
     */
    fun fillMissingSearchNames(db: GimnasioDb) {
        val q = db.catalogQueries
        val pending = q.selectExercisesWithoutSearchName().executeAsList()
        if (pending.isEmpty()) return
        db.transaction {
            pending.forEach { row ->
                q.updateSearchName(searchNameFor(row.nameEs, row.nameEn), row.id)
            }
        }
    }

    /**
     * Contra lo que busca el catálogo: el nombre en español, el inglés y los
     * nombres alternativos que trae wger, todo junto y normalizado. Los alias son
     * justo lo que la gente teclea ("press banca" en vez de "press de banca con
     * barra"), así que sin ellos la búsqueda se queda corta.
     */
    fun searchNameFor(nameEs: String, nameEn: String?, aliases: List<String> = emptyList()): String =
        normalizeForSearch((listOfNotNull(nameEs, nameEn) + aliases).joinToString(" "))
}
