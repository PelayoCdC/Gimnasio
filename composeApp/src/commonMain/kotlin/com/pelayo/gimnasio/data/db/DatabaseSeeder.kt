package com.pelayo.gimnasio.data.db

import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.model.MuscleGroups

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
    }
}
