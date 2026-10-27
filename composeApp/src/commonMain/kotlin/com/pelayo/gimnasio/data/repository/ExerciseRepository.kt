package com.pelayo.gimnasio.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.pelayo.gimnasio.data.db.DatabaseSeeder
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
import com.pelayo.gimnasio.util.searchTerms
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

    // mapToOne(dispatcher) y no map { executeAsOne() }: la consulta debe correr
    // en IO, no en el hilo del colector.
    fun exerciseCount(): Flow<Long> =
        db.catalogQueries.countExercises().asFlow().mapToOne(dispatcher)

    /**
     * Busca por palabras sueltas: todas tienen que aparecer en el nombre, en
     * cualquier orden y sin importar mayúsculas ni tildes.
     */
    fun searchExercises(
        query: String,
        muscleGroupId: Long?,
        equipmentId: Long?,
        limit: Long = 200,
    ): Flow<List<ExerciseListItem>> {
        val terms = searchTerms(query)
        return db.catalogQueries.searchExercises(
            term1 = terms.getOrNull(0),
            term2 = terms.getOrNull(1),
            term3 = terms.getOrNull(2),
            term4 = terms.getOrNull(3),
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
                    categoryId = rows.first().categoryId,
                )
            }
    }

    // --- Ejercicios propios --------------------------------------------------

    /**
     * Crea un ejercicio propio. Los grupos musculares se traducen al primer
     * músculo de wger de cada grupo: la tabla de músculos es de wger y el
     * análisis de balance agrega por grupo, así que con uno basta.
     */
    suspend fun createCustomExercise(
        name: String,
        description: String?,
        exerciseType: ExerciseType,
        primaryGroupIds: Set<Long>,
        secondaryGroupIds: Set<Long> = emptySet(),
        metValue: Double = 3.5,
    ): Long = withContext(dispatcher) {
        val muscleByGroup = muscleByGroup()
        db.transactionWithResult {
            db.catalogQueries.insertCustomExercise(
                nameEs = name,
                descriptionEs = description?.takeIf { it.isNotBlank() },
                categoryId = null,
                videoUrl = null,
                metValue = metValue,
                exerciseType = exerciseType.name,
                searchName = DatabaseSeeder.searchNameFor(name, null),
            )
            val id = db.catalogQueries.lastInsertRowId().executeAsOne()
            primaryGroupIds.mapNotNull { muscleByGroup[it] }.forEach {
                db.catalogQueries.insertExerciseMuscle(id, it, "PRIMARY")
            }
            secondaryGroupIds.mapNotNull { muscleByGroup[it] }.forEach {
                db.catalogQueries.insertExerciseMuscle(id, it, "SECONDARY")
            }
            id
        }
    }

    suspend fun updateCustomExercise(
        id: Long,
        name: String,
        description: String?,
        exerciseType: ExerciseType,
        primaryGroupIds: Set<Long>,
        secondaryGroupIds: Set<Long> = emptySet(),
    ) = withContext(dispatcher) {
        val muscleByGroup = muscleByGroup()
        db.transaction {
            db.catalogQueries.updateCustomExercise(
                name,
                description?.takeIf { it.isNotBlank() },
                3.5,
                exerciseType.name,
                DatabaseSeeder.searchNameFor(name, null),
                id,
            )
            db.catalogQueries.deleteExerciseMuscles(id)
            primaryGroupIds.mapNotNull { muscleByGroup[it] }.forEach {
                db.catalogQueries.insertExerciseMuscle(id, it, "PRIMARY")
            }
            secondaryGroupIds.mapNotNull { muscleByGroup[it] }.forEach {
                db.catalogQueries.insertExerciseMuscle(id, it, "SECONDARY")
            }
        }
    }

    /** En cuántas rutinas y series del historial aparece el ejercicio. */
    suspend fun customExerciseUsage(id: Long): Pair<Int, Int> = withContext(dispatcher) {
        db.catalogQueries.countRoutineUsesForExercise(id).executeAsOne().toInt() to
            db.catalogQueries.countSetLogsForExercise(id).executeAsOne().toInt()
    }

    /**
     * Borra un ejercicio propio. Devuelve false sin tocar nada si está en alguna
     * rutina o en el historial: ambos lo referencian sin ON DELETE y el historial
     * no se reescribe.
     */
    suspend fun deleteCustomExercise(id: Long): Boolean = withContext(dispatcher) {
        db.transactionWithResult {
            val used = db.catalogQueries.countRoutineUsesForExercise(id).executeAsOne() > 0 ||
                db.catalogQueries.countSetLogsForExercise(id).executeAsOne() > 0
            if (used) {
                false
            } else {
                db.catalogQueries.deleteCustomExercise(id)
                true
            }
        }
    }

    /** Primer músculo de wger de cada grupo, para materializar el alta por grupos. */
    private fun muscleByGroup(): Map<Long, Long> =
        db.catalogQueries.selectAllMuscles().executeAsList()
            .groupBy { it.muscleGroupId }
            .mapValues { (_, muscles) -> muscles.first().id }

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
