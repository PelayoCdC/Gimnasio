package com.pelayo.gimnasio.domain.model

enum class Sex { M, F }

enum class MuscleGroupCategory { PUSH, PULL, LEGS, CORE }

enum class MuscleRole { PRIMARY, SECONDARY }

enum class ExerciseType { REPS, TIME }

enum class RoutineGoal { FUERZA, HIPERTROFIA, RESISTENCIA, PERDIDA_GRASA }

/** Experiencia entrenando, que decide qué ejercicios y qué volumen propone el generador. */
enum class ExperienceLevel { PRINCIPIANTE, INTERMEDIO, AVANZADO }

/**
 * Exigencia técnica de un ejercicio. No es lo duro que resulta, sino lo fácil que
 * es hacerlo mal y lesionarse sin supervisión.
 */
enum class ExerciseComplexity { BASICO, INTERMEDIO, AVANZADO }

/**
 * Con qué se hace el ejercicio, para poder preferir unas cosas sobre otras al
 * generar y al sugerir cambios. [priority] ordena de más a menos preferido:
 * máquina y polea primero por ser las más fáciles de ejecutar y de progresar,
 * después mancuernas y banco, y al final barra y peso corporal.
 */
enum class ExerciseStyle(val priority: Int, val labelEs: String) {
    MAQUINA(0, "Máquina"),
    POLEA(0, "Polea"),
    MANCUERNA(1, "Mancuernas"),
    BANCO(1, "Banco"),
    BARRA(2, "Barra"),
    PESO_CORPORAL(3, "Peso corporal"),
    OTRO(3, "Otro"),
}

enum class MealType { DESAYUNO, COMIDA, CENA, SNACK }

/**
 * Cómo se mide un alimento. Los valores nutricionales son siempre por 100 de esta
 * unidad, así que el cálculo no cambia: cambia lo que se escribe al lado del número.
 * Una leche que dice "100 g" cuando la etiqueta habla de 100 ml se lee mal.
 */
enum class FoodUnit(val labelEs: String) {
    G("g"),
    ML("ml"),
}

enum class BmiCategory { BAJO_PESO, NORMAL, SOBREPESO, OBESIDAD }

/** Factores de actividad estándar para el cálculo del TDEE. */
enum class ActivityLevel(val factor: Double, val labelEs: String) {
    SEDENTARIO(1.2, "Sedentario (poco o nada de ejercicio)"),
    LIGERO(1.375, "Ligero (1-3 días/semana)"),
    MODERADO(1.55, "Moderado (3-5 días/semana)"),
    INTENSO(1.725, "Intenso (6-7 días/semana)"),
    MUY_INTENSO(1.9, "Muy intenso (trabajo físico + entreno)"),
}
