package com.pelayo.gimnasio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/**
 * La mancuerna de la app sobre su fondo verde, para poder enseñar el icono
 * dentro de la propia aplicación.
 *
 * Es el mismo dibujo que `ic_launcher_foreground`, copiado tal cual de su
 * `pathData`: si se retoca el icono hay que retocarlo aquí también, pero a
 * cambio no hace falta cargar recursos de Android desde código común (que no
 * compilaría para iOS) ni mantener dos dibujos distintos.
 *
 * Los colores van fijos y no salen del tema: es una marca, y debe verse igual
 * en claro y en oscuro.
 */
@Composable
fun GimnasioLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val dumbbell = remember { DUMBBELL.map { svgPath(it) } }

    Canvas(modifier.size(size)) {
        withTransform({
            scale(this@Canvas.size.minDimension / CANVAS, this@Canvas.size.minDimension / CANVAS, Offset.Zero)
        }) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF7CCB3F), Color(0xFF3F8A16)),
                    start = Offset.Zero,
                    end = Offset(CANVAS, CANVAS),
                ),
                size = Size(CANVAS, CANVAS),
                cornerRadius = CornerRadius(CANVAS * 0.28f),
            )
            // Dentro de la app no hay zona segura que respetar como en el icono
            // del lanzador, así que la mancuerna se agranda para que se lea a 30dp.
            withTransform({ scale(1.18f, 1.18f, Offset(CANVAS / 2, CANVAS / 2)) }) {
                dumbbell.forEach { drawPath(it, Color.White) }
            }
        }
    }
}

/** Lienzo del icono adaptativo de Android, que es de donde vienen las rutas. */
private const val CANVAS = 108f

private val DUMBBELL = listOf(
    // Disco exterior izquierdo
    "M25,46 L28,46 Q31,46 31,49 L31,59 Q31,62 28,62 L25,62 Q22,62 22,59 L22,49 Q22,46 25,46 Z",
    // Disco interior izquierdo
    "M35,38 L37,38 Q41,38 41,42 L41,66 Q41,70 37,70 L35,70 Q31,70 31,66 L31,42 Q31,38 35,38 Z",
    // Barra
    "M41,50 L67,50 L67,58 L41,58 Z",
    // Disco interior derecho
    "M71,38 L73,38 Q77,38 77,42 L77,66 Q77,70 73,70 L71,70 Q67,70 67,66 L67,42 Q67,38 71,38 Z",
    // Disco exterior derecho
    "M80,46 L83,46 Q86,46 86,49 L86,59 Q86,62 83,62 L80,62 Q77,62 77,59 L77,49 Q77,46 80,46 Z",
)
