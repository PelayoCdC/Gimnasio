package com.pelayo.gimnasio.data.repository

import com.pelayo.gimnasio.data.db.DatabaseSeeder
import com.pelayo.gimnasio.data.db.WgerMappings
import com.pelayo.gimnasio.data.remote.WgerApi
import com.pelayo.gimnasio.data.remote.WgerMapper
import com.pelayo.gimnasio.db.GimnasioDb
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface SyncProgress {
    data class Running(val done: Int, val total: Int) : SyncProgress
    data class Finished(val imported: Int) : SyncProgress
    data class Failed(val message: String) : SyncProgress
}

/**
 * Descarga el catálogo de wger (músculos, equipamiento y ejercicios paginados)
 * y lo materializa en la DB local. Reejecutable: hace upsert por wgerId.
 */
class CatalogSyncService(
    private val api: WgerApi,
    private val db: GimnasioDb,
    private val settings: Settings,
) {
    companion object {
        const val KEY_SYNCED_AT = "catalogSyncedAt"
    }

    val lastSyncedAt: String? get() = settings.getStringOrNull(KEY_SYNCED_AT)

    fun sync(): Flow<SyncProgress> = flow {
        try {
            emit(SyncProgress.Running(0, 0))

            val languages = api.getLanguages()
            val esId = languages.firstOrNull { it.shortName.equals("es", true) }?.id
            val enId = languages.firstOrNull { it.shortName.equals("en", true) }?.id

            // Equipamiento: lo desconocido entra con el nombre de la API.
            val equipment = api.getEquipment()
            db.transaction {
                equipment.forEach { eq ->
                    val id = eq.id.toLong()
                    val name = WgerMappings.equipmentNamesEs[id] ?: eq.name
                    db.catalogQueries.insertEquipment(id, name)
                }
            }

            var imported = 0
            var offset = 0
            var total: Int
            do {
                val page = api.getExercisePage(offset)
                total = page.count
                db.transaction {
                    page.results.forEach { dto ->
                        val mapped = WgerMapper.map(dto, esId, enId) ?: return@forEach
                        val q = db.catalogQueries
                        q.upsertExerciseByWgerId(
                            nameEs = mapped.nameEs,
                            nameEn = mapped.nameEn,
                            descriptionEs = mapped.descriptionEs,
                            categoryId = mapped.categoryId,
                            videoUrl = mapped.videoUrl,
                            metValue = mapped.metValue,
                            exerciseType = mapped.exerciseType,
                            wgerId = mapped.wgerId,
                            searchName = DatabaseSeeder.searchNameFor(mapped.nameEs, mapped.nameEn),
                        )
                        val exerciseId = q.selectExerciseIdByWgerId(mapped.wgerId).executeAsOne()
                        q.deleteExerciseMuscles(exerciseId)
                        mapped.primaryMuscleIds.forEach { q.insertExerciseMuscle(exerciseId, it, "PRIMARY") }
                        mapped.secondaryMuscleIds.forEach { q.insertExerciseMuscle(exerciseId, it, "SECONDARY") }
                        q.deleteExerciseEquipment(exerciseId)
                        mapped.equipmentIds.forEach { q.insertExerciseEquipment(exerciseId, it) }
                        q.deleteExerciseImages(exerciseId)
                        mapped.imageUrls.forEach { (url, isMain) ->
                            q.insertExerciseImage(exerciseId, url, if (isMain) 1 else 0)
                        }
                        imported++
                    }
                }
                offset += WgerApi.PAGE_SIZE
                emit(SyncProgress.Running(minOf(offset, total), total))
            } while (page.next != null)

            settings.putString(KEY_SYNCED_AT, com.pelayo.gimnasio.util.nowIsoString())
            emit(SyncProgress.Finished(imported))
        } catch (e: Exception) {
            emit(SyncProgress.Failed(e.message ?: "Error desconocido al descargar el catálogo"))
        }
    }
}
