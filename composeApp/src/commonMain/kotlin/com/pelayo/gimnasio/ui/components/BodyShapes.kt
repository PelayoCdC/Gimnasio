package com.pelayo.gimnasio.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.pelayo.gimnasio.domain.model.MuscleGroups

/**
 * Silueta humana del mapa muscular, en un lienzo de 100x200.
 *
 * Las formas se escribieron como rutas SVG (dibujadas y ajustadas mirando el
 * resultado) y se leen aquí con un intérprete mínimo, en vez de traducirlas a
 * mano a llamadas de `Path`: son veinticinco curvas y transcribirlas era pedir
 * una errata. El intérprete solo entiende lo que usan estas rutas: M, L, C y Z
 * en absoluto.
 *
 * El cuerpo se dibuja en dos capas: primero la silueta entera en gris y encima
 * las zonas de cada grupo muscular. Lo que no pertenece a ningún grupo (cabeza,
 * cuello, antebrazos, manos, rodillas y pies) se queda en gris y hace de relleno.
 */
internal object BodyShapes {

    /** Silueta completa: cabeza, cuello, torso, hombros, brazos, manos, piernas y pies. */
    val silhouette: Path by lazy {
        Path().apply {
            addOval(Rect(41f, 4f, 59f, 26f))                    // cabeza
            addPath(parse("M45.4 23 L54.6 23 L54.6 34 L45.4 34 Z")) // cuello
            addPath(parse(TORSO))
            addOval(DELTOID_LEFT)
            addOval(DELTOID_RIGHT)
            addPath(parse(UPPER_ARM_LEFT))
            addPath(parse(FOREARM_LEFT))
            addOval(Rect(15f, 99.4f, 22.2f, 110.6f))            // mano izquierda
            addPath(parse(UPPER_ARM_RIGHT))
            addPath(parse(FOREARM_RIGHT))
            addOval(Rect(77.8f, 99.4f, 85f, 110.6f))            // mano derecha
            addPath(parse(THIGH_LEFT))
            addPath(parse(SHIN_LEFT))
            addPath(parse(FOOT_LEFT))
            addPath(parse(THIGH_RIGHT))
            addPath(parse(SHIN_RIGHT))
            addPath(parse(FOOT_RIGHT))
        }
    }

    /** Zonas de la vista frontal, en pares (grupo muscular, forma). */
    val front: List<Pair<Long, Path>> by lazy {
        listOf(
            MuscleGroups.HOMBROS.id to Path().apply { addOval(DELTOID_LEFT); addOval(DELTOID_RIGHT) },
            MuscleGroups.PECHO.id to parse(PEC_LEFT).apply { addPath(parse(PEC_RIGHT)) },
            MuscleGroups.BICEPS.id to parse(ARM_ZONE_LEFT).apply { addPath(parse(ARM_ZONE_RIGHT)) },
            MuscleGroups.CORE.id to parse(ABS),
            MuscleGroups.CUADRICEPS.id to parse(QUAD_LEFT).apply { addPath(parse(QUAD_RIGHT)) },
            MuscleGroups.GEMELOS.id to parse(CALF_LEFT).apply { addPath(parse(CALF_RIGHT)) },
        )
    }

    /** Zonas de la vista trasera. */
    val back: List<Pair<Long, Path>> by lazy {
        listOf(
            MuscleGroups.HOMBROS.id to Path().apply { addOval(DELTOID_LEFT); addOval(DELTOID_RIGHT) },
            MuscleGroups.ESPALDA.id to parse(BACK),
            MuscleGroups.TRICEPS.id to parse(ARM_ZONE_LEFT).apply { addPath(parse(ARM_ZONE_RIGHT)) },
            MuscleGroups.GLUTEOS.id to parse(GLUTE_LEFT).apply { addPath(parse(GLUTE_RIGHT)) },
            MuscleGroups.ISQUIOS.id to parse(HAMSTRING_LEFT).apply { addPath(parse(HAMSTRING_RIGHT)) },
            MuscleGroups.GEMELOS.id to parse(CALF_LEFT).apply { addPath(parse(CALF_RIGHT)) },
        )
    }

    private val DELTOID_LEFT = Rect(18.9f, 32.8f, 36.1f, 51.2f)
    private val DELTOID_RIGHT = Rect(63.9f, 32.8f, 81.1f, 51.2f)

    // --- Silueta -------------------------------------------------------------

    private const val TORSO =
        "M29 36 C 36 32, 64 32, 71 36 C 74 46, 71 58, 67.5 68 C 64.5 75, 64 83, 66 92 " +
            "C 62 99, 56 101, 50 101 C 44 101, 38 99, 34 92 C 36 83, 35.5 75, 32.5 68 " +
            "C 29 58, 26 46, 29 36 Z"

    private const val UPPER_ARM_LEFT =
        "M19.2 42 C 21 36, 29 36, 30.6 43 L 26.4 72 C 25.6 77, 18.4 76.4, 18 71 Z"
    private const val UPPER_ARM_RIGHT =
        "M80.8 42 C 79 36, 71 36, 69.4 43 L 73.6 72 C 74.4 77, 81.6 76.4, 82 71 Z"
    private const val FOREARM_LEFT =
        "M18.2 71.5 L 26.2 72 L 22.6 99 C 22.2 103, 15.6 102.4, 15.4 98.4 Z"
    private const val FOREARM_RIGHT =
        "M81.8 71.5 L 73.8 72 L 77.4 99 C 77.8 103, 84.4 102.4, 84.6 98.4 Z"

