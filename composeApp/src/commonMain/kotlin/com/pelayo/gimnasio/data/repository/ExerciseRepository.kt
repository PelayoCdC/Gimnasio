package com.pelayo.gimnasio.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pelayo.gimnasio.db.GimnasioDb
import com.pelayo.gimnasio.domain.calc.CandidateExercise
import com.pelayo.gimnasio.domain.model.EquipmentItem
import com.pelayo.gimnasio.domain.model.ExerciseDetail
import com.pelayo.gimnasio.domain.model.ExerciseListItem
import com.pelayo.gimnasio.domain.model.ExerciseType
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleGroups
import com.pelayo.gimnasio.domain.model.MuscleRole
import com.pelayo.gimnasio.domain.model.MuscleWithRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExerciseRepository(
    private val db: GimnasioDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun exerciseCount(): Flow<Long> =
        db.catalogQueries.countExercises().asFlow().map { it.executeAsOne() }

    fun searchExercises(
        query: String,
        muscleGroupId: Long?,
        equipmentId: Long?,
        limit: Long = 200,
    ): Flow<List<ExerciseListItem>> =
        db.catalogQueries.searchExercises(
            query = query.trim(),
            muscleGroupId = muscleGroupId,
            equipmentId = equipmentId,
            limit = limit,
            offset = 0,
        ).asFlow().mapToList(dispatcher).map { rows ->
            rows.map { row ->
                ExerciseListItem(
                    id = row.id,
                    name = row.nameEs,
                    imageUrl = row.mainImageUrl,
                    exerciseType = ExerciseType.valueOf(row.exerciseType),
                    isCustom = row.isCustom == 1L,
                )
            }
        }

    fun muscleGroups(): List<MuscleGroupInfo> = MuscleGroups.all

    suspend fun equipment(): List<EquipmentItem> = withContext(dispatcher) {
        db.catalogQueries.selectAllEquipment().executeAsList().map { EquipmentItem(it.id, it.nameEs) }
    }

    /** Catálogo completo en la forma que necesita el generador de rutinas. */
    suspend fun candidates(): List<CandidateExercise> = withContext(dispatcher) {
        val equipmentByExercise = db.catalogQueries.selectAllExerciseEquipment().executeAsList()
            .groupBy({ it.exerciseId }, { it.equipmentId })

        db.catalogQueries.selectExerciseGroups().executeAsList()
            .groupBy { it.id }
            .map { (exerciseId, rows) ->
                CandidateExercise(
                    id = exerciseId,
                    name = rows.first().nameEs,
                    primaryGroupIds = rows.filter { it.role == "PRIMARY" }
                        .map { it.muscleGroupId }.toSet(),
                    secondaryGroupIds = rows.filter { it.role == "SECONDARY" }
                        .map { it.muscleGroupId }.toSet(),
                    equipmentIds = equipmentByExercise[exerciseId].orEmpty().toSet(),
                )
            }
    }

    suspend fun exerciseDetail(id: Long): ExerciseDetail? = withContext(dispatcher) {
        val e = db.catalogQueries.selectExerciseById(id).executeAsOneOrNull() ?: return@withContext null
        val images = db.catalogQueries.selectImagesForExercise(id).executeAsList()
        val muscles = db.catalogQueries.selectMusclesForExercise(id).executeAsList()
        val equipment = db.catalogQueries.selectEquipmentForExercise(id).executeAsList()
        ExerciseDetail(
            id = e.id,
            name = e.nameEs,
            nameEn = e.nameEn,
            description = e.descriptionEs,
            videoUrl = e.videoUrl,
            metValue = e.metValue,
            exerciseType = ExerciseType.valueOf(e.exerciseType),
            isCustom = e.isCustom == 1L,
            images = images.map { it.url },
            muscles = muscles.map {
                MuscleWithRole(
                    id = it.id,
                    nameEs = it.nameEs,
                    isFront = it.isFront == 1L,
                    muscleGroupId = it.muscleGroupId,
                    role = MuscleRole.valueOf(it.role),
                )
            },
            equipment = equipment.map { EquipmentItem(it.id, it.nameEs) },
        )
    }
}
