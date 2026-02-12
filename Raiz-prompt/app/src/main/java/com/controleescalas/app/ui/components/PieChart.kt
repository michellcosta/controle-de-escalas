package com.controleescalas.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.TextGray
import com.controleescalas.app.ui.theme.TextWhite

/**
 * Dados para um segmento do gráfico de pizza
 */
data class PieChartSegment(
    val label: String,
    val value: Float,
    val color: Color
)

/**
 * Componente de gráfico de pizza simples
 */
@Composable
fun PieChart(
    segments: List<PieChartSegment>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true
) {
    if (segments.isEmpty() || segments.all { it.value == 0f }) {
        Box(
            modifier = modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sem dados",
                color = TextGray
            )
        }
        return
    }
    
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    if (total == 0f) {
        Box(
            modifier = modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sem dados",
                color = TextGray
            )
        }
        return
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gráfico de pizza
        Canvas(
            modifier = Modifier.size(200.dp)
        ) {
            var startAngle = -90f // Começar do topo
            
            segments.forEach { segment ->
                val sweepAngle = (segment.value / total) * 360f
                
                if (sweepAngle > 0) {
                    drawPieSlice(
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        color = segment.color
                    )
                    startAngle += sweepAngle
                }
            }
        }
        
        // Legenda
        if (showLegend) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                segments.forEach { segment ->
                    val percentage = if (total > 0) (segment.value / total * 100) else 0f
                    LegendItem(
                        label = segment.label,
                        color = segment.color,
                        value = segment.value.toInt(),
                        percentage = percentage
                    )
                }
            }
        }
    }
}

/**
 * Desenhar um segmento do gráfico de pizza
 */
private fun DrawScope.drawPieSlice(
    startAngle: Float,
    sweepAngle: Float,
    color: Color
) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = (size.minDimension / 2) - 20.dp.toPx()
    
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = true,
        topLeft = Offset(
            center.x - radius,
            center.y - radius
        ),
        size = Size(radius * 2, radius * 2)
    )
}

/**
 * Item da legenda
 */
@Composable
fun LegendItem(
    label: String,
    color: Color,
    value: Int,
    percentage: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
            Text(
                text = label,
                color = TextWhite,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "$value (${String.format("%.1f", percentage)}%)",
            color = TextGray,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

