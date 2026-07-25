package com.pelayo.gimnasio.domain.model

enum class Sex { M, F }

enum class MuscleGroupCategory { PUSH, PULL, LEGS, CORE }

enum class MuscleRole { PRIMARY, SECONDARY }

enum class ExerciseType { REPS, TIME }

enum class RoutineGoal { FUERZA, HIPERTROFIA, RESISTENCIA, PERDIDA_GRASA }

enum class MealType { DESAYUNO, COMIDA, CENA, SNACK }

enum class BmiCategory { BAJO_PESO, NORMAL, SOBREPESO, OBESIDAD }

/** Factores de actividad estándar para el cálculo del TDEE. */
enum class ActivityLevel(val factor: Double, val labelEs: String) {
    SEDENTARIO(1.2, "Sedentario (poco o nada de ejercicio)"),
    LIGERO(1.375, "Ligero (1-3 días/semana)"),
    MODERADO(1.55, "Moderado (3-5 días/semana)"),
    INTENSO(1.725, "Intenso (6-7 días/semana)"),
    MUY_INTENSO(1.9, "Muy intenso (trabajo físico + entreno)"),
}
