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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.util.oneDecimal
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
    val max = volumeByGroup.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    fun colorFor(groupId: Long): Color {
        val value = volumeByGroup[groupId] ?: 0.0
        if (value <= 0.0) return emptyColor
        // Mínimo 0.35 de alfa para que lo poco trabajado se distinga de lo nulo.
        val intensity = (0.35 + 0.65 * (value / max)).coerceIn(0.0, 1.0)
        return activeColor.copy(alpha = intensity.toFloat())
    }

    // La silueta es un Canvas mudo para el lector de pantalla: se describe con
    // los grupos trabajados y sus series efectivas.
    val worked = MuscleGroups.all
        .mapNotNull { group ->
            volumeByGroup[group.id]?.takeIf { it > 0 }?.let { "${group.name} ${it.oneDecimal()}" }
        }
    val mapDescription = if (worked.isEmpty()) {
        "Mapa muscular sin volumen registrado"
    } else {
        "Mapa muscular, series efectivas por grupo: ${worked.joinToString(", ")}"
    }

    Column(modifier.semantics { contentDescription = mapDescription }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.5f)) {
                    drawBody(front = true, neutral = emptyColor, colorFor = ::colorFor)
                }
                Text(
                    "Delante",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.5f)) {
                    drawBody(front = false, neutral = emptyColor, colorFor = ::colorFor)
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

/**
 * Dibuja la silueta escalando el diseño base de 100x200 al tamaño disponible.
 *
 * Dos capas: el cuerpo entero en gris y encima las zonas de cada grupo. Lo que no
 * pertenece a ningún grupo (cabeza, cuello, antebrazos, manos, rodillas y pies)
 * se queda en gris, así que la figura se entiende aunque no se entrene nada.
 */
private fun DrawScope.drawBody(
    front: Boolean,
    neutral: Color,
    colorFor: (Long) -> Color,
) {
    withTransform({ scale(size.width / 100f, size.height / 200f, pivot = Offset.Zero) }) {
        drawPath(BodyShapes.silhouette, neutral.copy(alpha = 0.6f))
        val zones = if (front) BodyShapes.front else BodyShapes.back
        zones.forEach { (groupId, shape) -> drawPath(shape, colorFor(groupId)) }
    }
}