    private const val THIGH_LEFT =
        "M34.2 90 C 39 95, 45 96, 48.6 94 L 48 132 C 47.8 137, 37.6 137, 37.2 132 " +
            "C 36.6 120, 35 104, 34.2 90 Z"
    private const val THIGH_RIGHT =
        "M65.8 90 C 61 95, 55 96, 51.4 94 L 52 132 C 52.2 137, 62.4 137, 62.8 132 " +
            "C 63.4 120, 65 104, 65.8 90 Z"
    private const val SHIN_LEFT =
        "M37.4 134 L 47.9 134 C 47.6 146, 47 158, 46.2 168 C 46 172, 39.6 172, 39.4 168 " +
            "C 38.6 158, 37.8 146, 37.4 134 Z"
    private const val SHIN_RIGHT =
        "M62.6 134 L 52.1 134 C 52.4 146, 53 158, 53.8 168 C 54 172, 60.4 172, 60.6 168 " +
            "C 61.4 158, 62.2 146, 62.6 134 Z"
    private const val FOOT_LEFT =
        "M39.3 168 L 46.2 168 L 46.6 179 L 36.4 179 C 35.6 176, 37.2 171, 39.3 168 Z"
    private const val FOOT_RIGHT =
        "M60.7 168 L 53.8 168 L 54.2 179 L 64.4 179 C 65.2 176, 63.6 171, 60.7 168 Z"

    // --- Zonas ---------------------------------------------------------------

    private const val PEC_LEFT =
        "M34.6 42 C 40 39, 46.4 40, 49 44.4 L 49 59 C 43 62, 36.4 60.6, 33.8 56 " +
            "C 33 50, 33.6 45, 34.6 42 Z"
    private const val PEC_RIGHT =
        "M65.4 42 C 60 39, 53.6 40, 51 44.4 L 51 59 C 57 62, 63.6 60.6, 66.2 56 " +
            "C 67 50, 66.4 45, 65.4 42 Z"

    /** Bíceps por delante y tríceps por detrás ocupan el mismo trozo de brazo. */
    private const val ARM_ZONE_LEFT =
        "M20.6 48 C 24 46, 28 46, 30.2 49 L 27.4 66 C 25 68.4, 21 68, 19.4 65 Z"
    private const val ARM_ZONE_RIGHT =
        "M79.4 48 C 76 46, 72 46, 69.8 49 L 72.6 66 C 75 68.4, 79 68, 80.6 65 Z"

    private const val ABS =
        "M40.4 60 C 45 58.6, 55 58.6, 59.6 60 C 60.4 70, 60 80, 58.6 88 " +
            "C 54 90.4, 46 90.4, 41.4 88 C 40 80, 39.6 70, 40.4 60 Z"

    /** Trapecio y dorsal van juntos: en la app son el mismo grupo, "Espalda". */
    private const val BACK =
        "M33.8 37 C 41 33, 59 33, 66.2 37 C 67.6 50, 64.8 64, 59.6 74 " +
            "C 54.6 77, 45.4 77, 40.4 74 C 35.2 64, 32.4 50, 33.8 37 Z"

    private const val QUAD_LEFT =
        "M36.2 97 C 41 100, 45.6 100.4, 48.2 99 L 47.8 128 C 46 130.4, 39.6 130.4, 38 128 " +
            "C 37.4 118, 36.8 106, 36.2 97 Z"
    private const val QUAD_RIGHT =
        "M63.8 97 C 59 100, 54.4 100.4, 51.8 99 L 52.2 128 C 54 130.4, 60.4 130.4, 62 128 " +
            "C 62.6 118, 63.2 106, 63.8 97 Z"

    private const val GLUTE_LEFT =
        "M35.2 84 C 40 82, 46 82.6, 49 86 L 49 96 C 45 99.4, 38 98.4, 35 94 " +
            "C 34.4 90, 34.6 86.6, 35.2 84 Z"
    private const val GLUTE_RIGHT =
        "M64.8 84 C 60 82, 54 82.6, 51 86 L 51 96 C 55 99.4, 62 98.4, 65 94 " +
            "C 65.6 90, 65.4 86.6, 64.8 84 Z"

    private const val HAMSTRING_LEFT =
        "M36.2 101 C 41 103.4, 45.6 103.6, 48.2 102.4 L 47.8 130 C 46 132.4, 39.6 132.4, 38 130 " +
            "C 37.4 121, 36.8 110, 36.2 101 Z"
    private const val HAMSTRING_RIGHT =
        "M63.8 101 C 59 103.4, 54.4 103.6, 51.8 102.4 L 52.2 130 C 54 132.4, 60.4 132.4, 62 130 " +
            "C 62.6 121, 63.2 110, 63.8 101 Z"

    private const val CALF_LEFT =
        "M38.6 138 C 41 140.4, 44.4 140.4, 46.8 138 C 46.6 149, 46.2 158, 45.6 165 " +
            "C 43.6 167, 41 167, 39.8 165 C 39.2 158, 38.8 149, 38.6 138 Z"
    private const val CALF_RIGHT =
        "M61.4 138 C 59 140.4, 55.6 140.4, 53.2 138 C 53.4 149, 53.8 158, 54.4 165 " +
            "C 56.4 167, 59 167, 60.2 165 C 60.8 158, 61.2 149, 61.4 138 Z"

    internal fun parse(data: String): Path = svgPath(data)
}
