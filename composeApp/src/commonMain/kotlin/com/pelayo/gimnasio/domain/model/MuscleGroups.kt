package com.pelayo.gimnasio.domain.model

data class MuscleGroupInfo(
    val id: Long,
    val name: String,
    val category: MuscleGroupCategory,
)

/**
 * Catálogo canónico de grupos musculares. Es la semilla de la tabla MuscleGroup
 * y la referencia del análisis de balance; los ids son estables.
 */
object MuscleGroups {
    val PECHO = MuscleGroupInfo(1, "Pecho", MuscleGroupCategory.PUSH)
    val ESPALDA = MuscleGroupInfo(2, "Espalda", MuscleGroupCategory.PULL)
    val HOMBROS = MuscleGroupInfo(3, "Hombros", MuscleGroupCategory.PUSH)
    val BICEPS = MuscleGroupInfo(4, "Bíceps", MuscleGroupCategory.PULL)
    val TRICEPS = MuscleGroupInfo(5, "Tríceps", MuscleGroupCategory.PUSH)
    val CUADRICEPS = MuscleGroupInfo(6, "Cuádriceps", MuscleGroupCategory.LEGS)
    val ISQUIOS = MuscleGroupInfo(7, "Isquios", MuscleGroupCategory.LEGS)
    val GLUTEOS = MuscleGroupInfo(8, "Glúteos", MuscleGroupCategory.LEGS)
    val GEMELOS = MuscleGroupInfo(9, "Gemelos", MuscleGroupCategory.LEGS)
    val CORE = MuscleGroupInfo(10, "Core", MuscleGroupCategory.CORE)

    val all = listOf(
        PECHO, ESPALDA, HOMBROS, BICEPS, TRICEPS,
        CUADRICEPS, ISQUIOS, GLUTEOS, GEMELOS, CORE,
    )

    fun byId(id: Long): MuscleGroupInfo? = all.firstOrNull { it.id == id }
}
