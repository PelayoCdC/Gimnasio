package com.pelayo.gimnasio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.pelayo.gimnasio.domain.model.MuscleGroupInfo
import com.pelayo.gimnasio.domain.model.MuscleGroups

/**
 * Silueta esquemática del cuerpo, de frente y de espalda, con cada grupo muscular
 * coloreado según su volumen relativo. No pretende ser anatómicamente exacta:
 * sirve para ver de un vistazo qué zonas se están trabajando y cuáles no.
 *
 * Las formas se definen en un lienzo de 100x200 y se escalan al tamaño real.
 */
@Composable
fun MuscleMap(
    /** Volumen por grupo muscular; la escala se normaliza con el valor máximo. */
    volumeByGroup: Map<Long, Double>,
    modifier: Modifier = Modifier,
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val max = volumeByGroup.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    fun colorFor(groupId: Long): Color {
        val value = volumeByGroup[groupId] ?: 0.0
        if (value <= 0.0) return emptyColor
        // Mínimo 0.35 de alfa para que lo poco trabajado se distinga de lo nulo.
        val intensity = (0.35 + 0.65 * (value / max)).coerceIn(0.0, 1.0)
        return activeColor.copy(alpha = intensity.toFloat())
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.5f)) {
                    drawBody(front = true, outline = outline, colorFor = ::colorFor)
                }
                Text(
                    "Delante",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.5f)) {
                    drawBody(front = false, outline = outline, colorFor = ::colorFor)
                }
                // "Detrás" y no "Espalda": esta última es además un grupo
                // muscular de la leyenda y se confundían.
                Text(
                    "Detrás",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        MuscleMapLegend(
            volumeByGroup = volumeByGroup,
            colorFor = { colorFor(it) },
        )
    }
}

@Composable
private fun MuscleMapLegend(
    volumeByGroup: Map<Long, Double>,
    colorFor: (Long) -> Color,
) {
    val worked = MuscleGroups.all.filter { (volumeByGroup[it.id] ?: 0.0) > 0.0 }
    val missing = MuscleGroups.all - worked.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (worked.isNotEmpty()) {
            LegendRow(label = "Trabajados", groups = worked, colorFor = colorFor)
        }
        if (missing.isNotEmpty()) {
            LegendRow(label = "Sin trabajar", groups = missing, colorFor = colorFor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendRow(
    label: String,
    groups: List<MuscleGroupInfo>,
    colorFor: (Long) -> Color,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorFor(group.id)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(group.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Dibuja la silueta escalando el diseño base de 100x200 al tamaño disponible. */
private fun DrawScope.drawBody(
    front: Boolean,
    outline: Color,
    colorFor: (Long) -> Color,
) {
    val scaleX = size.width / 100f
    val scaleY = size.height / 200f

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        drawRoundRect(
            color = color,
            topLeft = Offset(x * scaleX, y * scaleY),
            size = Size(w * scaleX, h * scaleY),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scaleX),
        )
    }

    val G = MuscleGroups

    // Cabeza y cuello, solo como referencia visual.
    drawCircle(
        color = outline.copy(alpha = 0.35f),
        radius = 9f * scaleX,
        center = Offset(50f * scaleX, 14f * scaleY),
    )

    if (front) {
        rect(38f, 28f, 24f, 8f, colorFor(G.HOMBROS.id))   // deltoides frontales
        rect(28f, 30f, 10f, 12f, colorFor(G.HOMBROS.id))  // hombro izquierdo
        rect(62f, 30f, 10f, 12f, colorFor(G.HOMBROS.id))  // hombro derecho
        rect(36f, 38f, 28f, 20f, colorFor(G.PECHO.id))    // pectoral
        rect(26f, 44f, 10f, 20f, colorFor(G.BICEPS.id))   // bíceps izquierdo
        rect(64f, 44f, 10f, 20f, colorFor(G.BICEPS.id))   // bíceps derecho
        rect(38f, 60f, 24f, 26f, colorFor(G.CORE.id))     // abdomen
        rect(36f, 92f, 12f, 40f, colorFor(G.CUADRICEPS.id))
        rect(52f, 92f, 12f, 40f, colorFor(G.CUADRICEPS.id))
        rect(38f, 150f, 9f, 26f, colorFor(G.GEMELOS.id))
        rect(53f, 150f, 9f, 26f, colorFor(G.GEMELOS.id))
    } else {
        rect(38f, 28f, 24f, 10f, colorFor(G.ESPALDA.id))  // trapecio
        rect(28f, 30f, 10f, 12f, colorFor(G.HOMBROS.id))  // deltoides posterior
        rect(62f, 30f, 10f, 12f, colorFor(G.HOMBROS.id))
        rect(34f, 40f, 32f, 26f, colorFor(G.ESPALDA.id))  // dorsal
        rect(26f, 44f, 10f, 20f, colorFor(G.TRICEPS.id))  // tríceps izquierdo
        rect(64f, 44f, 10f, 20f, colorFor(G.TRICEPS.id))  // tríceps derecho
        rect(36f, 76f, 28f, 18f, colorFor(G.GLUTEOS.id))
        rect(36f, 98f, 12f, 36f, colorFor(G.ISQUIOS.id))
        rect(52f, 98f, 12f, 36f, colorFor(G.ISQUIOS.id))
        rect(38f, 150f, 9f, 26f, colorFor(G.GEMELOS.id))
        rect(53f, 150f, 9f, 26f, colorFor(G.GEMELOS.id))
    }
}
