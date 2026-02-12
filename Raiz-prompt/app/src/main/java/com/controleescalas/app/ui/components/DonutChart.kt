package com.controleescalas.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.TextGray
import com.controleescalas.app.ui.theme.TextWhite
import kotlin.math.*

/**
 * Componente de gráfico donut (pizza com buraco) com animação e interatividade
 */
@Composable
fun DonutChart(
    segments: List<PieChartSegment>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    innerRadiusRatio: Float = 0.6f, // Proporção do buraco (0.6 = 60% do raio)
    animationDuration: Int = 1000,
    onSegmentClick: ((PieChartSegment) -> Unit)? = null
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
    
    // Animação de crescimento (apenas uma vez)
    var animatedProgress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        animatedProgress = 0f
        animatedProgress = 1f
    }
    
    val animatedProgressValue by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(animationDuration, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    var selectedSegment by remember { mutableStateOf<PieChartSegment?>(null) }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gráfico donut
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        if (onSegmentClick != null) {
                            detectTapGestures { tapOffset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val radius = min(size.width, size.height) / 2f
                                val distance = sqrt(
                                    (tapOffset.x - center.x).pow(2f) + 
                                    (tapOffset.y - center.y).pow(2f)
                                )
                                
                                if (distance <= radius && distance >= radius * innerRadiusRatio) {
                                    // Calcular qual segmento foi clicado
                                    val angle = Math.toDegrees(
                                        atan2(
                                            (tapOffset.y - center.y).toDouble(),
                                            (tapOffset.x - center.x).toDouble()
                                        )
                                    ).toFloat() + 90f
                                    val normalizedAngle = if (angle < 0) angle + 360f else angle
                                    
                                    var currentAngle = 0f
                                    segments.forEach { segment ->
                                        val sweepAngle = (segment.value / total) * 360f * animatedProgressValue
                                        if (normalizedAngle >= currentAngle && normalizedAngle < currentAngle + sweepAngle) {
                                            selectedSegment = segment
                                            onSegmentClick?.invoke(segment)
                                            return@detectTapGestures
                                        }
                                        currentAngle += sweepAngle
                                    }
                                }
                            }
                        }
                    }
            ) {
                var startAngle = -90f // Começar do topo
                
                segments.forEach { segment ->
                    val sweepAngle = (segment.value / total) * 360f * animatedProgressValue
                    
                    if (sweepAngle > 0) {
                        val isSelected = selectedSegment == segment
                        val alpha = if (isSelected) 1f else 0.8f
                        
                        drawDonutSlice(
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            color = segment.color.copy(alpha = alpha),
                            innerRadiusRatio = innerRadiusRatio,
                            strokeWidth = if (isSelected) 4.dp.toPx() else 0f
                        )
                        startAngle += sweepAngle
                    }
                }
            }
            
            // Texto central (opcional - mostrar total ou valor selecionado)
            selectedSegment?.let { segment ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = segment.value.toInt().toString(),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = segment.color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = segment.label,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            } ?: run {
                Text(
                    text = total.toInt().toString(),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
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
                    val isSelected = selectedSegment == segment
                    
                    LegendItem(
                        label = segment.label,
                        color = segment.color,
                        value = segment.value.toInt(),
                        percentage = percentage,
                        isSelected = isSelected
                    )
                }
            }
        }
    }
}

/**
 * Desenhar um segmento do gráfico donut
 */
private fun DrawScope.drawDonutSlice(
    startAngle: Float,
    sweepAngle: Float,
    color: Color,
    innerRadiusRatio: Float,
    strokeWidth: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = kotlin.math.min(size.width, size.height) / 2f - 20.dp.toPx()
    val innerRadius = outerRadius * innerRadiusRatio
    
    // Desenhar arco externo
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(
            center.x - outerRadius,
            center.y - outerRadius
        ),
        size = Size(outerRadius * 2, outerRadius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = outerRadius - innerRadius,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    )
    
    // Borda destacada se selecionado
    if (strokeWidth > 0) {
        drawArc(
            color = Color.White,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(
                center.x - outerRadius,
                center.y - outerRadius
            ),
            size = Size(outerRadius * 2, outerRadius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

/**
 * Item da legenda melhorado
 */
@Composable
fun LegendItem(
    label: String,
    color: Color,
    value: Int,
    percentage: Float,
    isSelected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = if (isSelected) color else TextWhite,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Text(
            text = "$value (${String.format("%.1f", percentage)}%)",
            color = if (isSelected) color else TextGray,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

