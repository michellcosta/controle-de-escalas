package com.controleescalas.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleescalas.app.data.models.QuinzenaMotorista
import com.controleescalas.app.ui.theme.*

/**
 * Card que exibe a contagem de dias trabalhados na quinzena atual
 */
@Composable
fun QuinzenaCard(
    quinzena: QuinzenaMotorista?,
    modifier: Modifier = Modifier
) {
    val primeiraQuinzenaDias = quinzena?.primeiraQuinzena?.diasTrabalhados ?: 0
    val segundaQuinzenaDias = quinzena?.segundaQuinzena?.diasTrabalhados ?: 0
    val totalDias = primeiraQuinzenaDias + segundaQuinzenaDias
    
    // Animação para os valores
    val animatedPrimeira by animateFloatAsState(
        targetValue = primeiraQuinzenaDias.toFloat(),
        label = "primeira"
    )
    val animatedSegunda by animateFloatAsState(
        targetValue = segundaQuinzenaDias.toFloat(),
        label = "segunda"
    )
    
    GlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Quinzena",
                        tint = NeonBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "DIAS TRABALHADOS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
                
                // Total em destaque
                Text(
                    text = "$totalDias dias",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen
                )
            }
            
            HorizontalDivider(color = CardBorder)
            
            // Primeira Quinzena (01-15)
            QuinzenaSection(
                titulo = "1ª Quinzena (01-15)",
                dias = animatedPrimeira.toInt(),
                maxDias = 15,
                cor = NeonBlue
            )
            
            // Segunda Quinzena (16-fim)
            QuinzenaSection(
                titulo = "2ª Quinzena (16-fim)",
                dias = animatedSegunda.toInt(),
                maxDias = 15,
                cor = NeonPurple
            )
        }
    }
}

/**
 * Seção individual de uma quinzena
 */
@Composable
private fun QuinzenaSection(
    titulo: String,
    dias: Int,
    maxDias: Int,
    cor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titulo,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextGray
            )
            Text(
                text = "$dias/$maxDias",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cor
            )
        }
        
        // Barra de progresso
        LinearProgressIndicator(
            progress = { (dias.toFloat() / maxDias).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = cor,
            trackColor = CardBorder,
            strokeCap = StrokeCap.Round
        )
    }
}
