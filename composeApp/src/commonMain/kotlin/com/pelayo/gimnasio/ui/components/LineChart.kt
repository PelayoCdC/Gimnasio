package com.pelayo.gimnasio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.util.oneDecimal

/**
 * Gráfica de línea sencilla sobre Canvas: un punto por valor, escala automática.
 * Suficiente para el progreso de peso o volumen; evita traer una librería entera.
 */
@Composable
fun LineChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    unit: String = "",
) {
    if (values.isEmpty()) return

    val minValue = values.min()
    val maxValue = values.max()
    // Si todos los valores son iguales, se dibuja una línea centrada.
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    // El Canvas es invisible para el lector de pantalla; esto resume lo que pinta.
    val chartDescription = "Gráfica: de ${values.first().oneDecimal()}$unit a " +
        "${values.last().oneDecimal()}$unit en ${values.size} registros"

    // Punto seleccionado con un toque; volver a tocarlo lo quita. Sin esto la
    // gráfica era decorativa: no había forma de leer un valor concreto.
    var selected by remember(values) { mutableStateOf<Int?>(null) }

    Column(modifier.semantics { contentDescription = chartDescription }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${maxValue.oneDecimal()}$unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${minValue.oneDecimal()}$unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .pointerInput(values) {
                        detectTapGestures { offset ->
                            val index = if (values.size <= 1) {
                                0
                            } else {
                                val stepX = size.width / (values.size - 1f)
                                (offset.x / stepX).roundToInt().coerceIn(0, values.lastIndex)
                            }
                            selected = if (selected == index) null else index
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val stepX = if (values.size > 1) w / (values.size - 1) else 0f

                // Líneas guía horizontales.
                repeat(4) { index ->
                    val y = h * index / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                fun pointAt(index: Int): Offset {
                    val value = values[index]
                    val normalized = if (values.size == 1 || maxValue == minValue) {
                        0.5
                    } else {
                        (value - minValue) / range
                    }
                    val x = if (values.size == 1) w / 2f else stepX * index
                    return Offset(x, h - (normalized * h).toFloat())
                }

                if (values.size > 1) {
                    val path = Path().apply {
                        moveTo(pointAt(0).x, pointAt(0).y)
                        for (i in 1 until values.size) {
                            val p = pointAt(i)
                            lineTo(p.x, p.y)
                        }
                    }
                    drawPath(path, color = lineColor, style = Stroke(width = 4f))
                }

                values.indices.forEach { index ->
                    drawCircle(color = lineColor, radius = 6f, center = pointAt(index))
                }

                // Anillo sobre el punto elegido, para saber de cuál se habla.
                selected?.let { index ->
                    drawCircle(
                        color = lineColor,
                        radius = 12f,
                        center = pointAt(index),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }

        selected?.let { index ->
            val label = labels.getOrNull(index)?.takeIf { it.isNotBlank() }
            Text(
                buildString {
                    label?.let { append("$it: ") }
                    append("${values[index].oneDecimal()}$unit")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    labels.first(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (labels.size > 1) {
                    Text(
                        labels.last(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

