package com.pelayo.gimnasio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.domain.calc.BalanceReport
import com.pelayo.gimnasio.domain.calc.CoverageLevel
import com.pelayo.gimnasio.domain.calc.MuscleBalanceAnalyzer
import com.pelayo.gimnasio.domain.calc.WarningSeverity
import com.pelayo.gimnasio.ui.theme.WarningAmber

/**
 * Muestra el volumen semanal por grupo muscular y los avisos de descompensación.
 * La escala de la barra llega hasta el umbral de volumen excesivo.
 */
@Composable
fun BalancePanel(report: BalanceReport, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (report.isBalanced) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (report.isBalanced) MaterialTheme.colorScheme.primary else WarningAmber,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (report.isBalanced) "Rutina equilibrada" else "Revisa el equilibrio",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                "Series efectivas por semana",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            report.scores.forEach { score ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = score.group.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(88.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        val fraction = (score.effectiveSets / MuscleBalanceAnalyzer.HIGH_THRESHOLD)
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(score.level.color()),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = score.effectiveSets.formatSets(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(32.dp),
                    )
                }
            }

            if (report.warnings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                report.warnings.forEach { warning ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = if (warning.severity == WarningSeverity.AVISO) {
                                Icons.Filled.Warning
                            } else {
                                Icons.Filled.Info
                            },
                            contentDescription = null,
                            tint = if (warning.severity == WarningSeverity.AVISO) {
                                WarningAmber
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(warning.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageLevel.color(): Color = when (this) {
    CoverageLevel.AUSENTE -> MaterialTheme.colorScheme.error
    CoverageLevel.BAJO -> WarningAmber
    CoverageLevel.OK -> MaterialTheme.colorScheme.primary
    CoverageLevel.EXCESIVO -> MaterialTheme.colorScheme.tertiary
}

private fun Double.formatSets(): String =
    if (this == toLong().toDouble()) toLong().toString() else ((this * 10).toLong() / 10.0).toString()
