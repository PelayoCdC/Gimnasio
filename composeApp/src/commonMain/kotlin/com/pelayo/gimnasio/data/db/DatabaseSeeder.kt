package com.pelayo.gimnasio.data.db

import com.pelayo.gimnasio.db.GimnasioDb
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

    /** El nombre en español y el inglés juntos, para poder buscar por cualquiera de los dos. */
    fun searchNameFor(nameEs: String, nameEn: String?): String =
        normalizeForSearch(listOfNotNull(nameEs, nameEn).joinToString(" "))
}
